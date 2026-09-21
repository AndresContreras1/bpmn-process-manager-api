package com.facimus.procesos.modelado.dto.response;

import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoParticipante;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A participant of the process")
public record PoolResponse(
        @Schema(example = "3") Long id,
        @Schema(example = "Payment gateway") String nombre,
        @Schema(example = "SISTEMA_EXTERNO") TipoParticipante tipoParticipante,
        @Schema(example = "true") boolean cajaNegra,
        @Schema(description = "Position in the process; the store's own pool is 0", example = "2") int orden,
        @Schema(example = "1") Long procesoId) {

    public static PoolResponse of(Pool p) {
        return new PoolResponse(p.getId(), p.getNombre(), p.getTipoParticipante(), p.isCajaNegra(),
                p.getOrden(), p.getProceso().getId());
    }
}
