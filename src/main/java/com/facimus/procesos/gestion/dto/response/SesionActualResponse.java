package com.facimus.procesos.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * F13: lo que la web pide al arrancar en vez de recordarlo en el navegador: quien entro y cuanto le queda a su
 * sesion, para avisar antes de que la inactividad la cierre.
 */
@Schema(description = "The session of the cookies as it is now: the user's profile and how long it has left")
public record SesionActualResponse(
        UsuarioResponse usuario,
        @Schema(description = "True on the half-done login of a second factor: only the code is missing",
                example = "false") boolean segundoFactorPendiente,
        @Schema(description = "Seconds until the session closes if nothing renews it; null on a half-done login",
                example = "3480") Long inactividadSegundos,
        @Schema(description = "Seconds until the session ends and the user signs in again; null on a half-done "
                + "login", example = "82800") Long restanteSegundos) {
}
