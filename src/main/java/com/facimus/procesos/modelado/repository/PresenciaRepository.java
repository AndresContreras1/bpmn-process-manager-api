package com.facimus.procesos.modelado.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Presencia;

/** PR 58: las conexiones abiertas. Las modifican el hilo de los latidos y el fin de una conexion, fuera de una peticion. */
public interface PresenciaRepository extends RepositorioTenant<Presencia> {

    List<Presencia> findAllByProcesoIdAndEmpresaIdOrderByIdAsc(Long procesoId, Long empresaId);

    long countByUsuarioIdAndEmpresaIdAndUltimoLatidoAfter(Long usuarioId, Long empresaId, LocalDateTime desde);

    List<Presencia> findAllByUltimoLatidoBefore(LocalDateTime antes);

    @Transactional
    @Modifying
    @Query("delete from Presencia p where p.conexion = :conexion")
    int borrarConexion(@Param("conexion") String conexion);

    @Transactional
    @Modifying
    @Query("delete from Presencia p where p.id in :ids")
    int borrar(@Param("ids") Collection<Long> ids);

    @Transactional
    @Modifying
    @Query("update Presencia p set p.ultimoLatido = :ahora where p.conexion in :conexiones")
    int latir(@Param("conexiones") Collection<String> conexiones, @Param("ahora") LocalDateTime ahora);
}
