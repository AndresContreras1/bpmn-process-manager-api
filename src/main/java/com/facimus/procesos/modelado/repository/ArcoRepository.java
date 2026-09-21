package com.facimus.procesos.modelado.repository;

import java.util.List;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Arco;

public interface ArcoRepository extends RepositorioTenant<Arco> {

    List<Arco> findAllByOrigenIdAndEmpresaId(Long origenId, Long empresaId);

    List<Arco> findAllByDestinoIdAndEmpresaId(Long destinoId, Long empresaId);

    List<Arco> findAllByPoolIdAndEmpresaId(Long poolId, Long empresaId);

    boolean existsByOrigenIdAndDestinoIdAndEmpresaId(Long origenId, Long destinoId, Long empresaId);
}
