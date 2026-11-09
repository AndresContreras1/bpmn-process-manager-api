package com.facimus.procesos.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * PR 50: la API no llama al proveedor en nombre de nadie: del login solo le importa quien es. El access token que el
 * proveedor entrega no se guarda, ni en memoria ni en una sesion HTTP que la API no tiene.
 */
class SinClientesAutorizados implements OAuth2AuthorizedClientRepository {

    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String registro, Authentication principal,
            HttpServletRequest peticion) {
        return null;
    }

    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient cliente, Authentication principal,
            HttpServletRequest peticion, HttpServletResponse respuesta) {
        // Nada que guardar: ver la clase.
    }

    @Override
    public void removeAuthorizedClient(String registro, Authentication principal, HttpServletRequest peticion,
            HttpServletResponse respuesta) {
        // Nada que quitar: ver la clase.
    }
}
