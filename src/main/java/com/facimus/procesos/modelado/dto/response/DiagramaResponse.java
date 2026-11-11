package com.facimus.procesos.modelado.dto.response;

import java.util.List;

import com.facimus.procesos.gestion.dto.response.ProcesoResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/** El diagrama completo de un proceso: listas planas que se enlazan por id, con los mismos DTO de cada endpoint. */
@Schema(description = "A whole BPMN diagram in one response: flat lists linked by id, with the same shape as the "
        + "single-resource endpoints")
public record DiagramaResponse(
        ProcesoResponse proceso,
        @Schema(description = "True when another store owns the process and shares it read-only (HU-23)")
        boolean compartido,
        @Schema(description = "In diagram order; the store's own pool first") List<PoolResponse> pools,
        @Schema(description = "Ordered by pool and by position inside the pool") List<LaneResponse> lanes,
        @Schema(description = "Linked to their lane by laneId") List<ActividadResponse> actividades,
        @Schema(description = "Linked to their lane by laneId") List<GatewayResponse> gateways,
        @Schema(description = "Start, end and message events, linked to their lane by laneId")
        List<EventoResponse> eventos,
        @Schema(description = "Sequence flows; origenId and destinoId point to activities or gateways")
        List<ArcoResponse> arcos,
        @Schema(description = "Message flows; poolOrigenId and poolDestinoId point to pools")
        List<MensajeResponse> mensajes,
        @Schema(description = "Correlation keys; mensajeId points to a message")
        List<CorrelacionResponse> correlaciones,
        @Schema(description = "Text annotations, groups, data objects and data stores") List<ArtefactoResponse> artefactos,
        @Schema(description = "Associations and data associations; origen and destino are bpmnIds")
        List<AsociacionResponse> asociaciones) {

    /** PR 55: una instantanea de antes no trae artefactos ni asociaciones: no tenia ninguno. */
    public DiagramaResponse {
        artefactos = artefactos == null ? List.of() : artefactos;
        asociaciones = asociaciones == null ? List.of() : asociaciones;
    }

    /** El de antes del PR 55, sin artefactos ni asociaciones. */
    public DiagramaResponse(ProcesoResponse proceso, boolean compartido, List<PoolResponse> pools,
            List<LaneResponse> lanes, List<ActividadResponse> actividades, List<GatewayResponse> gateways,
            List<EventoResponse> eventos, List<ArcoResponse> arcos, List<MensajeResponse> mensajes,
            List<CorrelacionResponse> correlaciones) {
        this(proceso, compartido, pools, lanes, actividades, gateways, eventos, arcos, mensajes, correlaciones,
                List.of(), List.of());
    }

    /** El mismo dibujo con otro encabezado: lo usa quien responde una version publicada con el proceso de hoy. */
    public DiagramaResponse conProceso(ProcesoResponse otro, boolean esCompartido) {
        return new DiagramaResponse(otro, esCompartido, pools, lanes, actividades, gateways, eventos, arcos,
                mensajes, correlaciones, artefactos, asociaciones);
    }
}
