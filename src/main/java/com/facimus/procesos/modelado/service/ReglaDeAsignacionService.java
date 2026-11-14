package com.facimus.procesos.modelado.service;

import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.model.TipoAsignacion;

/**
 * PR 70: a quien le llega una tarea de usuario del borrador. Se guarda con la actividad y se congela con la version al
 * publicar: un caso asigna sus tareas con la regla de la version que corre, no con la del borrador de hoy.
 */
public interface ReglaDeAsignacionService {

    /**
     * Reemplaza la regla de la actividad. ROL la devuelve a la bandeja de su lane, en cualquier actividad; las demas
     * solo van en una de usuario, y cada una con lo suyo: PERSONA con alguien que pueda completar tareas, CAMPO con la
     * ruta de una variable del caso.
     */
    ActividadResponse deLaActividad(Long empresaId, Long usuarioId, Long actividadId, TipoAsignacion tipo,
            Long asignadoId, String campo, Long version);
}
