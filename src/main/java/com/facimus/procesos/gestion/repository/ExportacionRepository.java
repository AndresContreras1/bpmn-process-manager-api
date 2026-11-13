package com.facimus.procesos.gestion.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.EstadoExportacion;
import com.facimus.procesos.gestion.model.Exportacion;

/** PR 66: las exportaciones completas de cada tienda. */
public interface ExportacionRepository extends RepositorioTenant<Exportacion> {

    List<Exportacion> findAllByEmpresaIdOrderByIdDesc(Long empresaId);

    boolean existsByEmpresaIdAndEstado(Long empresaId, EstadoExportacion estado);

    /** La limpieza, que cruza tiendas: las listas cuyo enlace vencio. */
    List<Exportacion> findAllByEstadoAndVenceEnBeforeOrderByIdAsc(EstadoExportacion estado, LocalDateTime limite);

    /** La limpieza, que cruza tiendas: las pendientes pedidas antes de ese momento, que la cola no termino. */
    List<Exportacion> findAllByEstadoAndPedidaEnBeforeOrderByIdAsc(EstadoExportacion estado, LocalDateTime limite);
}
