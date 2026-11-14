package com.facimus.procesos.gestion.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.EstadoExportacion;
import com.facimus.procesos.gestion.model.Exportacion;

import jakarta.persistence.LockModeType;

/** PR 66: las exportaciones completas de cada tienda. */
public interface ExportacionRepository extends RepositorioTenant<Exportacion> {

    Page<Exportacion> findAllByEmpresaIdOrderByIdDesc(Long empresaId, Pageable pagina);

    /**
     * La exportacion bloqueada hasta el fin de la transaccion: quien la deja lista, la da por fallida o la vence lo
     * hace de a uno, y ninguno pisa lo que otro anoto.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Exportacion e where e.id = :id and e.empresa.id = :empresaId")
    Optional<Exportacion> bloquear(Long id, Long empresaId);

    boolean existsByEmpresaIdAndEstado(Long empresaId, EstadoExportacion estado);

    /** La limpieza, que cruza tiendas: las listas cuyo enlace vencio. */
    List<Exportacion> findAllByEstadoAndVenceEnBeforeOrderByIdAsc(EstadoExportacion estado, LocalDateTime limite);

    /** La limpieza, que cruza tiendas: las pendientes pedidas antes de ese momento, que la cola no termino. */
    List<Exportacion> findAllByEstadoAndPedidaEnBeforeOrderByIdAsc(EstadoExportacion estado, LocalDateTime limite);
}
