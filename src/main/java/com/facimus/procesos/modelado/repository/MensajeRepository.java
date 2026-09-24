package com.facimus.procesos.modelado.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Mensaje;

public interface MensajeRepository extends RepositorioTenant<Mensaje> {

    List<Mensaje> findAllByPoolOrigenIdAndEmpresaId(Long poolOrigenId, Long empresaId);

    List<Mensaje> findAllByPoolDestinoIdAndEmpresaId(Long poolDestinoId, Long empresaId);

    List<Mensaje> findAllByProcesoIdAndEmpresaIdOrderByIdAsc(Long procesoId, Long empresaId);

    /** Si algun mensaje del proceso se ancla a este nodo, por cualquiera de sus tres extremos. */
    @Query("select count(m) > 0 from Mensaje m where m.empresa.id = :empresaId and (m.nodoOrigen.id = :nodoId "
            + "or m.nodoDestino.id = :nodoId or m.nodoManejoError.id = :nodoId)")
    boolean tieneMensajesAnclados(@Param("nodoId") Long nodoId, @Param("empresaId") Long empresaId);
}
