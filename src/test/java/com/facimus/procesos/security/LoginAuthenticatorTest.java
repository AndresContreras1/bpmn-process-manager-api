package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.facimus.procesos.common.DemasiadosIntentosException;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.model.RolAcceso;

@ExtendWith(MockitoExtension.class)
class LoginAuthenticatorTest {

    private static final UsuarioResponse ANA =
            new UsuarioResponse(10L, "Ana", "ana@acme.com", RolAcceso.EDITOR, true, 1L, 0L, null, null, null, null);

    @Mock
    private AuthenticationManager authenticationManager;

    private LoginAuthenticator loginAuthenticator;

    @BeforeEach
    void crearConDosIntentos() {
        loginAuthenticator = new LoginAuthenticator(authenticationManager,
                new AttemptLimiter(2, Duration.ofMinutes(15), 100, new RelojDePrueba()));
    }

    @Test
    @DisplayName("Con la clave correcta devuelve el perfil del usuario")
    void autenticar_claveCorrecta_devuelveElPerfil() {
        given(authenticationManager.authenticate(any())).willReturn(autenticado());

        assertThat(loginAuthenticator.autenticar("ana@acme.com", "clave-buena", "10.0.0.1")).isEqualTo(ANA);
    }

    @Test
    @DisplayName("Tras el maximo de fallos del correo desde una IP responde 429 sin comprobar la clave")
    void autenticar_trasElMaximoDeFallos_noCompruebaLaClave() {
        given(authenticationManager.authenticate(any())).willThrow(new BadCredentialsException("Bad credentials"));
        fallar("ana@acme.com", "10.0.0.1");
        fallar(" ANA@Acme.com", "10.0.0.1");

        assertThatThrownBy(() -> loginAuthenticator.autenticar("ana@acme.com", "clave-buena", "10.0.0.1"))
                .isInstanceOf(DemasiadosIntentosException.class);
        then(authenticationManager).should(times(2)).authenticate(any());
    }

    @Test
    @DisplayName("Los fallos desde una IP no bloquean el mismo correo desde otra")
    void autenticar_desdeOtraIp_noEstaBloqueado() {
        given(authenticationManager.authenticate(any())).willThrow(new BadCredentialsException("Bad credentials"));
        fallar("ana@acme.com", "10.0.0.1");
        fallar("ana@acme.com", "10.0.0.1");

        fallar("ana@acme.com", "10.0.0.2");
    }

    @Test
    @DisplayName("Un login correcto borra los fallos anteriores del correo desde esa IP")
    void autenticar_loginCorrecto_borraLosFallos() {
        given(authenticationManager.authenticate(any()))
                .willThrow(new BadCredentialsException("Bad credentials"))
                .willReturn(autenticado())
                .willThrow(new BadCredentialsException("Bad credentials"));
        fallar("ana@acme.com", "10.0.0.1");
        loginAuthenticator.autenticar("ana@acme.com", "clave-buena", "10.0.0.1");
        fallar("ana@acme.com", "10.0.0.1");

        // Con dos intentos, si el login correcto no hubiera borrado el primer fallo, este ya seria un 429
        fallar("ana@acme.com", "10.0.0.1");
    }

    private void fallar(String email, String ip) {
        assertThatThrownBy(() -> loginAuthenticator.autenticar(email, "clave-mala", ip))
                .isInstanceOf(BadCredentialsException.class);
    }

    private static UsernamePasswordAuthenticationToken autenticado() {
        return UsernamePasswordAuthenticationToken.authenticated(new UserAccount(ANA, null), null, List.of());
    }
}
