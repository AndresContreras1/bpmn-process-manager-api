package com.facimus.procesos.modelado.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.ExtensionXml;

/** PR 54: las extensiones ajenas que trajo el ultimo archivo importado en un proceso. */
public interface ExtensionXmlRepository extends RepositorioTenant<ExtensionXml> {

    List<ExtensionXml> findAllByProcesoIdAndEmpresaIdOrderByIdAsc(Long procesoId, Long empresaId);

    /** Se reemplazan enteras con cada importacion: el archivo nuevo es la verdad. */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from ExtensionXml e where e.procesoId = :procesoId and e.empresa.id = :empresaId")
    int borrarDelProceso(@Param("procesoId") Long procesoId, @Param("empresaId") Long empresaId);
}
