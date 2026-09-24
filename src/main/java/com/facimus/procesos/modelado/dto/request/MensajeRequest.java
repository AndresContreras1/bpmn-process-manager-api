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

@Schema(description = "A message flow between two different pools of the process. Everything below the two pools is "
        + "optional: a message can be drawn first and detailed later.")
public record MensajeRequest(
        @Schema(example = "Payment authorization request")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres.") String nombre,
        @Schema(example = "Order total and tokenized card.")
        @NotBlank(message = "El contenido es obligatorio.")
        @Size(max = 2000, message = "El contenido no puede superar 2000 caracteres.") String contenido,
        @Schema(description = "Sending pool, a participant of the same process", example = "1")
        @NotNull(message = "El pool de origen es obligatorio.") Long poolOrigenId,
        @Schema(description = "Receiving pool, different from the sender", example = "3")
        @NotNull(message = "El pool de destino es obligatorio.") Long poolDestinoId,
        @Schema(description = "Node the message leaves from: a message end event or an activity of type ENVIO or "
                + "SERVICIO. Only for a pool that models its flow", example = "12") Long nodoOrigenId,
        @Schema(description = "Node that waits for it: a message start or intermediate event, or an activity of type "
                + "RECEPCION. Only for a pool that models its flow", example = "15") Long nodoDestinoId,
        @Schema(description = "How it travels: CORREO, SERVICIO_WEB or COLA", example = "SERVICIO_WEB")
        TipoDestino tipoDestino,
        @Schema(description = "What the process does if the message does not get through: CONTINUAR, MANEJAR_ERROR "
                + "or FINALIZAR. CONTINUAR when it is missing", example = "MANEJAR_ERROR") AccionSiFalla siFalla,
        @Schema(description = "Activity of the sending pool that handles the failure; required with MANEJAR_ERROR",
                example = "18") Long nodoManejoErrorId,
        @Schema(description = "true when the message arrives from outside the diagram and no node throws it; "
                + "false when it is missing", example = "false") Boolean origenExterno,
        @Schema(description = "The data that travels inside") @Valid List<CampoDeMensaje> campos,
        @Schema(example = "The payment result decides whether the order is picked or cancelled.")
        @Size(max = 1000, message = "El uso de los datos no puede superar 1000 caracteres.") String usoDeLosDatos,
        @Schema(description = "Name the body takes among the case variables; derived from the message name when it "
                + "is missing", example = "payment")
        @Size(max = 60, message = "El nombre de la variable no puede superar 60 caracteres.") String variable,
        @Schema(description = "Message that answers this one, coming back from the receiving pool", example = "9")
        Long respuestaEsperadaId) {
}
