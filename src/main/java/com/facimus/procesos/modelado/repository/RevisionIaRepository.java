package com.facimus.procesos.modelado.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.RevisionIa;

public interface RevisionIaRepository extends RepositorioTenant<RevisionIa> {

    /** La revision mas reciente de un proceso de la tienda. */
    Optional<RevisionIa> findFirstByProcesoIdAndEmpresaIdOrderByFechaDescIdDesc(Long procesoId, Long empresaId);

    /** Cuantas respuestas del modelo pidio la tienda desde ese momento: es lo que cuenta para su limite. */
    long countByEmpresaIdAndFechaAfter(Long empresaId, LocalDateTime desde);

    /** La mas vieja de la ventana: cuando salga de ella, la tienda vuelve a tener una revision. */
    Optional<RevisionIa> findFirstByEmpresaIdAndFechaAfterOrderByFechaAscIdAsc(Long empresaId, LocalDateTime desde);

    /**
     * D20: una revision que ya no es la ultima de su proceso y que salio de la ventana del limite no sirve para nada.
     * La purga es la unica que cruza tiendas, y solo borra lo que nadie va a volver a leer.
     */
    @Transactional
    @Modifying
    @Query("""
            delete from RevisionIa r
            where r.fecha < :limite
            and exists (select 1 from RevisionIa nueva where nueva.procesoId = r.procesoId and nueva.fecha > r.fecha)
            """)
    int borrarSuperadasAntesDe(LocalDateTime limite);
}
