package com.facimus.procesos.gestion.controller.dto;

import java.time.LocalDateTime;

import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;

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
        @Schema(example = "2026-09-21T15:30:00") LocalDateTime fechaModificacion) {

    public static ProcesoResponse of(Proceso p) {
        return new ProcesoResponse(p.getId(), p.getNombre(), p.getDescripcion(), p.getCategoria(),
                p.getEstado(), p.isActivo(), p.getFechaCreacion(), p.getFechaModificacion());
    }
}
