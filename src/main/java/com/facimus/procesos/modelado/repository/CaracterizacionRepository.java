package com.facimus.procesos.modelado.repository;

import java.util.Optional;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Caracterizacion;

/** PR 62: la ficha de caracterizacion de cada proceso. */
public interface CaracterizacionRepository extends RepositorioTenant<Caracterizacion> {

    Optional<Caracterizacion> findByProcesoIdAndEmpresaId(Long procesoId, Long empresaId);
}
