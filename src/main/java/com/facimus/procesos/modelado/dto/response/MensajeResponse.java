package com.facimus.procesos.modelado.dto.response;

import com.facimus.procesos.modelado.model.Mensaje;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A message flow of the process")
public record MensajeResponse(
        @Schema(example = "5") Long id,
        @Schema(example = "Payment authorization request") String nombre,
        @Schema(example = "Order total and tokenized card.") String contenido,
        @Schema(example = "1") Long poolOrigenId,
        @Schema(example = "3") Long poolDestinoId,
        @Schema(example = "1") Long procesoId) {

    public static MensajeResponse of(Mensaje m) {
        return new MensajeResponse(m.getId(), m.getNombre(), m.getContenido(), m.getPoolOrigen().getId(),
                m.getPoolDestino().getId(), m.getProceso().getId());
    }
}
