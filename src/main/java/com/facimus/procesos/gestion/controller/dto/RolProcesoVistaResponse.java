package com.facimus.procesos.gestion.controller.dto;

import com.facimus.procesos.gestion.service.dto.RolProcesoVista;

public record RolProcesoVistaResponse(Long id, String nombre, String descripcion, long procesosQueLoUsan,
        boolean enUso) {

    public static RolProcesoVistaResponse of(RolProcesoVista v) {
        return new RolProcesoVistaResponse(v.rol().getId(), v.rol().getNombre(), v.rol().getDescripcion(),
                v.procesosQueLoUsan(), v.enUso());
    }
}
