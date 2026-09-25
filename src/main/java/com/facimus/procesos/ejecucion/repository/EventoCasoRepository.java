package com.facimus.procesos.ejecucion.repository;

import java.util.List;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.ejecucion.model.EventoCaso;

/** La bitacora de los casos. Solo se inserta y se lee en orden: lo que paso no se corrige. */
public interface EventoCasoRepository extends RepositorioTenant<EventoCaso> {

    List<EventoCaso> findAllByCasoIdAndEmpresaIdOrderByIdAsc(Long casoId, Long empresaId);
}
