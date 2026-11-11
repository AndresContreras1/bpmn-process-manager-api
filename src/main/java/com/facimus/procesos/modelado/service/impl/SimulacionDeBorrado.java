package com.facimus.procesos.modelado.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.facimus.procesos.modelado.dto.response.ArcoResponse;
import com.facimus.procesos.modelado.dto.response.ArtefactoResponse;
import com.facimus.procesos.modelado.dto.response.AsociacionResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.LaneResponse;
import com.facimus.procesos.modelado.dto.response.MensajeResponse;
import com.facimus.procesos.modelado.model.CodigoDeDiagnostico;

/**
 * HU-10.3 y HU-13.3: que pasaria si se borrara este elemento. Se arma el diagrama que quedaria, con el mismo
 * arrastre que hace el borrado real (un pool se lleva sus lanes, una lane sus nodos, un nodo sus arcos), y se
 * diagnostica ese. Asi la advertencia no se inventa nada: es el mismo catalogo aplicado al diagrama de despues.
 */
final class SimulacionDeBorrado {

    private SimulacionDeBorrado() {
    }

    /** Lo que quedaria del diagrama sin el elemento y sin lo que su borrado se lleva por delante. */
    static DiagramaResponse sinElElemento(DiagramaResponse diagrama, ElementoDelDiagrama elemento) {
        Set<Long> pools = elemento.tipo() == ElementoDelDiagrama.Tipo.POOL ? Set.of(elemento.id()) : Set.of();

        Set<Long> lanes = new HashSet<>();
        si(elemento, ElementoDelDiagrama.Tipo.LANE, lanes);
        diagrama.lanes().stream().filter(lane -> pools.contains(lane.poolId())).map(LaneResponse::id)
                .forEach(lanes::add);

        Set<Long> nodos = new HashSet<>();
        if (elemento.esNodo()) {
            nodos.add(elemento.id());
        }
        MapaDelDiagrama.nodosDe(diagrama).stream()
                .filter(nodo -> lanes.contains(nodo.laneId()))
                .forEach(nodo -> nodos.add(nodo.id()));
        // PR 55 y 56: un evento de borde se va con su actividad, y el contenido con su subproceso, a cualquier
        // profundidad: se repite mientras se siga yendo algo
        List<NodoDelDiagrama> todos = MapaDelDiagrama.nodosDe(diagrama);
        boolean sigue = true;
        while (sigue) {
            sigue = false;
            for (NodoDelDiagrama nodo : todos) {
                boolean seVaConOtro = nodo.adjuntoAId() != null && nodos.contains(nodo.adjuntoAId())
                        || nodo.padreId() != null && nodos.contains(nodo.padreId());
                if (seVaConOtro && nodos.add(nodo.id())) {
                    sigue = true;
                }
            }
        }

        Set<Long> arcos = new HashSet<>();
        si(elemento, ElementoDelDiagrama.Tipo.ARCO, arcos);
        diagrama.arcos().stream()
                .filter(arco -> nodos.contains(arco.origenId()) || nodos.contains(arco.destinoId())
                        || pools.contains(arco.poolId()))
                .map(ArcoResponse::id)
                .forEach(arcos::add);

        Set<Long> mensajes = new HashSet<>();
        si(elemento, ElementoDelDiagrama.Tipo.MENSAJE, mensajes);
        diagrama.mensajes().stream()
                .filter(mensaje -> pools.contains(mensaje.poolOrigenId()) || pools.contains(mensaje.poolDestinoId()))
                .map(MensajeResponse::id)
                .forEach(mensajes::add);

        // PR 55: los artefactos de un pool se van con el, y las asociaciones con cualquiera de sus extremos
        Set<String> seVan = new HashSet<>();
        MapaDelDiagrama.nodosDe(diagrama).stream().filter(nodo -> nodos.contains(nodo.id()))
                .forEach(nodo -> seVan.add(nodo.bpmnId()));
        diagrama.arcos().stream().filter(arco -> arcos.contains(arco.id())).forEach(arco -> seVan.add(arco.bpmnId()));
        List<ArtefactoResponse> artefactos = diagrama.artefactos().stream()
                .filter(artefacto -> artefacto.poolId() == null || !pools.contains(artefacto.poolId()))
                .toList();
        diagrama.artefactos().stream().filter(artefacto -> !artefactos.contains(artefacto))
                .forEach(artefacto -> seVan.add(artefacto.bpmnId()));
        List<AsociacionResponse> asociaciones = diagrama.asociaciones().stream()
                .filter(asociacion -> !seVan.contains(asociacion.origen()) && !seVan.contains(asociacion.destino()))
                .toList();

        return new DiagramaResponse(diagrama.proceso(), diagrama.compartido(),
                diagrama.pools().stream().filter(pool -> !pools.contains(pool.id())).toList(),
                diagrama.lanes().stream().filter(lane -> !lanes.contains(lane.id())).toList(),
                diagrama.actividades().stream().filter(actividad -> !nodos.contains(actividad.id())).toList(),
                diagrama.gateways().stream().filter(gateway -> !nodos.contains(gateway.id())).toList(),
                diagrama.eventos().stream().filter(evento -> !nodos.contains(evento.id())).toList(),
                diagrama.arcos().stream().filter(arco -> !arcos.contains(arco.id())).toList(),
                diagrama.mensajes().stream()
                        .filter(mensaje -> !mensajes.contains(mensaje.id()))
                        .map(mensaje -> sinLoQueYaNoExiste(mensaje, nodos, mensajes))
                        .toList(),
                diagrama.correlaciones().stream()
                        .filter(correlacion -> !mensajes.contains(correlacion.mensajeId()))
                        .toList(),
                artefactos, asociaciones);
    }

