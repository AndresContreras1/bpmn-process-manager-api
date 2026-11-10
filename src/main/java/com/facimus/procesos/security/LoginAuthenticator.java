package com.facimus.procesos.security;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import com.facimus.procesos.common.DemasiadosIntentosException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.metricas.MetricasDeNegocio;
import com.facimus.procesos.gestion.dto.response.CredencialesUsuario;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.service.UsuarioService;

/**
 * HU-03: comprueba el correo y la contrasena con el AuthenticationManager y devuelve el perfil del usuario. Cuenta los
 * fallos por correo e IP: cuando un correo junta el maximo desde una IP, el login responde 429 sin comprobar la clave,
 * asi que probar contrasenas deja de servir, y quien falla desde otra IP no bloquea al dueno de la cuenta.
 */
@Component
public class LoginAuthenticator {

    static final String CODIGO_INVALIDO = "El código no es válido o ya se usó.";

    /** PR 49b: lo que responde una entrada con passkey que no verifica, sea cual sea el motivo. */
    public static final String PASSKEY_INVALIDA = "La passkey no sirvió para entrar.";

    private final AuthenticationManager authenticationManager;
    private final LimiteDeIntentos limitador;
    private final MetricasDeNegocio metricas;

    private final UsuarioService usuarioService;
    private final Auditoria auditoria;

    public LoginAuthenticator(AuthenticationManager authenticationManager, LimiteDeIntentos limitador,
            MetricasDeNegocio metricas, UsuarioService usuarioService, Auditoria auditoria) {
        this.authenticationManager = authenticationManager;
        this.limitador = limitador;
        this.metricas = metricas;
        this.usuarioService = usuarioService;
        this.auditoria = auditoria;
    }

    /** Si el correo no existe, el usuario esta desactivado o la clave falla, lanza siempre el mismo BadCredentials. */
    public UsuarioResponse autenticar(String email, String password, String ip) {
        String clave = clave(email, ip);
        esperar(clave);
        try {
            Authentication autenticado = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, password));
            limitador.reiniciar(clave);
            return ((UserAccount) autenticado.getPrincipal()).usuario();
        } catch (AuthenticationException e) {
            limitador.registrar(clave);
            metricas.loginFallido(false);
            anotarFallo(email, "LOGIN_FALLIDO");
            throw e;
        }
    }

    /**
     * PR 49: un codigo del segundo factor, con el mismo limite que la clave y en la misma cuenta: quien tiene la clave
     * no prueba los un millon de codigos. Si la comprobacion no devuelve nada, el codigo era malo o ya se habia usado:
     * cuenta como un fallo y responde 400.
     */
    public <T> T comprobarCodigo(String email, String ip, Supplier<Optional<T>> comprobacion) {
        String clave = clave(email, ip);
        esperar(clave);
        Optional<T> resultado = comprobacion.get();
        if (resultado.isEmpty()) {
            limitador.registrar(clave);
            metricas.loginFallido(false);
            anotarFallo(email, "CODIGO_FALLIDO");
            throw new SolicitudInvalidaException(CODIGO_INVALIDO);
        }
        limitador.reiniciar(clave);
        return resultado.get();
    }

    /**
     * PR 49b: la firma de una passkey de una persona, con su mismo limite: quien tiene una cookie robada o el desafio
     * de otro no prueba firmas gratis. Lo que no verifica cuenta como un fallo y devuelve vacio; quien llama decide la
     * respuesta, 400 en un login a medias y 401 al entrar sin clave.
     */
    public <T> Optional<T> comprobarPasskey(String email, String ip, Supplier<Optional<T>> comprobacion) {
        String clave = clave(email, ip);
        esperar(clave);
        Optional<T> resultado = comprobacion.get();
        if (resultado.isEmpty()) {
            limitador.registrar(clave);
            metricas.loginFallido(false);
            anotarFallo(email, "PASSKEY_FALLIDA");
        } else {
            limitador.reiniciar(clave);
        }
        return resultado;
    }

    /**
     * PR 31: un fallo de una cuenta que existe queda en la bitacora de su tienda. El de un correo que no existe no es
     * de ninguna tienda, y lo cuenta el limite.
     */
    private void anotarFallo(String email, String accion) {
        usuarioService.buscarCredenciales(email.trim().toLowerCase(Locale.ROOT)).map(CredencialesUsuario::usuario)
                .ifPresent(usuario -> auditoria.registrarFallo(usuario.empresaId(), usuario.id(), accion, Map.of()));
    }

    /** Ana@Acme.com y ana@acme.com son la misma cuenta: cambiar mayusculas no da intentos nuevos. */
    private static String clave(String email, String ip) {
        return email.trim().toLowerCase(Locale.ROOT) + "|" + ip;
    }

    private void esperar(String clave) {
        limitador.espera(clave).ifPresent(espera -> {
            metricas.loginFallido(true);
            throw new DemasiadosIntentosException(espera);
        });
    }
}
