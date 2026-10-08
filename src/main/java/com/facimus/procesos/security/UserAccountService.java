package com.facimus.procesos.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.service.UsuarioService;

/**
 * HU-03: carga la cuenta del correo para el AuthenticationManager. Un correo desconocido y un usuario desactivado
 * terminan igual: DaoAuthenticationProvider los convierte en el mismo BadCredentialsException de una clave mala.
 * Despues de una clave buena guarda el hash que el login rehizo, si el que habia era de un algoritmo o un costo viejo.
 */
@Component
public class UserAccountService implements UserDetailsService, UserDetailsPasswordService {

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

    @Override
    public UserDetails updatePassword(UserDetails cuenta, String hashNuevo) {
        UsuarioResponse usuario = ((UserAccount) cuenta).usuario();
        usuarioService.renovarHash(usuario.empresaId(), usuario.id(), hashNuevo);
        return new UserAccount(usuario, hashNuevo);
    }
}
