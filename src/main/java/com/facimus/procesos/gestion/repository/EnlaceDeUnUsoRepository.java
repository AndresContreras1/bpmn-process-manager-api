package com.facimus.procesos.gestion.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.EnlaceDeUnUso;
import com.facimus.procesos.gestion.model.PropositoDeEnlace;

public interface EnlaceDeUnUsoRepository extends RepositorioTenant<EnlaceDeUnUso> {

    /**
     * Quien sigue un enlace todavia no tiene sesion: el token es la credencial y dice de que tienda es. El usuario y la
     * tienda llegan en la misma consulta.
     */
    @EntityGraph(attributePaths = {"usuario", "empresa"})
    Optional<EnlaceDeUnUso> findByTokenHash(String tokenHash);

    /** Lo marca usado solo si nadie lo uso antes: de dos usos a la vez, el segundo cambia 0 filas. */
    @Modifying
    @Query("update EnlaceDeUnUso e set e.usadoEn = :ahora where e.id = :id and e.usadoEn is null")
    int marcarUsado(Long id, LocalDateTime ahora);

    /** Usar un enlace deja sin efecto los demas que el usuario tenia para lo mismo, por ejemplo dos recuperaciones. */
    @Modifying
    @Query("update EnlaceDeUnUso e set e.usadoEn = :ahora "
            + "where e.usuario.id = :usuarioId and e.proposito = :proposito and e.usadoEn is null")
    int anularVigentes(Long usuarioId, PropositoDeEnlace proposito, LocalDateTime ahora);

    /** D20: un enlace vencido ya no abre nada. La limpieza cruza tiendas a proposito. */
    @Transactional
    @Modifying
    @Query("delete from EnlaceDeUnUso e where e.venceEn < :limite")
    int borrarVencidosAntesDe(LocalDateTime limite);
}
