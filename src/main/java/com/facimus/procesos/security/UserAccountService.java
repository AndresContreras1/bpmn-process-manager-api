package com.facimus.procesos.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import com.facimus.procesos.gestion.service.UsuarioService;

/**
 * HU-03: carga la cuenta del correo para el AuthenticationManager. Un correo desconocido y un usuario desactivado
 * terminan igual: DaoAuthenticationProvider los convierte en el mismo BadCredentialsException de una clave mala.
 */
@Component
public class UserAccountService implements UserDetailsService {

    private final UsuarioService usuarioService;

    public UserAccountService(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return usuarioService.buscarCredenciales(email)
                .map(credenciales -> new UserAccount(credenciales.usuario(), credenciales.claveHash()))
                .orElseThrow(() -> new UsernameNotFoundException("No hay un usuario activo con ese correo."));
    }
}
