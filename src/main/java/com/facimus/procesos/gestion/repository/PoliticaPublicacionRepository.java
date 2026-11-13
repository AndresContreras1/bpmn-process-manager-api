package com.facimus.procesos.gestion.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.PoliticaPublicacion;

/** PR 64: la politica de publicacion de cada tienda, una fila como mucho. */
public interface PoliticaPublicacionRepository extends RepositorioTenant<PoliticaPublicacion> {

    Optional<PoliticaPublicacion> findByEmpresaId(Long empresaId);

    /** Si un rol de proceso es el de los aprobadores, aunque la politica este apagada: volveria a usarlo. */
    boolean existsByEmpresaIdAndRolProcesoId(Long empresaId, Long rolProcesoId);

    /** Quien se anonimiza deja la lista de aprobadores, como deja sus roles de proceso. */
    @Modifying
    @Query(value = "update politicas_publicacion set aprobadores = array_remove(aprobadores, :usuarioId) "
            + "where empresa_id = :empresaId", nativeQuery = true)
    int quitarAprobador(@Param("empresaId") Long empresaId, @Param("usuarioId") Long usuarioId);
}
