package com.facimus.procesos.modelado.service;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.modelado.dto.response.ComentarioResponse;
import com.facimus.procesos.modelado.dto.response.HiloResponse;
import com.facimus.procesos.modelado.model.EstadoHilo;

/**
 * PR 63: los comentarios de un proceso, en hilos. Comentar, responder, resolver y reabrir piden {@code COMENTAR}, que
 * tiene tambien quien solo lee; leerlos pide {@code VER}. Mencionar es solo a quien puede ver el proceso.
 */
public interface ComentarioService {

    /** Los filtros de los hilos: uno vacio no filtra. */
    record Filtro(String bpmnId, Integer version, EstadoHilo estado) {
    }

    /** Un hilo nuevo, con su ancla si la tiene. */
    record Nuevo(String markdown, String bpmnId, Integer version, List<Long> menciones) {
    }

    /** Los hilos de un proceso, del mas nuevo al mas viejo, cada uno con sus respuestas. */
    PageResponse<HiloResponse> listar(Long empresaId, Long procesoId, Filtro filtro, Pageable pagina);

    ComentarioResponse comentar(Long empresaId, Long usuarioId, Long procesoId, Nuevo comentario);

    /** Responder a una respuesta es responder en su hilo. */
    ComentarioResponse responder(Long empresaId, Long usuarioId, Long comentarioId, String markdown,
            List<Long> menciones);

    /** Solo quien lo escribio; las menciones nuevas reciben su aviso. */
    ComentarioResponse editar(Long empresaId, Long usuarioId, Long comentarioId, String markdown, List<Long> menciones,
            Long version);

    ComentarioResponse resolver(Long empresaId, Long usuarioId, Long comentarioId);

    ComentarioResponse reabrir(Long empresaId, Long usuarioId, Long comentarioId);

    /** Quien lo escribio, o un administrador. */
    void eliminar(Long empresaId, Long usuarioId, RolAcceso rol, Long comentarioId);
}
