package com.facimus.procesos.gestion.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.CodigoDeRecuperacion;

public interface CodigoDeRecuperacionRepository extends RepositorioTenant<CodigoDeRecuperacion> {

    Optional<CodigoDeRecuperacion> findByUsuarioIdAndEmpresaIdAndCodigoHashAndUsadoEnIsNull(Long usuarioId,
            Long empresaId, String codigoHash);

    long countByUsuarioIdAndEmpresaIdAndUsadoEnIsNull(Long usuarioId, Long empresaId);

    boolean existsByUsuarioIdAndEmpresaId(Long usuarioId, Long empresaId);

    /** Lo marca usado solo si nadie lo uso antes: de dos usos a la vez, el segundo cambia 0 filas. */
    @Modifying
    @Query("update CodigoDeRecuperacion c set c.usadoEn = :ahora where c.id = :id and c.usadoEn is null")
    int marcarUsado(Long id, LocalDateTime ahora);

    @Modifying
    @Query("delete from CodigoDeRecuperacion c where c.empresa.id = :empresaId and c.usuario.id = :usuarioId")
    int borrarLosDe(Long empresaId, Long usuarioId);
}
