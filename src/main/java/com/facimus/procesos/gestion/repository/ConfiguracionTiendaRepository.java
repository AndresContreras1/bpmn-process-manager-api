package com.facimus.procesos.gestion.repository;

import java.util.Optional;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.ConfiguracionTienda;

/** La configuracion de una tienda: una fila por empresa, creada con ella. */
public interface ConfiguracionTiendaRepository extends RepositorioTenant<ConfiguracionTienda> {

    Optional<ConfiguracionTienda> findByEmpresaId(Long empresaId);
}
