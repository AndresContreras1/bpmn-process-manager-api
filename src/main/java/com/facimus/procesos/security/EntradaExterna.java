package com.facimus.procesos.security;

import java.io.IOException;
import java.util.EnumSet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.security.ContextoDeTienda.Alcance;
import com.facimus.procesos.common.security.Factor;
import com.facimus.procesos.gestion.dto.response.ResultadoEntradaExterna;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.service.IdentidadExternaService;
import com.facimus.procesos.gestion.service.SegundoFactorService;
import com.facimus.procesos.gestion.service.SesionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * PR 50: la vuelta del proveedor, ya con el id_token comprobado (firma, emisor, audiencia, fechas y nonce). La sesion
 * es la misma de siempre, en las dos cookies; la autenticacion de OAuth 2 no se queda en ningun sitio. Despues se
 * vuelve a la web: a su inicio, al paso del segundo factor, a confirmar el vinculo o al login con el motivo.
 */
@Component
public class EntradaExterna implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private final IdentidadExternaService identidades;
    private final SegundoFactorService segundoFactor;
    private final SesionService sesiones;
    private final AperturaDeSesion apertura;
    private final String web;

    public EntradaExterna(IdentidadExternaService identidades, SegundoFactorService segundoFactor,
            SesionService sesiones, AperturaDeSesion apertura, @Value("${app.url-publica}") String web) {
        this.identidades = identidades;
        this.segundoFactor = segundoFactor;
        this.sesiones = sesiones;
        this.apertura = apertura;
        this.web = web.endsWith("/") ? web.substring(0, web.length() - 1) : web;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest peticion, HttpServletResponse respuesta,
            Authentication autenticacion) throws IOException {
        SecurityContextHolder.clearContext();
        // PR 51: quien vuelve del proveedor todavia no tiene tienda; se busca entre todas.
        Alcance anterior = ContextoDeTienda.fijar(Alcance.SISTEMA);
        try {
            alVolver(autenticacion, peticion, respuesta);
        } finally {
            ContextoDeTienda.restaurar(anterior);
        }
    }

    private void alVolver(Authentication autenticacion, HttpServletRequest peticion, HttpServletResponse respuesta)
            throws IOException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) autenticacion;
        OidcUser persona = (OidcUser) token.getPrincipal();
        // Microsoft no manda email_verified: dice con xms_edov si el dominio del correo es de la organizacion.
        boolean verificado = Boolean.TRUE.equals(persona.getEmailVerified())
                || Boolean.TRUE.equals(persona.getClaimAsBoolean("xms_edov"));
        ResultadoEntradaExterna resultado = identidades.entrar(token.getAuthorizedClientRegistrationId(),
                persona.getSubject(), persona.getEmail(), verificado, persona.getFullName());
        switch (resultado.tipo()) {
            case ENTRA -> entrar(resultado.usuario(), peticion, respuesta);
            case VINCULAR -> respuesta.sendRedirect(web + "/vincular#token=" + resultado.token());
            case RECHAZADA -> respuesta.sendRedirect(web + "/login?error=" + resultado.motivo());
        }
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest peticion, HttpServletResponse respuesta,
            AuthenticationException excepcion) throws IOException {
        SecurityContextHolder.clearContext();
        respuesta.sendRedirect(web + "/login?error=proveedor");
    }

    /** Con segundo factor, el proveedor es solo el primero: queda el login a medias, como con la clave. */
    private void entrar(UsuarioResponse usuario, HttpServletRequest peticion, HttpServletResponse respuesta)
            throws IOException {
        if (segundoFactor.loPide(usuario.empresaId(), usuario.id())) {
            apertura.pendiente(usuario, EnumSet.of(Factor.PROVEEDOR_EXTERNO), peticion, respuesta);
            respuesta.sendRedirect(web + "/login?paso=segundo-factor");
            return;
        }
        apertura.abrir(sesiones.iniciar(usuario.empresaId(), usuario.id(), EnumSet.of(Factor.PROVEEDOR_EXTERNO)),
                peticion, respuesta);
        respuesta.sendRedirect(web + "/");
    }
}
