package com.facimus.procesos.gestion.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.MembresiaRol;

/** Que roles de proceso tiene cada persona. Se reemplaza entera: no hay edicion de una membresia suelta. */
public interface MembresiaRolRepository extends RepositorioTenant<MembresiaRol> {

    List<MembresiaRol> findAllByUsuarioIdAndEmpresaIdOrderByIdAsc(Long usuarioId, Long empresaId);

    /** Los ids de los roles de una persona, que es lo unico que la bandeja propia necesita. */
    @Query("""
            select m.rolProceso.id from MembresiaRol m
            where m.empresa.id = :empresaId and m.usuario.id = :usuarioId
            order by m.rolProceso.id
            """)
    List<Long> rolesDe(@Param("empresaId") Long empresaId, @Param("usuarioId") Long usuarioId);

    /** PR 71: quienes pertenecen a un rol de proceso: a quienes avisa el plazo de una tarea que nadie tiene. */
    @Query("""
            select m.usuario.id from MembresiaRol m
            where m.empresa.id = :empresaId and m.rolProceso.id = :rolProcesoId
            order by m.usuario.id
            """)
    List<Long> miembrosDe(@Param("empresaId") Long empresaId, @Param("rolProcesoId") Long rolProcesoId);

    /** Deja a una persona sin ninguno, que es el primer paso de reemplazar su lista entera. */
    @Modifying
    @Query("delete from MembresiaRol m where m.empresa.id = :empresaId and m.usuario.id = :usuarioId")
    int borrarLasDe(@Param("empresaId") Long empresaId, @Param("usuarioId") Long usuarioId);
}
