package com.facimus.procesos.modelado.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Comentario;

/** PR 63: los comentarios de cada proceso. Los hilos se filtran con ComentarioSpecifications. */
public interface ComentarioRepository extends RepositorioTenant<Comentario>, JpaSpecificationExecutor<Comentario> {

    /** Las respuestas que siguen en pie de unos hilos, de la mas vieja a la mas nueva. */
    @Query("""
            select c from Comentario c
            where c.empresa.id = :empresaId and c.padreId in :hilos and c.eliminadoEn is null
            order by c.fechaCreacion asc, c.id asc
            """)
    List<Comentario> respuestasDe(@Param("empresaId") Long empresaId, @Param("hilos") Collection<Long> hilos);

    /** Quienes escribieron en un hilo, en el primero o en una respuesta que sigue en pie. */
    @Query("""
            select distinct c.autorId from Comentario c
            where c.empresa.id = :empresaId and (c.id = :hiloId or c.padreId = :hiloId) and c.eliminadoEn is null
            """)
    List<Long> participantes(@Param("empresaId") Long empresaId, @Param("hiloId") Long hiloId);

    /** Los comentarios que mencionan a una persona, como al anonimizarla. */
    @Query(value = "select * from comentarios where empresa_id = :empresaId and :usuarioId = any(menciones)",
            nativeQuery = true)
    List<Comentario> mencionandoA(@Param("empresaId") Long empresaId, @Param("usuarioId") Long usuarioId);
}
