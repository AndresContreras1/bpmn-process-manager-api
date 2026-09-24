package com.facimus.procesos.modelado.dto.request;

import com.facimus.procesos.modelado.model.TipoEvento;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "New name, type and position of an event, with the version that was read")
public record EditarEventoRequest(
        @Schema(description = "Name, unique among the flow nodes of the process", example = "Order received")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres.") String nombre,
        @Schema(description = "A start event cannot keep incoming flows, and an end event cannot keep outgoing ones",
                example = "MENSAJE_INICIO")
        @NotNull(message = "El tipo de evento es obligatorio.") TipoEvento tipoEvento,
        @Schema(description = "Horizontal position on the diagram canvas", example = "20") int posicionX,
        @Schema(description = "Vertical position on the diagram canvas", example = "80") int posicionY,
        @Schema(description = "Version read with the last GET; if someone saved a change since, the edit answers 409",
                example = "0")
        @NotNull(message = "La versión es obligatoria.") Long version) {
}
