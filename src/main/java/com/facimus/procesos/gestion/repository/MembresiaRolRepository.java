package com.facimus.procesos.gestion.repository;

import java.util.Collection;
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

    /** PR 64: las personas que tienen un rol, que son los aprobadores cuando la politica de publicacion lo nombra. */
    @Query("""
            select m.usuario.id from MembresiaRol m
            where m.empresa.id = :empresaId and m.rolProceso.id = :rolId
            order by m.usuario.id
            """)
    List<Long> personasDe(@Param("empresaId") Long empresaId, @Param("rolId") Long rolId);

    /** PR 64: los roles de varias personas de una vez, de a pares: el id de la persona y el de un rol suyo. */
    @Query("""
            select m.usuario.id, m.rolProceso.id from MembresiaRol m
            where m.empresa.id = :empresaId and m.usuario.id in :usuarios
            """)
    List<Object[]> rolesDeCada(@Param("empresaId") Long empresaId, @Param("usuarios") Collection<Long> usuarios);

    /** Deja a una persona sin ninguno, que es el primer paso de reemplazar su lista entera. */
    @Modifying
    @Query("delete from MembresiaRol m where m.empresa.id = :empresaId and m.usuario.id = :usuarioId")
    int borrarLasDe(@Param("empresaId") Long empresaId, @Param("usuarioId") Long usuarioId);
}
