package com.facimus.procesos.gestion.dto.response;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import com.facimus.procesos.common.security.Factor;

/**
 * Lo que deja un login o una renovacion: el refresh token en claro (la base solo guarda su hash, asi que es la unica
 * vez que se ve), el codigo de la sesion para el claim sid, el perfil del usuario, cuanto vive ese refresh token y
 * cuanto le queda a la sesion antes de que haya que volver a entrar, y con que factores y cuando confirmo la persona
 * quien es (PR 49), que van en el access token.
 */
public record SesionIniciada(String refreshToken, String sesion, UsuarioResponse usuario, Duration vidaDelRefresco,
        Duration vidaRestante, Set<Factor> factores, Instant verificadaEn) {

    /** El toString de un record imprime todos sus campos: el refresh token no debe llegar a un log. */
    @Override
    public String toString() {
        return "SesionIniciada[refreshToken=[oculto], sesion=" + sesion + ", usuario=" + usuario
                + ", vidaDelRefresco=" + vidaDelRefresco + ", vidaRestante=" + vidaRestante + ", factores=" + factores
                + ", verificadaEn=" + verificadaEn + "]";
    }
}
