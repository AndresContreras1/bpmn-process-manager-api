package com.facimus.procesos.ejecucion.service.impl;

import java.util.Arrays;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EstadoDelegacion;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.modelado.dto.response.EscalamientoResponse;
import com.facimus.procesos.modelado.model.AccionDeEscalamiento;

import lombok.RequiredArgsConstructor;

/**
 * PR 71: lo que hace un temporizador de borde que no interrumpe cuando salta sobre su actividad, ademas de abrir su
 * camino. Lo llama el motor con el caso bloqueado (D3), en la transaccion del salto.
 *
 * <p>El supervisor es la persona que dice el evento, o el dueno del proceso si no dice ninguna. Reasignar le pasa la
 * tarea, como lo haria un administrador: la delegacion que hubiera se anula, y queda en la linea de tiempo y en la
 * bitacora, con el sistema como autor. Si no se le puede pasar (no es una tarea de usuario, o el supervisor no puede
 * completar tareas) se le avisa, y la linea de tiempo dice por que.
 */
@Component
@RequiredArgsConstructor
class Escalamientos {

    static final String ESCALADA = "TAREA_ESCALADA";

    private final ActividadCasoRepository actividadCasoRepository;
    private final PersonasDeLaTarea personas;
    private final Tareas tareas;
    private final Bitacora bitacora;
    private final Auditoria auditoria;
    private final AvisosDePlazo avisos;

    /** Escala la actividad si la version le dio al borde algo que hacer; si no, el borde solo abre su camino. */
    void alSaltar(Caso caso, GrafoDeVersion grafo, ActividadCaso actividad, NodoDeLaVersion borde,
            Momento momento) {
        Optional<EscalamientoResponse> escalamiento = grafo.escalamientoDe(borde.id());
        if (escalamiento.isEmpty()) {
            return;
        }
        String sobre = "\"" + borde.nombre() + "\" escala \"" + actividad.getNodoNombre() + "\"";
        Long supervisor = escalamiento.get().usuarioId() != null ? escalamiento.get().usuarioId()
                : caso.getProceso().getDuenoId();
        if (supervisor == null) {
            bitacora.anotar(caso, momento.tick(), TipoEventoCaso.TAREA_ESCALADA, sobre + ", pero no tiene a quien: "
                    + "el evento no dice un supervisor y el proceso no tiene dueno.");
            return;
        }
        Long laTenia = actividad.getAsignadoA();
        if (escalamiento.get().accion() == AccionDeEscalamiento.REASIGNAR) {
            Optional<String> porQueNo = porQueNoSeLaPasa(caso, actividad, supervisor);
            if (porQueNo.isEmpty()) {
                reasignar(caso, actividad, supervisor, sobre, momento);
                avisos.deUnEscalamiento(caso, Arrays.asList(laTenia, supervisor));
                return;
            }
            bitacora.anotar(caso, momento.tick(), TipoEventoCaso.TAREA_ESCALADA, sobre + ": no se le pasa al usuario "
                    + supervisor + " porque " + porQueNo.get() + "; se le avisa.");
        } else {
            bitacora.anotar(caso, momento.tick(), TipoEventoCaso.TAREA_ESCALADA, sobre + ": se le avisa al usuario "
                    + supervisor + ", que la supervisa.");
        }
        avisos.deUnEscalamiento(caso, Arrays.asList(laTenia, supervisor));
    }

    private Optional<String> porQueNoSeLaPasa(Caso caso, ActividadCaso actividad, Long supervisor) {
        if (!actividad.esTarea()) {
            return Optional.of("no es una tarea de usuario");
        }
        if (supervisor.equals(actividad.getAsignadoA())) {
            return Optional.of("ya la tiene");
        }
        if (personas.siPuedeTenerla(caso.getEmpresa().getId(), supervisor).isEmpty()) {
            return Optional.of("no puede tener tareas: esta desactivado o solo lee");
        }
        return Optional.empty();
    }

    private void reasignar(Caso caso, ActividadCaso tarea, Long supervisor, String sobre, Momento momento) {
        Long laTenia = tarea.getAsignadoA();
        tareas.cerrarDelegacion(tarea, EstadoDelegacion.ANULADA);
        tarea.setAsignadoA(supervisor);
        tarea.setDelegadaPor(null);
        actividadCasoRepository.save(tarea);
        String desde = laTenia == null ? "de la bandeja de su rol" : "del usuario " + laTenia;
        bitacora.anotar(caso, momento.tick(), TipoEventoCaso.TAREA_ESCALADA, sobre + ": pasa " + desde
                + " al usuario " + supervisor + ", que la supervisa.");
        auditoria.registrar(caso.getEmpresa().getId(), null, ESCALADA, TareaServiceImpl.RECURSO, tarea.getId(),
                AsignacionDeTareaServiceImpl.detalle(tarea, laTenia, supervisor));
    }
}
