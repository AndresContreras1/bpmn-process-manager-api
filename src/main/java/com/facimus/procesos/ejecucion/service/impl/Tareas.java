package com.facimus.procesos.ejecucion.service.impl;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.Delegacion;
import com.facimus.procesos.ejecucion.model.EstadoDelegacion;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.repository.CasoRepository;
import com.facimus.procesos.ejecucion.repository.DelegacionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Como se llega a una tarea de la tienda: lo que comparten completarla (TareaServiceImpl) y hacer algo con ella
 * mientras espera (PR 70, AsignacionDeTareaServiceImpl). Lo que no es de la tienda, o no es una tarea, es un 404.
 */
@Component
@RequiredArgsConstructor
class Tareas {

    static final String NO_ENCONTRADA = "Tarea no encontrada.";

    private final ActividadCasoRepository actividadCasoRepository;
    private final CasoRepository casoRepository;
    private final DelegacionRepository delegacionRepository;

    /** Solo una actividad de usuario es una tarea; los demas pasos del caso los mueve el motor, no una persona. */
    ActividadCaso exigir(Long empresaId, Long tareaId) {
        ActividadCaso tarea = actividadCasoRepository.findByIdAndEmpresaId(tareaId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADA));
        if (!tarea.esTarea()) {
            throw new RecursoNoEncontradoException(NO_ENCONTRADA);
        }
        return tarea;
    }

    /**
     * El caso de la tarea, bloqueado (D3). Se bloquea antes de leer la tarea, no despues: si se leyera primero, el
     * segundo hilo se quedaria con el estado que vio antes de esperar.
     */
    Caso bloquearSuCaso(Long empresaId, Long tareaId) {
        Long casoId = actividadCasoRepository.casoDe(tareaId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADA));
        return casoRepository.bloquear(casoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADA));
    }

    /** PR 70: cierra la delegacion abierta de la tarea, si la tiene, como termino. */
    void cerrarDelegacion(ActividadCaso tarea, EstadoDelegacion como) {
        if (tarea.getDelegadaPor() == null) {
            return;
        }
        Optional<Delegacion> abierta = delegacionRepository.findFirstByTareaIdAndEmpresaIdAndEstado(tarea.getId(),
                tarea.getEmpresa().getId(), EstadoDelegacion.ACTIVA);
        if (abierta.isPresent()) {
            Delegacion delegacion = abierta.get();
            delegacion.cerrar(como, LocalDateTime.now());
            delegacionRepository.save(delegacion);
        }
    }
}
