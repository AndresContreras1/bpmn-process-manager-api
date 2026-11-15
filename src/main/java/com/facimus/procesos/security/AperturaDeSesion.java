package com.facimus.procesos.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.common.security.Factor;
import com.facimus.procesos.gestion.dto.response.SesionIniciada;
import com.facimus.procesos.gestion.dto.response.SesionResponse;
import com.facimus.procesos.gestion.dto.response.SesionVerificada;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.service.SegundoFactorService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Lo que convierte una sesion en cookies, sea cual sea la puerta por la que se entro: la clave, el segundo factor, la
 * renovacion o, desde el PR 50, un proveedor de OpenID Connect. El cuerpo nunca lleva un token.
 */
@Component
public class AperturaDeSesion {

    /** PR 49: lo que dura el login a medias, entre el primer factor y el codigo. */
    public static final Duration VIDA_DEL_LOGIN_PENDIENTE = Duration.ofMinutes(5);

    private final JwtService jwtService;
    private final CookiesDeSesion cookies;
    private final SegundoFactorService segundoFactorService;

    public AperturaDeSesion(JwtService jwtService, CookiesDeSesion cookies, SegundoFactorService segundoFactorService) {
        this.jwtService = jwtService;
        this.cookies = cookies;
        this.segundoFactorService = segundoFactorService;
    }

    /**
     * Las cookies de una sesion nueva, con su access token recien firmado, y un token CSRF nuevo (ADR-0064). El acceso
     * no pasa del fin de la sesion: a las 24 horas se vuelve a entrar, no a las 24 y cuarto.
     */
    public SesionResponse abrir(SesionIniciada sesion, HttpServletRequest peticion, HttpServletResponse respuesta) {
        Duration vidaDelAcceso = vidaDelAcceso(sesion.vidaRestante());
        cookies.abrir(peticion, respuesta, accessToken(sesion, vidaDelAcceso), vidaDelAcceso, sesion.refreshToken(),
                sesion.vidaDelRefresco());
        return respuesta(sesion, vidaDelAcceso);
    }

    /** Las cookies de la misma sesion, renovada: el token CSRF no cambia. */
    public SesionResponse renovar(SesionIniciada sesion, HttpServletResponse respuesta) {
        Duration vidaDelAcceso = vidaDelAcceso(sesion.vidaRestante());
        cookies.renovar(respuesta, accessToken(sesion, vidaDelAcceso), vidaDelAcceso, sesion.refreshToken(),
                sesion.vidaDelRefresco());
        return respuesta(sesion, vidaDelAcceso);
    }

    /**
     * PR 49: el login a medias de quien tiene segundo factor. Solo la cookie de acceso, de cinco minutos, con un token
     * que solo sirve para dar el codigo y que lleva el factor con que se empezo; la de refresco se borra, porque
     * todavia no hay una sesion que renovar.
     */
    public SesionResponse pendiente(UsuarioResponse usuario, Set<Factor> factores, HttpServletRequest peticion,
            HttpServletResponse respuesta) {
        ApiPrincipal principal = usuario.comoPrincipal(UUID.randomUUID().toString()).pendienteDelSegundoFactor();
        String accessToken = jwtService.generarToken(principal, factores, Instant.now(), VIDA_DEL_LOGIN_PENDIENTE);
        cookies.abrirPendiente(peticion, respuesta, accessToken, VIDA_DEL_LOGIN_PENDIENTE);
        return new SesionResponse(VIDA_DEL_LOGIN_PENDIENTE.toSeconds(), usuario, true);
    }

    /** PR 49: solo la cookie de acceso, con la verificacion nueva; la de refresco sigue sirviendo. */
    public SesionResponse renovarAcceso(SesionVerificada verificada, HttpServletResponse respuesta) {
        Duration vidaDelAcceso = vidaDelAcceso(verificada.vidaRestante());
        cookies.renovarAcceso(respuesta, jwtService.generarToken(principal(verificada.usuario(), verificada.sesion()),
                verificada.factores(), verificada.verificadaEn(), vidaDelAcceso), vidaDelAcceso);
        // El refresh token no cambia: lo que aguanta sin usarse sigue siendo lo que la web ya sabia
        return new SesionResponse(vidaDelAcceso.toSeconds(), verificada.usuario(), false, null,
                verificada.vidaRestante().toSeconds());
    }

    /**
     * PR 49: la identidad del token de una sesion completa. Si la tienda le exige un segundo factor que no tiene, el
     * token lo dice y solo sirve para inscribirlo; al renovar la sesion despues de confirmarlo, ya no.
     */
    private ApiPrincipal principal(UsuarioResponse usuario, String sesion) {
        ApiPrincipal principal = usuario.comoPrincipal(sesion);
        return segundoFactorService.faltaInscribir(usuario.empresaId(), usuario.id(), usuario.rolAcceso())
                ? principal.conInscripcionObligatoria() : principal;
    }

    private String accessToken(SesionIniciada sesion, Duration vidaDelAcceso) {
        return jwtService.generarToken(principal(sesion.usuario(), sesion.sesion()), sesion.factores(),
                sesion.verificadaEn(), vidaDelAcceso);
    }

    private static SesionResponse respuesta(SesionIniciada sesion, Duration vidaDelAcceso) {
        return new SesionResponse(vidaDelAcceso.toSeconds(), sesion.usuario(), false,
                sesion.vidaDelRefresco().toSeconds(), sesion.vidaRestante().toSeconds());
    }

    private Duration vidaDelAcceso(Duration vidaRestante) {
        Duration maximo = Duration.ofSeconds(jwtService.getExpirationSeconds());
        return vidaRestante.compareTo(maximo) < 0 ? vidaRestante : maximo;
    }
}
