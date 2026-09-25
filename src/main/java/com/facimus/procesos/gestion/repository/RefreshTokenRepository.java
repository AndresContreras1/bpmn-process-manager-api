package com.facimus.procesos.gestion.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.RefreshToken;

public interface RefreshTokenRepository extends RepositorioTenant<RefreshToken> {

    /**
     * Como el login, la renovacion todavia no conoce la empresa: el token es la credencial y dice de quien es. La
     * sesion y su usuario llegan en la misma consulta.
     */
    @EntityGraph(attributePaths = {"sesion", "sesion.usuario"})
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Marca el token como usado solo si nadie lo uso antes: de dos renovaciones a la vez, la segunda cambia 0 filas.
     */
    @Modifying
    @Query("update RefreshToken t set t.fechaUso = :ahora where t.id = :id and t.fechaUso is null")
    int marcarUsado(Long id, LocalDateTime ahora);

    /** D20: un refresh token vencido ya no renueva nada. La limpieza cruza tiendas a proposito. */
    @Transactional
    @Modifying
    @Query("delete from RefreshToken t where t.fechaExpiracion < :limite")
    int borrarVencidosAntesDe(LocalDateTime limite);
}
