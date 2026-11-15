package com.facimus.procesos.modelado.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.VersionDocumento;

/** PR 61: los archivos subidos a cada documento. */
public interface VersionDocumentoRepository extends RepositorioTenant<VersionDocumento> {

    List<VersionDocumento> findAllByDocumento_IdAndEmpresaIdOrderByNumeroDesc(Long documentoId, Long empresaId);

    Optional<VersionDocumento> findByDocumento_IdAndNumeroAndEmpresaId(Long documentoId, int numero, Long empresaId);

    /** La ultima version de cada documento de la lista, en una sola consulta. */
    @Query("""
            select v from VersionDocumento v
            where v.empresa.id = :empresaId and v.documento.id in :documentos
              and v.numero = v.documento.versionActual
            """)
    List<VersionDocumento> ultimas(Long empresaId, Collection<Long> documentos);

    /** Lo que ya ocupa la tienda en el almacen, para su cuota: todas las versiones, tambien las retenidas. */
    @Query("select coalesce(sum(v.tamano), 0) from VersionDocumento v where v.empresa.id = :empresaId")
    long ocupado(Long empresaId);
}
