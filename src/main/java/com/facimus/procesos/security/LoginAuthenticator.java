package com.facimus.procesos.security;

import java.util.Locale;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import com.facimus.procesos.common.DemasiadosIntentosException;
import com.facimus.procesos.common.metricas.MetricasDeNegocio;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;

/**
 * HU-03: comprueba el correo y la contrasena con el AuthenticationManager y devuelve el perfil del usuario. Cuenta los
 * fallos por correo e IP: cuando un correo junta el maximo desde una IP, el login responde 429 sin comprobar la clave,
 * asi que probar contrasenas deja de servir, y quien falla desde otra IP no bloquea al dueno de la cuenta.
 */
@Component
public class LoginAuthenticator {

    private final AuthenticationManager authenticationManager;
    private final AttemptLimiter limitador;
    private final MetricasDeNegocio metricas;

    public LoginAuthenticator(AuthenticationManager authenticationManager, AttemptLimiter limitador,
            MetricasDeNegocio metricas) {
        this.authenticationManager = authenticationManager;
        this.limitador = limitador;
        this.metricas = metricas;
    }

    /** Si el correo no existe, el usuario esta desactivado o la clave falla, lanza siempre el mismo BadCredentials. */
    public UsuarioResponse autenticar(String email, String password, String ip) {
        // Ana@Acme.com y ana@acme.com son la misma cuenta: cambiar mayusculas no da intentos nuevos.
        String clave = email.trim().toLowerCase(Locale.ROOT) + "|" + ip;
        limitador.espera(clave).ifPresent(espera -> {
            metricas.loginFallido(true);
            throw new DemasiadosIntentosException(espera);
        });
        try {
            Authentication autenticado = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, password));
            limitador.reiniciar(clave);
            return ((UserAccount) autenticado.getPrincipal()).usuario();
        } catch (AuthenticationException e) {
            limitador.registrar(clave);
            metricas.loginFallido(false);
            throw e;
        }
    }
}
