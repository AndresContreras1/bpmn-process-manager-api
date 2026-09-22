package com.facimus.procesos.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.facimus.procesos.gestion.dto.response.UsuarioResponse;

/**
 * El usuario como lo ve Spring Security durante el login: su perfil y el hash de su clave. El AuthenticationManager
 * borra el hash apenas comprueba la contrasena (eraseCredentials), asi que no se queda en memoria.
 */
public final class UserAccount implements UserDetails, CredentialsContainer {

    private final UsuarioResponse usuario;
    private String claveHash;

    public UserAccount(UsuarioResponse usuario, String claveHash) {
        this.usuario = usuario;
        this.claveHash = claveHash;
    }

    public UsuarioResponse usuario() {
        return usuario;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(usuario.rolAcceso().name()));
    }

    @Override
    public String getPassword() {
        return claveHash;
    }

    @Override
    public String getUsername() {
        return usuario.email();
    }

    @Override
    public void eraseCredentials() {
        claveHash = null;
    }
}
