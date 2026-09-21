package com.facimus.procesos.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "New data of an existing process")
public record EditarProcesoRequest(
        @Schema(description = "Name, unique among the store's active processes", example = "Order fulfillment")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres.") String nombre,
        @Schema(description = "What the process covers",
                example = "From checkout to delivery: payment authorization, picking, packing and shipment.")
        @NotBlank(message = "La descripcion es obligatoria.")
        @Size(max = 4000, message = "La descripcion no puede superar 4000 caracteres.") String descripcion,
        @Schema(description = "Free-text category used to filter processes", example = "Fulfillment")
        @NotBlank(message = "La categoria es obligatoria.")
        @Size(max = 80, message = "La categoria no puede superar 80 caracteres.") String categoria) {
}
