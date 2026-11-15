package com.facimus.procesos.gestion.service.impl;

import java.time.LocalDate;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.notificaciones.NotificacionPedida;
import com.facimus.procesos.common.notificaciones.TipoDeNotificacion;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.security.ContextoDeTienda.Alcance;
import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.RecertificacionService;

import lombok.RequiredArgsConstructor;

/**
 * PR 64: el reloj diario no avisa a nadie: encola un trabajo por tienda, y cada trabajo corre en su tienda. Lo avisado
 * se anota en el proceso por fecha de revision, en la misma transaccion que el pedido de la notificacion: un trabajo
 * que se repite no vuelve a avisar, y mover la fecha deja a la nueva con sus dos avisos.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecertificacionServiceImpl implements RecertificacionService {

    /** El tipo de trabajo de la cola. */
    public static final String TRABAJO = "recertificacion.avisos";

    private final ProcesoRepository procesoRepository;
    private final ColaDeTrabajos cola;
    private final ApplicationEventPublisher eventos;

    @Override
    @Transactional
    public int encolarAvisos(LocalDate hoy) {
        List<Long> tiendas = procesoRepository.tiendasConRevisionesPorAvisar(hoy);
        tiendas.forEach(empresaId -> cola.encolar(empresaId, TRABAJO, hoy.toString()));
        return tiendas.size();
    }

    @Override
    @Transactional
    public int avisar(Long empresaId, LocalDate hoy) {
        // La cola corre como sistema: lo que se lee y se escribe aqui es de esta tienda y de ninguna otra
        Alcance anterior = ContextoDeTienda.fijar(Alcance.deLaTienda(empresaId));
        try {
            int avisos = 0;
            for (Proceso proceso : procesoRepository.revisionesPorAvisar(empresaId, hoy)) {
                avisos += avisar(empresaId, proceso, hoy);
            }
            return avisos;
        } finally {
            ContextoDeTienda.restaurar(anterior);
        }
    }

    /**
     * Vencida es pasada la fecha. Si el primer aviso llega con la fecha ya pasada, por ejemplo porque se puso una del
     * pasado, va solo el de vencida: dos avisos el mismo dia sobre lo mismo serian ruido.
     */
    private int avisar(Long empresaId, Proceso proceso, LocalDate hoy) {
        LocalDate fecha = proceso.getProximaRevision();
        boolean vencida = fecha.isBefore(hoy);
        if (vencida && !fecha.equals(proceso.getVencidaAvisada())) {
            pedir(empresaId, TipoDeNotificacion.RECERTIFICACION_VENCIDA, proceso);
            procesoRepository.anotarAvisos(proceso.getId(), empresaId, fecha, fecha);
            return 1;
        }
        if (!vencida && !fecha.equals(proceso.getRevisionAvisada())) {
            pedir(empresaId, TipoDeNotificacion.RECERTIFICACION_PENDIENTE, proceso);
            procesoRepository.anotarAvisos(proceso.getId(), empresaId, fecha, proceso.getVencidaAvisada());
            return 1;
        }
        return 0;
    }

    private void pedir(Long empresaId, TipoDeNotificacion tipo, Proceso proceso) {
        eventos.publishEvent(new NotificacionPedida(empresaId, tipo, null, proceso.getId(), null,
                List.of(proceso.getDuenoId())));
    }
}
