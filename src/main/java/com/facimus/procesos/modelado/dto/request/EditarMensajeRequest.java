package com.facimus.procesos.modelado.dto.request;

import java.util.List;

import com.facimus.procesos.modelado.model.AccionSiFalla;
import com.facimus.procesos.modelado.model.CampoDeMensaje;
import com.facimus.procesos.modelado.model.TipoDestino;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "New content of a message flow; its pools cannot change. The request describes the message as "
        + "it should end up, so what is left out is cleared.")
public record EditarMensajeRequest(
        @Schema(example = "Payment authorization request")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres.") String nombre,
        @Schema(example = "Order total and tokenized card.")
        @NotBlank(message = "El contenido es obligatorio.")
        @Size(max = 2000, message = "El contenido no puede superar 2000 caracteres.") String contenido,
        @Schema(description = "Node the message leaves from; only for a pool that models its flow", example = "12")
        Long nodoOrigenId,
        @Schema(description = "Node that waits for it; only for a pool that models its flow", example = "15")
        Long nodoDestinoId,
        @Schema(description = "How it travels: CORREO, SERVICIO_WEB or COLA", example = "SERVICIO_WEB")
        TipoDestino tipoDestino,
        @Schema(description = "What the process does if the message does not get through", example = "MANEJAR_ERROR")
        AccionSiFalla siFalla,
        @Schema(description = "Activity of the sending pool that handles the failure; required with MANEJAR_ERROR",
                example = "18") Long nodoManejoErrorId,
        @Schema(description = "true when the message arrives from outside the diagram; false when it is "
                + "missing", example = "false") Boolean origenExterno,
        @Schema(description = "The data that travels inside") @Valid List<CampoDeMensaje> campos,
        @Schema(example = "The payment result decides whether the order is picked or cancelled.")
        @Size(max = 1000, message = "El uso de los datos no puede superar 1000 caracteres.") String usoDeLosDatos,
        @Schema(description = "Name the body takes among the case variables", example = "payment")
        @Size(max = 60, message = "El nombre de la variable no puede superar 60 caracteres.") String variable,
        @Schema(description = "Message that answers this one, coming back from the receiving pool", example = "9")
        Long respuestaEsperadaId,
        @Schema(description = "Version read with the last GET; if someone saved a change since, the edit answers 409",
                example = "0")
        @NotNull(message = "La versión es obligatoria.") Long version) {
}
