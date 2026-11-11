package com.facimus.procesos.modelado.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 57: lo que cambia entre dos estados de un proceso, con los bpmnId para pintarlo. */
public record ComparacionResponse(
        @Schema(description = "What is compared from", example = "3") String de,
        @Schema(description = "What is compared to", example = "borrador") String a,
        @Schema(description = "Elements only in the second") List<Elemento> agregados,
        @Schema(description = "Elements only in the first") List<Elemento> quitados,
        @Schema(description = "Elements with different data, and which") List<Cambiado> cambiados,
        @Schema(description = "Elements that only changed place or size") List<Elemento> movidos) {

    /** Un elemento: POOL, LANE, ACTIVIDAD, GATEWAY, EVENTO, ARCO, MENSAJE, CORRELACION, ARTEFACTO, ASOCIACION o PROCESO. */
    public record Elemento(
            @Schema(example = "Activity_1f3a9c0b7e42") String bpmnId,
            @Schema(example = "ACTIVIDAD") String tipo,
            @Schema(example = "Pick and pack items") String nombre) {
    }

    public record Cambiado(
            @Schema(example = "Activity_1f3a9c0b7e42") String bpmnId,
            @Schema(example = "ACTIVIDAD") String tipo,
            @Schema(example = "Pick and pack items") String nombre,
            List<Campo> campos) {
    }

    /** Un campo que cambio; un extremo que no tenia valor va vacio. */
    public record Campo(
            @Schema(example = "nombre") String campo,
            @Schema(example = "Pack") String antes,
            @Schema(example = "Pick and pack items") String despues) {
    }

    /** Si los dos son lo mismo. */
    public boolean sinDiferencias() {
        return agregados.isEmpty() && quitados.isEmpty() && cambiados.isEmpty() && movidos.isEmpty();
    }
}
