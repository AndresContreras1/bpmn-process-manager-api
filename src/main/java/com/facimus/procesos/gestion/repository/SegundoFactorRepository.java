package com.facimus.procesos.gestion.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.SegundoFactor;

public interface SegundoFactorRepository extends RepositorioTenant<SegundoFactor> {

    Optional<SegundoFactor> findByUsuarioIdAndEmpresaId(Long usuarioId, Long empresaId);

    /** Si el login tiene que pedirle el codigo: solo cuando lo confirmo. */
    boolean existsByUsuarioIdAndEmpresaIdAndConfirmadoEnIsNotNull(Long usuarioId, Long empresaId);

    /**
     * Acepta el paso solo si es posterior al ultimo: de dos logins a la vez con el mismo codigo, el segundo cambia 0
     * filas y no entra.
     */
    @Modifying
    @Query("update SegundoFactor f set f.ultimoPaso = :paso where f.id = :id and f.ultimoPaso < :paso")
    int aceptarPaso(Long id, long paso);

    @Modifying
    @Query("delete from SegundoFactor f where f.empresa.id = :empresaId and f.usuario.id = :usuarioId")
    int borrarElDe(Long empresaId, Long usuarioId);
}
