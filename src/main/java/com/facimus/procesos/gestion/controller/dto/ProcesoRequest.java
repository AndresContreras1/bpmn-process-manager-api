package com.facimus.procesos.gestion.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "New process of the store")
public record ProcesoRequest(
        @Schema(description = "Name, unique among the store's active processes", example = "Order fulfillment")
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @Schema(description = "What the process covers",
                example = "From checkout to delivery: payment authorization, picking, packing and shipment.")
        @NotBlank(message = "La descripcion es obligatoria.") String descripcion,
        @Schema(description = "Free-text category used to filter processes", example = "Fulfillment")
        @NotBlank(message = "La categoria es obligatoria.") String categoria) {
}
