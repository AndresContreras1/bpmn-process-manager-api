package com.facimus.procesos.modelado.dto.response;

import java.util.List;

import com.facimus.procesos.gestion.dto.response.ProcesoResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/** El diagrama completo de un proceso: listas planas que se enlazan por id, con los mismos DTO de cada endpoint. */
@Schema(description = "A whole BPMN diagram in one response: flat lists linked by id, with the same shape as the "
        + "single-resource endpoints")
public record DiagramaResponse(
        ProcesoResponse proceso,
        @Schema(description = "In diagram order; the store's own pool first") List<PoolResponse> pools,
        @Schema(description = "Ordered by pool and by position inside the pool") List<LaneResponse> lanes,
        @Schema(description = "Linked to their lane by laneId") List<ActividadResponse> actividades,
        @Schema(description = "Linked to their lane by laneId") List<GatewayResponse> gateways,
        @Schema(description = "Sequence flows; origenId and destinoId point to activities or gateways")
        List<ArcoResponse> arcos,
        @Schema(description = "Message flows; poolOrigenId and poolDestinoId point to pools")
        List<MensajeResponse> mensajes,
        @Schema(description = "Correlation keys; mensajeId points to a message")
        List<CorrelacionResponse> correlaciones) {
}
