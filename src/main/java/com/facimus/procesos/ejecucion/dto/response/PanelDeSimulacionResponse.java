package com.facimus.procesos.ejecucion.dto.response;

import java.util.List;

import com.facimus.procesos.gestion.model.ModoSimulacion;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Where the store's simulation is: the clock, who moves it and what is still in the trays")
public record PanelDeSimulacionResponse(
        @Schema(description = "The store's clock, in ticks", example = "7") int reloj,
        @Schema(description = "Who moves it: whoever is testing, or a job", example = "MANUAL")
        ModoSimulacion modo,
        @Schema(description = "Messages sent and not delivered yet", example = "3") long salientesPendientes,
        @Schema(description = "Those same messages, by the kind of partner they are waiting for")
        List<PendientesPorSocioResponse> porSocio,
        @Schema(description = "Messages that arrived before anyone was waiting for them", example = "1")
        long entrantesEnEspera) {
}
