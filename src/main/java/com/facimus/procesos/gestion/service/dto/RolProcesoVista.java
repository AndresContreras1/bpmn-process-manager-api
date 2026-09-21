package com.facimus.procesos.gestion.service.dto;

import com.facimus.procesos.gestion.model.RolProceso;

/** Envuelve un RolProceso con la informacion de uso que pide HU-20 (para decidir si se puede eliminar). */
public record RolProcesoVista(RolProceso rol, long procesosQueLoUsan, boolean enUso) {
}
