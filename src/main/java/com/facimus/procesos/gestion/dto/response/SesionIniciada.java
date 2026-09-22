package com.facimus.procesos.gestion.dto.response;

/**
 * Lo que deja un login o una renovacion: el refresh token en claro (la base solo guarda su hash, asi que es la unica
 * vez que se ve), el codigo de la sesion para el claim sid y el perfil del usuario.
 */
public record SesionIniciada(String refreshToken, String sesion, UsuarioResponse usuario) {

    /** El toString de un record imprime todos sus campos: el refresh token no debe llegar a un log. */
    @Override
    public String toString() {
        return "SesionIniciada[refreshToken=[oculto], sesion=" + sesion + ", usuario=" + usuario + "]";
    }
}
