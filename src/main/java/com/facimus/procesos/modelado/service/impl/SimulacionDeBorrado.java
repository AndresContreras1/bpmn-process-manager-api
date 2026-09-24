package com.facimus.procesos.modelado.service.impl;

import java.util.HashSet;
import java.util.Set;

import com.facimus.procesos.modelado.dto.response.ArcoResponse;
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
                        .toList());
    }

    /** A-06: lo que el borrado se llevaria por delante ademas del elemento por el que se pregunto. */
    static void avisarDeLoQueSeVa(MapaDelDiagrama antes, MapaDelDiagrama despues, ElementoDelDiagrama elemento,
            Hallazgos hallazgos) {
        Set<Long> quedan = new HashSet<>();
        despues.diagrama().lanes().forEach(lane -> quedan.add(lane.id()));
        despues.nodos().forEach(nodo -> quedan.add(nodo.id()));
        despues.diagrama().arcos().forEach(arco -> quedan.add(arco.id()));
        despues.diagrama().mensajes().forEach(mensaje -> quedan.add(mensaje.id()));

        antes.diagrama().lanes().stream()
                .filter(lane -> seVa(lane.id(), quedan, elemento))
                .forEach(lane -> anotar(hallazgos, "Lane \"" + lane.nombre() + "\"", lane.id(), "la lane"));
        antes.nodos().stream()
                .filter(nodo -> seVa(nodo.id(), quedan, elemento))
                .forEach(nodo -> anotar(hallazgos, nodo.etiqueta(), nodo.id(), "el nodo"));
        antes.diagrama().arcos().stream()
                .filter(arco -> seVa(arco.id(), quedan, elemento))
                .forEach(arco -> anotar(hallazgos, antes.nombreDelFlujo(arco), arco.id(), "el flujo"));
        antes.diagrama().mensajes().stream()
                .filter(mensaje -> seVa(mensaje.id(), quedan, elemento))
                .forEach(mensaje -> anotar(hallazgos, "Mensaje \"" + mensaje.nombre() + "\"", mensaje.id(),
                        "el mensaje"));
    }

    private static void si(ElementoDelDiagrama elemento, ElementoDelDiagrama.Tipo tipo, Set<Long> fuera) {
        if (elemento.tipo() == tipo) {
            fuera.add(elemento.id());
        }
    }

    private static boolean seVa(Long id, Set<Long> quedan, ElementoDelDiagrama elemento) {
        return !quedan.contains(id) && !id.equals(elemento.id());
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
        return new MensajeResponse(mensaje.id(), mensaje.nombre(), mensaje.contenido(), mensaje.poolOrigenId(),
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
