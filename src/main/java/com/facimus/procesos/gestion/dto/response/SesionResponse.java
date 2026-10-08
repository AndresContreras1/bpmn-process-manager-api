package com.facimus.procesos.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * D29: lo que el cuerpo dice de una sesion abierta o renovada. Los tokens van en cookies que el JavaScript de la web
 * no puede leer, asi que aqui no hay ninguno: solo cuanto dura el acceso y quien entro.
 */
@Schema(description = "The session just opened or renewed: when its access expires and the user's profile. The tokens "
        + "travel in HttpOnly cookies, never in the body")
public record SesionResponse(
        @Schema(description = "Seconds until the access cookie expires and has to be renewed", example = "900")
        long expiresIn,
        UsuarioResponse usuario) {
}
