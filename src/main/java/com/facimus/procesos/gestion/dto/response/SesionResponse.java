package com.facimus.procesos.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * D29: lo que el cuerpo dice de una sesion abierta o renovada. Los tokens van en cookies que el JavaScript de la web
 * no puede leer, asi que aqui no hay ninguno: solo cuanto dura el acceso, quien entro y, desde F13, cuanto aguanta la
 * sesion sin usarse y cuanto le queda, que es lo que la web necesita para avisar antes de que se cierre.
 */
@Schema(description = "The session just opened or renewed: when its access expires, the user's profile and how long "
        + "the session has left. The tokens travel in HttpOnly cookies, never in the body")
public record SesionResponse(
        @Schema(description = "Seconds until the access cookie expires and has to be renewed", example = "900")
        long expiresIn,
        UsuarioResponse usuario,
        @Schema(description = "True when the password was right and the second factor is still missing: the access "
                + "cookie then only works to send the code to POST /api/v1/auth/segundo-factor/verificacion",
                example = "false") boolean segundoFactorPendiente,
        @Schema(description = "Seconds the session survives without being renewed; null when the answer did not "
                + "renew it, as after a recent verification", example = "3600") Long inactividadSegundos,
        @Schema(description = "Seconds until the session ends and the user signs in again", example = "86400")
        Long restanteSegundos) {

    /** Lo de antes de F13, para quien solo necesita el perfil y el acceso. */
    public SesionResponse(long expiresIn, UsuarioResponse usuario, boolean segundoFactorPendiente) {
        this(expiresIn, usuario, segundoFactorPendiente, null, null);
    }
}
