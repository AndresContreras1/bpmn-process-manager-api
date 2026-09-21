package com.facimus.procesos.gestion.controller.dto;

import java.time.LocalDateTime;

import com.facimus.procesos.gestion.model.HistorialCambio;

public record HistorialCambioResponse(Long id, LocalDateTime fechaCambio, String descripcionCambio,
        String autorNombre) {

    public static HistorialCambioResponse of(HistorialCambio h) {
        return new HistorialCambioResponse(h.getId(), h.getFechaCambio(), h.getDescripcionCambio(),
                h.getAutor().getNombre());
    }
}
