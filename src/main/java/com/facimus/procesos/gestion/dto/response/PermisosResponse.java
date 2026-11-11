package com.facimus.procesos.gestion.dto.response;

import java.util.Set;

import com.facimus.procesos.common.model.Accion;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "What the person asking can do with a process")
public record PermisosResponse(@Schema(example = "[\"VER\", \"MODELAR\"]") Set<Accion> acciones) {

    public PermisosResponse {
        acciones = Set.copyOf(acciones);
    }
}
