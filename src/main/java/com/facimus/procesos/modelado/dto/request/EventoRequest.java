package com.facimus.procesos.modelado.dto.request;

import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoTemporizador;

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
        @Schema(description = "Vertical position on the diagram canvas", example = "80") int posicionY,
        @Schema(description = "For a timer event: how it says when it fires", example = "DURACION")
        TipoTemporizador tipoTemporizador,
        @Schema(description = "For a timer event: an ISO 8601 duration (PT2H), date or cycle (R3/PT1H)",
                example = "PT2H")
        @Size(max = 120, message = "El temporizador no puede superar 120 caracteres.") String temporizador,
        @Schema(description = "For a boundary event: the activity it is attached to", example = "10") Long adjuntoAId,
        @Schema(description = "For a boundary event: whether it interrupts the activity; true when empty",
                example = "true") Boolean interrumpe) {

    /** El de antes del PR 55, sin temporizador. */
    public EventoRequest(String nombre, TipoEvento tipoEvento, int posicionX, int posicionY) {
        this(nombre, tipoEvento, posicionX, posicionY, null, null, null, null);
    }
}
