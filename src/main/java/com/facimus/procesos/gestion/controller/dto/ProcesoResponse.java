package com.facimus.procesos.gestion.controller.dto;

import java.time.LocalDateTime;

import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;

public record ProcesoResponse(Long id, String nombre, String descripcion, String categoria,
        EstadoProceso estado, boolean activo, LocalDateTime fechaCreacion, LocalDateTime fechaModificacion) {

    public static ProcesoResponse of(Proceso p) {
        return new ProcesoResponse(p.getId(), p.getNombre(), p.getDescripcion(), p.getCategoria(),
                p.getEstado(), p.isActivo(), p.getFechaCreacion(), p.getFechaModificacion());
    }
}
