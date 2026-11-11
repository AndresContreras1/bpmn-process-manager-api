package com.facimus.procesos.modelado.service.impl;

import java.util.List;

import org.springframework.stereotype.Component;

import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Evento;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.AsociacionRepository;
import com.facimus.procesos.modelado.repository.EventoRepository;

import lombok.RequiredArgsConstructor;

/**
 * PR 55: lo que va pegado a otro elemento y no tiene sentido sin el. Una asociacion nombra sus extremos por su
 * bpmn_id, asi que se va cuando se va cualquiera de los dos; un evento de borde va pegado a su actividad y se borra
 * con ella.
 */
@Component
@RequiredArgsConstructor
public class ElementosPegados {

    private final AsociacionRepository asociacionRepository;
    private final EventoRepository eventoRepository;
    private final ArcoRepository arcoRepository;

    /** Borra las asociaciones que salen del elemento o llegan a el. */
    public void soltarAsociaciones(Long empresaId, Long procesoId, String bpmnId) {
        if (bpmnId != null) {
            asociacionRepository.deleteAll(asociacionRepository.queTocan(empresaId, procesoId, bpmnId));
        }
    }

    public List<Evento> bordesDe(Long empresaId, Long actividadId) {
        return eventoRepository.findAllByAdjuntoA_IdAndEmpresaIdOrderByIdAsc(actividadId, empresaId);
    }

    /** Lo que se va con una actividad: sus eventos de borde, con sus flujos, y las asociaciones de todos. */
    public void borrarConLaActividad(Long empresaId, Long procesoId, Actividad actividad) {
        for (Evento borde : bordesDe(empresaId, actividad.getId())) {
            arcoRepository.deleteAll(arcoRepository.findAllByOrigenIdAndEmpresaId(borde.getId(), empresaId));
            soltarAsociaciones(empresaId, procesoId, borde.getBpmnId());
            eventoRepository.delete(borde);
        }
        soltarAsociaciones(empresaId, procesoId, actividad.getBpmnId());
    }
}
