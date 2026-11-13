package com.facimus.procesos.modelado.service;

import com.facimus.procesos.common.formularios.Formulario;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.EventoResponse;

/**
 * PR 69: el formulario tipado de un nodo del borrador. Solo lo lleva lo que llena una persona: una actividad de
 * usuario o un evento de inicio que se abre a mano. Se guarda entero, revisado contra su esquema, y se congela con la
 * version al publicar.
 */
public interface FormularioService {

    /** Reemplaza el formulario de la actividad; uno sin campos lo quita. */
    ActividadResponse deLaActividad(Long empresaId, Long usuarioId, Long actividadId, Formulario formulario,
            Long version);

    /** Reemplaza el formulario del evento de inicio; uno sin campos lo quita. */
    EventoResponse delEvento(Long empresaId, Long usuarioId, Long eventoId, Formulario formulario, Long version);
}
