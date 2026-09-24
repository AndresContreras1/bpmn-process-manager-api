package com.facimus.procesos.modelado.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Arco;

public interface ArcoRepository extends RepositorioTenant<Arco> {

    List<Arco> findAllByOrigenIdAndEmpresaId(Long origenId, Long empresaId);

    List<Arco> findAllByDestinoIdAndEmpresaId(Long destinoId, Long empresaId);

    List<Arco> findAllByPoolIdAndEmpresaId(Long poolId, Long empresaId);

    List<Arco> findAllByPool_ProcesoIdAndEmpresaIdOrderByIdAsc(Long procesoId, Long empresaId);

    boolean existsByOrigenIdAndDestinoIdAndEmpresaId(Long origenId, Long destinoId, Long empresaId);

    boolean existsByOrigenIdAndDestinoIdAndEmpresaIdAndIdNot(Long origenId, Long destinoId,
            Long empresaId, Long id);

    /** Si el nodo esta conectado, por cualquiera de los dos extremos de un arco. */
    @Query("select count(a) > 0 from Arco a where a.empresa.id = :empresaId and (a.origen.id = :nodoId "
            + "or a.destino.id = :nodoId)")
    boolean tieneArcos(@Param("nodoId") Long nodoId, @Param("empresaId") Long empresaId);
}
