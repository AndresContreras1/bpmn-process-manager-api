package com.facimus.procesos.gestion.dto.response;

/**
 * HU-03: lo que el login necesita para comprobar la contrasena, el perfil de un usuario activo y el hash de su clave.
 * Solo lo lee la capa de seguridad; nunca se serializa ni sale de la API.
 */
public record CredencialesUsuario(UsuarioResponse usuario, String claveHash) {

    /** El toString de un record imprime todos sus campos: el hash no debe llegar a un log. */
    @Override
    public String toString() {
        return "CredencialesUsuario[usuario=" + usuario + ", claveHash=[oculta]]";
    }
}
