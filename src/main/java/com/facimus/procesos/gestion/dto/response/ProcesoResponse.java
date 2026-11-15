package com.facimus.procesos.gestion.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.facimus.procesos.common.Default;
import com.facimus.procesos.gestion.model.Clasificacion;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.ModoProceso;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A process of the store")
public record ProcesoResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "Order fulfillment") String nombre,
        @Schema(example = "From checkout to delivery: payment authorization, picking, packing and shipment.")
        String descripcion,
        @Schema(example = "Fulfillment") String categoria,
        @Schema(example = "PUBLICADO") EstadoProceso estado,
        @Schema(description = "false once the process is deleted", example = "true") boolean activo,
        @Schema(example = "2026-09-21T15:00:00") LocalDateTime fechaCreacion,
        @Schema(example = "2026-09-21T15:30:00") LocalDateTime fechaModificacion,
        @Schema(description = "Send it back when editing; it goes up with every saved change", example = "0")
        Long version,
        @Schema(description = "Id of the user who created it; empty when the system did, like the store registration",
                example = "2") Long creadoPor,
        @Schema(description = "Id of the user who saved the last change", example = "5") Long modificadoPor,
        @Schema(description = "Number of the version in force; empty when nothing is published yet", example = "2")
        Integer versionPublicada,
        @Schema(description = "True when the live model differs from the version in force. Empty in listings, "
                + "where working it out would mean reading one whole diagram per row, and for a guest store, "
                + "which only sees what is published", example = "true")
        Boolean borradorPendiente,
        @Schema(description = "EJECUTABLE runs cases; DOCUMENTAL is published and documented but opens none, and its "
                + "diagnosis only checks the structure", example = "EJECUTABLE") ModoProceso modo,
        @Schema(description = "Its folder; empty if it is not filed", example = "1") Long carpetaId,
        @Schema(description = "The user who owns it", example = "2") Long duenoId,
        @Schema(description = "When it has to be reviewed next", example = "2027-03-01") LocalDate proximaRevision,
        @Schema(example = "INTERNA") Clasificacion clasificacion,
        @Schema(example = "[\"logistics\"]") List<String> etiquetas,
        @Schema(description = "How many months confirming that it is still valid moves the next review", example = "12")
        Integer mesesEntreRevisiones,
        @Schema(description = "True when the next review date has passed and nobody confirmed it is still valid",
                example = "false") Boolean revisionVencida) {

    /** PR 55: un proceso viene con su modo; uno que llega sin el, como el de una instantanea vieja, es ejecutable. */
    @Default
    public ProcesoResponse {
        modo = modo == null ? ModoProceso.EJECUTABLE : modo;
        clasificacion = clasificacion == null ? Clasificacion.INTERNA : clasificacion;
        etiquetas = etiquetas == null ? List.of() : List.copyOf(etiquetas);
    }

    /** El de antes del PR 64, sin la recertificacion. */
    public ProcesoResponse(Long id, String nombre, String descripcion, String categoria, EstadoProceso estado,
            boolean activo, LocalDateTime fechaCreacion, LocalDateTime fechaModificacion, Long version, Long creadoPor,
            Long modificadoPor, Integer versionPublicada, Boolean borradorPendiente, ModoProceso modo, Long carpetaId,
            Long duenoId, LocalDate proximaRevision, Clasificacion clasificacion, List<String> etiquetas) {
        this(id, nombre, descripcion, categoria, estado, activo, fechaCreacion, fechaModificacion, version, creadoPor,
                modificadoPor, versionPublicada, borradorPendiente, modo, carpetaId, duenoId, proximaRevision,
                clasificacion, etiquetas, null, null);
    }

    /** El de antes del PR 59, sin los datos del repositorio. */
    public ProcesoResponse(Long id, String nombre, String descripcion, String categoria, EstadoProceso estado,
            boolean activo, LocalDateTime fechaCreacion, LocalDateTime fechaModificacion, Long version, Long creadoPor,
            Long modificadoPor, Integer versionPublicada, Boolean borradorPendiente, ModoProceso modo) {
        this(id, nombre, descripcion, categoria, estado, activo, fechaCreacion, fechaModificacion, version, creadoPor,
                modificadoPor, versionPublicada, borradorPendiente, modo, null, null, null, null, null);
    }

    /** El de antes del PR 55, sin modo: ejecutable, como eran todos. */
    public ProcesoResponse(Long id, String nombre, String descripcion, String categoria, EstadoProceso estado,
            boolean activo, LocalDateTime fechaCreacion, LocalDateTime fechaModificacion, Long version, Long creadoPor,
            Long modificadoPor, Integer versionPublicada, Boolean borradorPendiente) {
        this(id, nombre, descripcion, categoria, estado, activo, fechaCreacion, fechaModificacion, version, creadoPor,
                modificadoPor, versionPublicada, borradorPendiente, ModoProceso.EJECUTABLE);
    }

    /**
     * El mismo proceso visto como quedara publicado con ese numero. La instantanea se toma antes de guardar nada,
     * asi que sin esto el diagrama de la version 1 diria para siempre que el proceso era un borrador.
     */
    public ProcesoResponse publicadoComo(int numero) {
        return new ProcesoResponse(id, nombre, descripcion, categoria, EstadoProceso.PUBLICADO, activo, fechaCreacion,
                fechaModificacion, version, creadoPor, modificadoPor, numero, false, modo, carpetaId, duenoId,
                proximaRevision, clasificacion, etiquetas, mesesEntreRevisiones, revisionVencida);
    }

    /** El mismo proceso diciendo si su borrador tiene cambios sin publicar; lo calcula quien ya tiene el diagrama. */
    public ProcesoResponse conBorradorPendiente(Boolean pendiente) {
        return new ProcesoResponse(id, nombre, descripcion, categoria, estado, activo, fechaCreacion,
                fechaModificacion, version, creadoPor, modificadoPor, versionPublicada, pendiente, modo, carpetaId,
                duenoId, proximaRevision, clasificacion, etiquetas, mesesEntreRevisiones, revisionVencida);
    }
}
