package com.facimus.procesos.gestion.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One entry of a process change history")
public record HistorialCambioResponse(
        @Schema(example = "7") Long id,
        @Schema(example = "2026-09-21T15:30:00") LocalDateTime fechaCambio,
        @Schema(example = "Proceso editado.") String descripcionCambio,
        @Schema(description = "Name of the user who made the change", example = "Administrador Demo")
        String autorNombre) {
}
