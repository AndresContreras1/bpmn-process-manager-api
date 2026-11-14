package com.facimus.procesos.modelado.service;

import org.springframework.data.domain.Pageable;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.modelado.dto.response.SolicitudResponse;
import com.facimus.procesos.modelado.model.Decision;
import com.facimus.procesos.modelado.model.EstadoSolicitud;

/**
 * PR 64: publicar con aprobacion (D32). Pedir la revision congela el borrador y la deja a quienes la politica de la
 * tienda nombra; cada uno aprueba, pide cambios o rechaza, y la ultima aprobacion que hacia falta publica lo congelado
 * por el mismo camino que publicar directo (D2). Pedirla pide {@code PUBLICAR}; decidir, {@code APROBAR} y ser uno de
 * sus aprobadores; leerla, {@code VER}.
 */
public interface SolicitudService {

    /** Los filtros de las solicitudes de la tienda: uno vacio no filtra. */
    record Filtro(boolean pendientesDeMi, EstadoSolicitud estado) {
    }

    /**
     * Congela el borrador en una revision y en la instantanea que se publicara, y avisa a sus aprobadores. Con la
     * tienda publicando directo, con otra abierta, sin aprobadores suficientes, con errores de diagnostico o sin
     * cambios desde la vigente, 409.
     */
    SolicitudResponse pedir(Long empresaId, Long usuarioId, Long procesoId, String markdown);

    /** Las de un proceso, de la mas nueva a la mas vieja. */
    PageResponse<SolicitudResponse> listarDelProceso(Long empresaId, Long procesoId, EstadoSolicitud estado,
            Pageable pagina);

    /** Las de la tienda que esa persona puede ver; con {@code pendientesDeMi}, solo las que esperan su decision. */
    PageResponse<SolicitudResponse> listar(Long empresaId, Long usuarioId, RolAcceso rol, Filtro filtro,
            Pageable pagina);

    /** Una, con lo que cambia contra la version vigente del dia en que se pidio. */
    SolicitudResponse obtener(Long empresaId, Long usuarioId, Long solicitudId);

    /** Aprobar, pedir cambios o rechazar: una decision por aprobador. Las dos ultimas la cierran. */
    SolicitudResponse decidir(Long empresaId, Long usuarioId, Long solicitudId, Decision decision, String markdown);

    /** Quien la pidio la retira, o quien puede administrar el proceso, mientras siga abierta. */
    SolicitudResponse retirar(Long empresaId, Long usuarioId, Long solicitudId);
}
