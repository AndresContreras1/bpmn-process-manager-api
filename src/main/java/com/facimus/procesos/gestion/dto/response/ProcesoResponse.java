package com.facimus.procesos.gestion.dto.response;

import java.time.LocalDateTime;

import com.facimus.procesos.gestion.model.EstadoProceso;

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
        Boolean borradorPendiente) {

    /**
     * El mismo proceso visto como quedara publicado con ese numero. La instantanea se toma antes de guardar nada,
     * asi que sin esto el diagrama de la version 1 diria para siempre que el proceso era un borrador.
     */
    public ProcesoResponse publicadoComo(int numero) {
        return new ProcesoResponse(id, nombre, descripcion, categoria, EstadoProceso.PUBLICADO, activo, fechaCreacion,
                fechaModificacion, version, creadoPor, modificadoPor, numero, false);
    }

    /** El mismo proceso diciendo si su borrador tiene cambios sin publicar; lo calcula quien ya tiene el diagrama. */
    public ProcesoResponse conBorradorPendiente(Boolean pendiente) {
        return new ProcesoResponse(id, nombre, descripcion, categoria, estado, activo, fechaCreacion,
                fechaModificacion, version, creadoPor, modificadoPor, versionPublicada, pendiente);
    }
}
