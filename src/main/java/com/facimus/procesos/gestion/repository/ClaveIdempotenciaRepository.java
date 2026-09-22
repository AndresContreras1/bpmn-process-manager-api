package com.facimus.procesos.gestion.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.ClaveIdempotencia;

public interface ClaveIdempotenciaRepository extends RepositorioTenant<ClaveIdempotencia> {

    Optional<ClaveIdempotencia> findByUsuarioIdAndClaveAndEmpresaId(Long usuarioId, String clave, Long empresaId);

    /**
     * Retoma una reserva que quedo en curso porque su peticion nunca termino. Solo la retoma quien la encuentre
     * igual que la leyo: de dos reintentos a la vez, el segundo cambia 0 filas.
     */
    @Transactional
    @Modifying
    @Query("""
            update ClaveIdempotencia c set c.fechaCreacion = :ahora
            where c.id = :id and c.estado is null and c.fechaCreacion = :anterior
            """)
    int retomar(Long id, LocalDateTime anterior, LocalDateTime ahora);
}
