package com.facimus.procesos.ejecucion.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

import com.facimus.procesos.ejecucion.model.OrigenMensajeEntrante;
import com.facimus.procesos.ejecucion.model.ResultadoCorrelacion;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A message that reached the process, and what was done with it")
public record MensajeEntranteResponse(
        @Schema(example = "31") Long id,
        @Schema(example = "1") Long procesoId,
        @Schema(description = "Case it was matched to; empty when nobody was waiting for it", example = "42")
        Long casoId,
        @Schema(example = "ORD-1001") String casoReferencia,
        @Schema(example = "Payment authorization result") String nombre,
        @Schema(description = "Value it looks for its case by", example = "ORD-1001") String clave,
        @Schema(description = "What travelled inside", example = "{\"status\": \"APPROVED\"}")
        Map<String, Object> cuerpo,
        @Schema(example = "MANUAL") OrigenMensajeEntrante origen,
        @Schema(description = "What the sender called it, so sending it twice does not process it twice",
                example = "webhook-7") String claveExterna,
        @Schema(example = "ENTREGADO_A_CASO") ResultadoCorrelacion resultado,
        @Schema(description = "Store clock tick when it arrived", example = "3") int tick,
        @Schema(example = "2026-09-24T15:05:00") LocalDateTime fecha,
        @Schema(description = "True when this same message had already been received and nothing was done again",
                example = "false") boolean repetido) {
}
