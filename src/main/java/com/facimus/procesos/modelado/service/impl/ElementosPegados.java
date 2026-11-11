package com.facimus.procesos.modelado.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Evento;
import com.facimus.procesos.modelado.model.NodoFlujo;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.AsociacionRepository;
import com.facimus.procesos.modelado.repository.EventoRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;

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
    private final NodoFlujoRepository nodoFlujoRepository;

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
        for (NodoFlujo hijo : nodoFlujoRepository.findAllByPadre_IdAndEmpresaIdOrderByIdAsc(actividad.getId(),
                empresaId)) {
            borrarDelContenido(empresaId, procesoId, (NodoFlujo) Hibernate.unproxy(hijo));
        }
        soltarAsociaciones(empresaId, procesoId, actividad.getBpmnId());
    }

    /** PR 56: un nodo de un subproceso que se borra, con sus flujos y lo que lleve dentro. */
    private void borrarDelContenido(Long empresaId, Long procesoId, NodoFlujo nodo) {
        if (nodo instanceof Actividad actividad) {
            borrarConLaActividad(empresaId, procesoId, actividad);
        } else {
            soltarAsociaciones(empresaId, procesoId, nodo.getBpmnId());
        }
        arcoRepository.deleteAll(arcoRepository.findAllByOrigenIdAndEmpresaId(nodo.getId(), empresaId));
        arcoRepository.deleteAll(arcoRepository.findAllByDestinoIdAndEmpresaId(nodo.getId(), empresaId));
        nodoFlujoRepository.delete(nodo);
    }

    /** PR 56: lo que va con una actividad al mudarse: sus eventos de borde y su contenido, a cualquier profundidad. */
    public List<NodoFlujo> pegadosA(Long empresaId, Long actividadId) {
        List<NodoFlujo> pegados = new ArrayList<>(bordesDe(empresaId, actividadId));
        for (NodoFlujo hijo : nodoFlujoRepository.findAllByPadre_IdAndEmpresaIdOrderByIdAsc(actividadId, empresaId)) {
            pegados.add(hijo);
            pegados.addAll(pegadosA(empresaId, hijo.getId()));
        }
        return pegados;
    }
}
