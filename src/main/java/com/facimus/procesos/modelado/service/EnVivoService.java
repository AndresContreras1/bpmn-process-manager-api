package com.facimus.procesos.modelado.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.facimus.procesos.modelado.dto.response.BloqueoResponse;

/** PR 58: quien esta en un proceso y quien lo edita. */
public interface EnVivoService {

    /** Abre una conexion en vivo. Se autoriza aqui, al suscribirse. */
    SseEmitter conectar(Long empresaId, Long usuarioId, Long procesoId);

    /** Lo mismo sobre un emisor dado. */
    void conectar(Long empresaId, Long usuarioId, Long procesoId, SseEmitter emisor);

    BloqueoResponse estado(Long empresaId, Long usuarioId, Long procesoId);

    /** Lo toma si esta libre o vencido, o renueva si ya es suyo; si es de otro, solo dice de quien. */
    BloqueoResponse adquirir(Long empresaId, Long usuarioId, Long procesoId);

    /** Lo mantiene. Si otra persona tomo el control, 409. */
    BloqueoResponse latir(Long empresaId, Long usuarioId, Long procesoId);

    /** Lo suelta, si es suyo. */
    BloqueoResponse soltar(Long empresaId, Long usuarioId, Long procesoId);

    /** Se lo quita a quien lo tenga: queda en la bitacora y se le avisa. */
    BloqueoResponse tomarControl(Long empresaId, Long usuarioId, Long procesoId);
}
