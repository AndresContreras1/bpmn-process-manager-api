package com.facimus.procesos.gestion.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.facimus.procesos.gestion.model.ModoDePublicacion;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "How the store publishes its processes")
public record PoliticaDePublicacionResponse(
        @Schema(description = "DIRECTA publishes right away, as always; CON_APROBACION publishes through a review "
                + "request", example = "CON_APROBACION") ModoDePublicacion modo,
        @Schema(description = "Approvals a request needs", example = "2") int aprobacionesNecesarias,
        @Schema(description = "The process role whose people approve; empty when a list approves", example = "3")
        Long rolProcesoId,
        @Schema(description = "Its name", example = "Process owners") String rolProceso,
        @Schema(description = "The people who approve; empty when a role approves") List<Aprobador> aprobadores,
        @Schema(description = "Send it back when changing it; empty while the store never saved one", example = "0")
        Long version,
        @Schema(description = "Who changed it last", example = "1") Long modificadoPor,
        @Schema(example = "2026-11-12T10:00:00") LocalDateTime fechaModificacion) {

    public PoliticaDePublicacionResponse {
        aprobadores = aprobadores == null ? List.of() : List.copyOf(aprobadores);
    }

    /** Una persona de la lista, con su nombre de hoy. */
    public record Aprobador(@Schema(example = "4") Long id, @Schema(example = "Beto") String nombre,
            @Schema(description = "False once deactivated or anonymized: it no longer approves", example = "true")
            boolean activo) {
    }
}
