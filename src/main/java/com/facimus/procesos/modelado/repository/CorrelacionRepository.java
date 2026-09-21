package com.facimus.procesos.modelado.repository;

import java.util.Optional;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Correlacion;

public interface CorrelacionRepository extends RepositorioTenant<Correlacion> {

    Optional<Correlacion> findByMensajeIdAndEmpresaId(Long mensajeId, Long empresaId);
}
