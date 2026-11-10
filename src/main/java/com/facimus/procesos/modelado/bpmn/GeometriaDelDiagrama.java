package com.facimus.procesos.modelado.bpmn;

import java.util.List;
import java.util.stream.Stream;

import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.model.DisposicionBpmn;
import com.facimus.procesos.modelado.model.DisposicionBpmn.ArcoPlano;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Geometria;
import com.facimus.procesos.modelado.model.DisposicionBpmn.LanePlana;
import com.facimus.procesos.modelado.model.DisposicionBpmn.MensajePlano;
import com.facimus.procesos.modelado.model.DisposicionBpmn.NodoPlano;
import com.facimus.procesos.modelado.model.DisposicionBpmn.PoolPlano;

/**
 * PR 53: la geometria de un diagrama que no esta en la base, como la instantanea de una version publicada antes de que
 * las versiones guardaran su XML. Es la misma cuenta que el borrador guarda en {@code formas_bpmn} y
 * {@code aristas_bpmn}, con los nodos en el mismo orden: actividades, gateways y eventos.
 */
public final class GeometriaDelDiagrama {

    private GeometriaDelDiagrama() {
    }

    public static Geometria de(DiagramaResponse diagrama) {
        List<NodoPlano> nodos = Stream.of(
                        diagrama.actividades().stream().map(actividad -> new NodoPlano(actividad.id(),
                                IdsBpmn.de(actividad.bpmnId(), "Activity", actividad.id()), actividad.laneId(),
                                "TAREA", actividad.posicionX(), actividad.posicionY(), actividad.nombre())),
                        diagrama.gateways().stream().map(gateway -> new NodoPlano(gateway.id(),
                                IdsBpmn.de(gateway.bpmnId(), "Gateway", gateway.id()), gateway.laneId(), "COMPUERTA",
                                gateway.posicionX(), gateway.posicionY(), gateway.nombre())),
                        diagrama.eventos().stream().map(evento -> new NodoPlano(evento.id(),
                                IdsBpmn.de(evento.bpmnId(), "Event", evento.id()), evento.laneId(), "EVENTO",
                                evento.posicionX(), evento.posicionY(), evento.nombre())))
                .flatMap(nodo -> nodo)
                .toList();
        return DisposicionBpmn.calcular(
                diagrama.pools().stream().map(pool -> new PoolPlano(pool.id(),
                        IdsBpmn.de(pool.bpmnId(), "Participant", pool.id()), pool.tipoParticipante().name(),
                        pool.orden(), pool.cajaNegra())).toList(),
                diagrama.lanes().stream().map(lane -> new LanePlana(lane.id(),
                        IdsBpmn.de(lane.bpmnId(), "Lane", lane.id()), lane.poolId(), lane.orden())).toList(),
                nodos,
                diagrama.arcos().stream().map(arco -> new ArcoPlano(IdsBpmn.de(arco.bpmnId(), "Flow", arco.id()),
                        arco.origenId(), arco.destinoId(), arco.etiqueta())).toList(),
                diagrama.mensajes().stream().map(mensaje -> new MensajePlano(mensaje.id(),
                        IdsBpmn.de(mensaje.bpmnId(), "MessageFlow", mensaje.id()), mensaje.poolOrigenId(),
                        mensaje.poolDestinoId(), mensaje.nodoOrigenId(), mensaje.nodoDestinoId())).toList());
    }
}
