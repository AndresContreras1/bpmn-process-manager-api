package com.facimus.procesos.gestion.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.FestivoEditado;

/** PR 68: lo que una tienda cambio de los festivos de su pais, dia por dia. */
public interface FestivoEditadoRepository extends RepositorioTenant<FestivoEditado> {

    Optional<FestivoEditado> findByEmpresaIdAndFecha(Long empresaId, LocalDate fecha);

    List<FestivoEditado> findAllByEmpresaIdOrderByFechaAsc(Long empresaId);
}
