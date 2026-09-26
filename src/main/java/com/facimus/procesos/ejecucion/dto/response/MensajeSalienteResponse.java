package com.facimus.procesos.ejecucion.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

import com.facimus.procesos.ejecucion.model.EstadoMensajeSaliente;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.TipoDestino;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A message the process sent, waiting in the outbox until the clock delivers it")
public record MensajeSalienteResponse(
        @Schema(example = "17") Long id,
        @Schema(example = "42") Long casoId,
        @Schema(example = "ORD-1001") String casoReferencia,
        @Schema(example = "Payment authorization request") String nombre,
        @Schema(description = "Participant it goes to, as the published version named it", example = "Payment gateway")
        String poolDestinoNombre,
        @Schema(description = "Kind of partner behind that participant", example = "PAGOS") Integracion integracion,
        @Schema(example = "SERVICIO_WEB") TipoDestino tipoDestino,
        @Schema(description = "Value the answer will find its case by", example = "ORD-1001") String clave,
        @Schema(description = "What travels inside", example = "{\"orderId\": \"ORD-1001\", \"total\": 150}")
        Map<String, Object> cuerpo,
        @Schema(example = "PENDIENTE") EstadoMensajeSaliente estado,
        @Schema(description = "Tick it was sent on", example = "2") int tickCreacion,
        @Schema(description = "Tick it is due to arrive on", example = "3") int tickEntrega,
        @Schema(example = "1") int intentos,
        @Schema(description = "Why it did not arrive, when it did not", example = "El socio no contesto.")
        String error,
        @Schema(example = "2026-09-24T15:02:00") LocalDateTime fecha) {
}
