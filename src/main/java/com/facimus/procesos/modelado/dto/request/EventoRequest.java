package com.facimus.procesos.modelado.dto.request;

import com.facimus.procesos.modelado.model.TipoEvento;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "An event (start, end or message) inside a lane")
public record EventoRequest(
        @Schema(description = "Name, unique among the flow nodes of the process", example = "Order received")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres.") String nombre,
        @Schema(description = "INICIO and MENSAJE_INICIO start the process, FIN and MENSAJE_FIN end a path, and "
                + "MENSAJE_INTERMEDIO waits for a message in the middle of the flow", example = "MENSAJE_INICIO")
        @NotNull(message = "El tipo de evento es obligatorio.") TipoEvento tipoEvento,
        @Schema(description = "Horizontal position on the diagram canvas", example = "20") int posicionX,
        @Schema(description = "Vertical position on the diagram canvas", example = "80") int posicionY) {
}
