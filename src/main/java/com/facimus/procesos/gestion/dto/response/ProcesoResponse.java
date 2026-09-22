package com.facimus.procesos.gestion.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.gestion.model.EstadoProceso;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A process of the store")
public record ProcesoResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "Order fulfillment") String nombre,
        @Schema(example = "From checkout to delivery: payment authorization, picking, packing and shipment.")
        String descripcion,
        @Schema(example = "Fulfillment") String categoria,
        @Schema(example = "PUBLICADO") EstadoProceso estado,
        @Schema(description = "false once the process is deleted", example = "true") boolean activo,
        @Schema(example = "2026-09-21T15:00:00") LocalDateTime fechaCreacion,
        @Schema(example = "2026-09-21T15:30:00") LocalDateTime fechaModificacion,
        @Schema(description = "Send it back when editing; it goes up with every saved change", example = "0")
        Long version) {
}
