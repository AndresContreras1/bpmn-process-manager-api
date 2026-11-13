package com.facimus.procesos.gestion.repository;

import java.util.Optional;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.Calendario;

/** PR 68: el calendario de una tienda, si ya lo edito. */
public interface CalendarioRepository extends RepositorioTenant<Calendario> {

    Optional<Calendario> findByEmpresaId(Long empresaId);
}
