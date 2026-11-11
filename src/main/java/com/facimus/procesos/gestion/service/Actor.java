package com.facimus.procesos.gestion.service;

import com.facimus.procesos.common.model.RolAcceso;

/** PR 60: quien pide algo. */
public record Actor(Long usuarioId, Long empresaId, RolAcceso rol) {
}
