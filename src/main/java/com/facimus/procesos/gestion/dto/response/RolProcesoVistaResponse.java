package com.facimus.procesos.gestion.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.facimus.procesos.common.Default;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A process role and how much it is used")
public record RolProcesoVistaResponse(
        @Schema(example = "2") Long id,
        @Schema(example = "Warehouse") String nombre,
        @Schema(example = "Picks, packs and ships the orders.") String descripcion,
        @Schema(description = "Active processes with a lane for this role", example = "1") long procesosQueLoUsan,
        @Schema(description = "A role in use cannot be deleted", example = "true") boolean enUso,
        @Schema(description = "Send it back when editing; it goes up with every saved change", example = "0")
        Long version,
        @Schema(description = "Id of the user who created it; empty when the system did, like the store registration",
                example = "2") Long creadoPor,
        @Schema(example = "2026-09-21T15:00:00") LocalDateTime fechaCreacion,
        @Schema(description = "Id of the user who saved the last change", example = "5") Long modificadoPor,
        @Schema(example = "2026-09-21T15:30:00") LocalDateTime fechaModificacion,
        @Schema(description = "HU-20.4: the processes that use it. Only when asking for one role, not in the list")
        List<ProcesoDelRolResponse> procesos) {

    @Default
    public RolProcesoVistaResponse {
        procesos = procesos == null ? List.of() : List.copyOf(procesos);
    }

    /** El de la lista, que solo cuenta. */
    public RolProcesoVistaResponse(Long id, String nombre, String descripcion, long procesosQueLoUsan, boolean enUso,
            Long version, Long creadoPor, LocalDateTime fechaCreacion, Long modificadoPor,
            LocalDateTime fechaModificacion) {
        this(id, nombre, descripcion, procesosQueLoUsan, enUso, version, creadoPor, fechaCreacion, modificadoPor,
                fechaModificacion, List.of());
    }

    public RolProcesoVistaResponse conProcesos(List<ProcesoDelRolResponse> lista) {
        return new RolProcesoVistaResponse(id, nombre, descripcion, procesosQueLoUsan, enUso, version, creadoPor,
                fechaCreacion, modificadoPor, fechaModificacion, lista);
    }
}
