package com.facimus.procesos.gestion.service;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.notificaciones.EnvioPorCorreo;
import com.facimus.procesos.common.notificaciones.NotificacionPedida;
import com.facimus.procesos.common.notificaciones.TipoDeNotificacion;
import com.facimus.procesos.gestion.dto.response.ConteoDeNotificacionesResponse;
import com.facimus.procesos.gestion.dto.response.NotificacionResponse;
import com.facimus.procesos.gestion.dto.response.PreferenciaDeNotificacionResponse;

/**
 * PR 63: el centro de notificaciones de cada persona. Una notificacion queda siempre en la tabla y sale en seguida
 * por el flujo en vivo de quien la recibe; por correo, segun lo que eligio para su tipo. Solo la ve su destinatario.
 */
public interface NotificacionService {

    /** Una notificacion por persona de las pedidas, menos quien lo hizo y quien ya no esta activo. */
    void notificar(NotificacionPedida pedido);

    PageResponse<NotificacionResponse> listar(Long empresaId, Long usuarioId, boolean soloNoLeidas, Pageable pagina);

    ConteoDeNotificacionesResponse contar(Long empresaId, Long usuarioId);

    NotificacionResponse marcarLeida(Long empresaId, Long usuarioId, Long notificacionId);

    ConteoDeNotificacionesResponse marcarTodasLeidas(Long empresaId, Long usuarioId);

    /** El flujo de eventos de la persona: {@code notificacion} con cada una nueva, y {@code conteo} cuando cambia. */
    SseEmitter conectar(Long empresaId, Long usuarioId);

    /** Lo mismo con un emisor que trae quien llama, como una prueba. */
    void conectar(Long empresaId, Long usuarioId, SseEmitter emisor);

    /** Cada tipo, con el envio por correo que tiene la persona. */
    List<PreferenciaDeNotificacionResponse> preferencias(Long empresaId, Long usuarioId);

    List<PreferenciaDeNotificacionResponse> guardarPreferencias(Long empresaId, Long usuarioId,
            Map<TipoDeNotificacion, EnvioPorCorreo> preferencias);

    /** El reloj del resumen diario: un correo en la cola por persona con notificaciones que lo esperan. */
    int encolarResumenes();

    /** La purga: lo leido que ya paso su retencion. */
    int olvidarLeidas();

    /** PR 35: lo que se borra al anonimizar a una persona: sus notificaciones y sus preferencias. */
    void borrarDe(Long empresaId, Long usuarioId);
}
