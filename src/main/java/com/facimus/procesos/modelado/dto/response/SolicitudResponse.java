package com.facimus.procesos.modelado.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.facimus.procesos.modelado.model.EstadoSolicitud;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 64: una solicitud de revision, con quienes la aprueban, lo que decidio cada uno y, sola, lo que cambia. */
@Schema(description = "A review request: the draft frozen to be published, who approves it and what each one "
        + "decided")
public record SolicitudResponse(
        @Schema(example = "4") Long id,
        @Schema(example = "7") Long procesoId,
        @Schema(description = "The name of the process", example = "Order fulfillment") String proceso,
        @Schema(description = "ABIERTA, PUBLICADA, APROBADA_SIN_PUBLICAR, CAMBIOS_PEDIDOS, RECHAZADA or RETIRADA",
                example = "ABIERTA") EstadoSolicitud estado,
        @Schema(description = "Who asked for it") PersonaResponse solicitante,
        @Schema(example = "Adds the **fraud check** before packing.") String markdown,
        @Schema(example = "<p>Adds the <strong>fraud check</strong> before packing.</p>") String html,
        @Schema(description = "The version it publishes", example = "3") int numero,
        @Schema(description = "The version in force when it was asked for; empty if there was none", example = "2")
        Integer versionBase,
        @Schema(description = "The draft revision saved when it was asked for; empty once old revisions are cleaned",
                example = "31") Long revisionId,
        @Schema(description = "Approvals it needs", example = "2") int aprobacionesNecesarias,
        @Schema(description = "Approvals it has", example = "1") int aprobaciones,
        @Schema(description = "Who can decide on it, fixed when it was asked for") List<PersonaResponse> aprobadores,
        @Schema(description = "What each approver decided, oldest first") List<DecisionResponse> decisiones,
        @Schema(description = "Why an approved request was not published",
                example = "La versión vigente cambió desde que se pidió la revisión (era la 2 y ahora es la 3): hay "
                        + "que pedirla de nuevo.") String motivo,
        @Schema(description = "Who closed it: the approver who decided last, or who withdrew it")
        PersonaResponse cerradaPor,
        @Schema(example = "2026-11-12T11:00:00") LocalDateTime cerradaEn,
        @Schema(description = "The version it published", example = "3") Integer versionPublicada,
        @Schema(example = "2026-11-12T10:00:00") LocalDateTime fechaCreacion,
        @Schema(description = "What it changes against the version in force when it was asked for; only when one "
                + "request is answered, not in lists") ComparacionResponse diferencia) {

    public SolicitudResponse {
        aprobadores = aprobadores == null ? List.of() : List.copyOf(aprobadores);
        decisiones = decisiones == null ? List.of() : List.copyOf(decisiones);
    }
}
