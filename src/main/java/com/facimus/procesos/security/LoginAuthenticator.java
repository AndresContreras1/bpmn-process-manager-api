package com.facimus.procesos.security;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.facimus.procesos.gestion.dto.response.UsuarioResponse;

/** HU-03: comprueba el correo y la contrasena con el AuthenticationManager y devuelve el perfil del usuario. */
@Component
public class LoginAuthenticator {

    private final AuthenticationManager authenticationManager;

    public LoginAuthenticator(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    /** Si el correo no existe, el usuario esta desactivado o la clave falla, lanza siempre el mismo BadCredentials. */
    public UsuarioResponse autenticar(String email, String password) {
        Authentication autenticado = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        return ((UserAccount) autenticado.getPrincipal()).usuario();
    }
}
