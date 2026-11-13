package com.facimus.procesos.modelado.service;

import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.model.AccionDeEscalamiento;
import com.facimus.procesos.modelado.model.UnidadDePlazo;

/**
 * PR 71: el plazo y el escalamiento de los nodos del borrador. Se guardan con el nodo y se congelan con la version al
 * publicar: un caso cuenta sus plazos con los de la version que corre, no con los del borrador de hoy.
 */
public interface PlazoDelNodoService {

    /** Reemplaza el plazo de una tarea de usuario; sin cantidad ni unidad, se lo quita. */
    ActividadResponse deLaActividad(Long empresaId, Long usuarioId, Long actividadId, Integer cantidad,
            UnidadDePlazo unidad, Long version);

    /** Reemplaza el plazo del caso entero en un evento de inicio; sin cantidad ni unidad, se lo quita. */
    EventoResponse delEvento(Long empresaId, Long usuarioId, Long eventoId, Integer cantidad, UnidadDePlazo unidad,
            Long version);

    /**
     * Reemplaza lo que hace un temporizador de borde que no interrumpe; sin accion, solo abre su camino. El supervisor
     * es una persona que puede completar tareas, o el dueno del proceso si no se dice ninguna.
     */
    EventoResponse escalamiento(Long empresaId, Long usuarioId, Long eventoId, AccionDeEscalamiento accion,
            Long supervisorId, Long version);
}