    /** A-06: lo que el borrado se llevaria por delante ademas del elemento por el que se pregunto. */
    static void avisarDeLoQueSeVa(MapaDelDiagrama antes, MapaDelDiagrama despues, ElementoDelDiagrama elemento,
            Hallazgos hallazgos) {
        // Cada clase de elemento numera sus ids en su tabla: un mismo numero puede ser una lane y un flujo
        Set<Long> lanes = new HashSet<>();
        Set<Long> nodos = new HashSet<>();
        Set<Long> arcos = new HashSet<>();
        Set<Long> mensajes = new HashSet<>();
        despues.diagrama().lanes().forEach(lane -> lanes.add(lane.id()));
        despues.nodos().forEach(nodo -> nodos.add(nodo.id()));
        despues.diagrama().arcos().forEach(arco -> arcos.add(arco.id()));
        despues.diagrama().mensajes().forEach(mensaje -> mensajes.add(mensaje.id()));

        antes.diagrama().lanes().stream()
                .filter(lane -> !lanes.contains(lane.id())
                        && !(elemento.tipo() == ElementoDelDiagrama.Tipo.LANE && lane.id().equals(elemento.id())))
                .forEach(lane -> anotar(hallazgos, "Lane \"" + lane.nombre() + "\"", lane.id(), "la lane"));
        antes.nodos().stream()
                .filter(nodo -> !nodos.contains(nodo.id()) && !(elemento.esNodo() && nodo.id().equals(elemento.id())))
                .forEach(nodo -> anotar(hallazgos, nodo.etiqueta(), nodo.id(), "el nodo"));
        antes.diagrama().arcos().stream()
                .filter(arco -> !arcos.contains(arco.id())
                        && !(elemento.tipo() == ElementoDelDiagrama.Tipo.ARCO && arco.id().equals(elemento.id())))
                .forEach(arco -> anotar(hallazgos, antes.nombreDelFlujo(arco), arco.id(), "el flujo"));
        antes.diagrama().mensajes().stream()
                .filter(mensaje -> !mensajes.contains(mensaje.id())
                        && !(elemento.tipo() == ElementoDelDiagrama.Tipo.MENSAJE && mensaje.id().equals(elemento.id())))
                .forEach(mensaje -> anotar(hallazgos, "Mensaje \"" + mensaje.nombre() + "\"", mensaje.id(),
                        "el mensaje"));
    }

    private static void si(ElementoDelDiagrama elemento, ElementoDelDiagrama.Tipo tipo, Set<Long> fuera) {
        if (elemento.tipo() == tipo) {
            fuera.add(elemento.id());
        }
    }

    private static void anotar(Hallazgos hallazgos, String etiqueta, Long id, String queEs) {
        hallazgos.anotar(CodigoDeDiagnostico.A06, etiqueta, id,
                "Si se borra el elemento por el que se pregunta, tambien se va " + queEs + ".",
                "Revisa si hay que rehacer esta parte del diagrama antes de borrar.");
    }

    /** Un mensaje sobrevive al borrado de un nodo, pero se queda sin el anclaje que apuntaba a el. */
    private static MensajeResponse sinLoQueYaNoExiste(MensajeResponse mensaje, Set<Long> nodos, Set<Long> mensajes) {
        boolean intacto = !nodos.contains(mensaje.nodoOrigenId()) && !nodos.contains(mensaje.nodoDestinoId())
                && !nodos.contains(mensaje.nodoManejoErrorId()) && !mensajes.contains(mensaje.respuestaEsperadaId());
        if (intacto) {
            return mensaje;
        }
        return new MensajeResponse(mensaje.id(), mensaje.bpmnId(), mensaje.nombre(), mensaje.contenido(),
                mensaje.poolOrigenId(),
                mensaje.poolDestinoId(), fuera(mensaje.nodoOrigenId(), nodos), fuera(mensaje.nodoDestinoId(), nodos),
                mensaje.tipoDestino(), mensaje.siFalla(), fuera(mensaje.nodoManejoErrorId(), nodos),
                mensaje.origenExterno(), mensaje.campos(), mensaje.usoDeLosDatos(), mensaje.variable(),
                fuera(mensaje.respuestaEsperadaId(), mensajes), mensaje.procesoId(), mensaje.version(),
                mensaje.creadoPor(), mensaje.fechaCreacion(), mensaje.modificadoPor(), mensaje.fechaModificacion());
    }

    private static Long fuera(Long id, Set<Long> borrados) {
        return borrados.contains(id) ? null : id;
    }
}
