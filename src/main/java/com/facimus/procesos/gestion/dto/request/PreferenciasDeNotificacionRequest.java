package com.facimus.procesos.gestion.dto.request;

import java.util.List;

import com.facimus.procesos.common.notificaciones.EnvioPorCorreo;
import com.facimus.procesos.common.notificaciones.TipoDeNotificacion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PR 63: como quiere una persona el correo de los tipos que nombra; los demas quedan como estaban. */
@Schema(description = "How the signed-in person wants each type of notification by e-mail. The types left out keep "
        + "what they had")
public record PreferenciasDeNotificacionRequest(
        @NotEmpty(message = "Di al menos una preferencia.")
        @Size(max = 20, message = "No pueden ser más de 20 preferencias.")
        List<@Valid @NotNull(message = "Una preferencia no puede ir vacía.") Preferencia> preferencias) {

    @Schema(description = "One type and how it arrives by e-mail")
    public record Preferencia(
            @Schema(example = "HILO_RESUELTO") @NotNull(message = "El tipo es obligatorio.") TipoDeNotificacion tipo,
            @Schema(description = "INMEDIATO, RESUMEN_DIARIO or NUNCA", example = "RESUMEN_DIARIO")
            @NotNull(message = "El envío es obligatorio.") EnvioPorCorreo envio) {
    }
}
