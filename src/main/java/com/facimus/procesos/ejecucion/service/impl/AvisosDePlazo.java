package com.facimus.procesos.ejecucion.service.impl;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.facimus.procesos.common.notificaciones.NotificacionPedida;
import com.facimus.procesos.common.notificaciones.TipoDeNotificacion;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.ModoCaso;
import com.facimus.procesos.gestion.service.MembresiaRolService;

import lombok.RequiredArgsConstructor;

/**
 * PR 71: a quien se le avisa de un plazo o de un escalamiento, por el centro de notificaciones del PR 63. El aviso
 * sale en la transaccion de lo que lo provoco y espera en el outbox: si eso se deshace, nadie se entera. Lleva el caso
 * como referencia, sin nombres ni textos; el sistema es el autor, asi que a nadie se le quita por haberlo hecho.
 *
 * <p>Un caso de prueba (una simulacion, del borrador o de lo publicado) no le avisa a nadie: sus plazos son de ticks,
 * no le pasan a una persona de verdad, y un correo sobre ellos llegaria a la bandeja de quien no esta probando nada.
 * La linea de tiempo si los cuenta, que es donde los mira quien prueba.
 */
@Component
@RequiredArgsConstructor
class AvisosDePlazo {

    private final ApplicationEventPublisher eventos;
    private final MembresiaRolService membresiaRolService;

    /**
     * De la tarea: a quien la tiene, o, si espera en la bandeja de su rol, a quienes pertenecen a el; y al dueno del
     * proceso.
     */
    void deLaTarea(Caso caso, ActividadCaso tarea, TipoDeNotificacion tipo) {
        Set<Long> personas = new LinkedHashSet<>();
        if (tarea.getAsignadoA() != null) {
            personas.add(tarea.getAsignadoA());
        } else if (tarea.getRolProcesoId() != null) {
            personas.addAll(membresiaRolService.idsDeLosMiembrosDe(caso.getEmpresa().getId(),
                    tarea.getRolProcesoId()));
        }
        personas.add(caso.getProceso().getDuenoId());
        avisar(caso, tipo, personas);
    }

    /** Del caso entero: a quien lo abrio y al dueno del proceso. */
    void delCaso(Caso caso, TipoDeNotificacion tipo) {
        avisar(caso, tipo, Arrays.asList(caso.getIniciadoPor(), caso.getProceso().getDuenoId()));
    }

    /** De un escalamiento: a quien tenia la tarea y a quien la supervisa. */
    void deUnEscalamiento(Caso caso, Collection<Long> personas) {
        avisar(caso, TipoDeNotificacion.TAREA_ESCALADA, personas);
    }

    private void avisar(Caso caso, TipoDeNotificacion tipo, Collection<Long> personas) {
        if (caso.getModo() == ModoCaso.PRUEBA) {
            return;
        }
        List<Long> destinatarios = personas.stream().filter(Objects::nonNull).distinct().toList();
        if (!destinatarios.isEmpty()) {
            eventos.publishEvent(new NotificacionPedida(caso.getEmpresa().getId(), tipo, null,
                    caso.getProceso().getId(), caso.getId(), destinatarios));
        }
    }
}
