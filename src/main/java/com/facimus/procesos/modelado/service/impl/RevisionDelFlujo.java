package com.facimus.procesos.modelado.service.impl;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

import com.facimus.procesos.common.condiciones.EvaluadorDeCondiciones;
import com.facimus.procesos.modelado.dto.response.ArcoResponse;
import com.facimus.procesos.modelado.dto.response.LaneResponse;
import com.facimus.procesos.modelado.dto.response.PoolResponse;
import com.facimus.procesos.modelado.model.CodigoDeDiagnostico;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;

/**
 * La mitad del diagnostico que mira el flujo: por donde empieza el proceso, por donde termina, a que se llega y
 * quien decide. Lo que solo tiene sentido dentro del pool de la tienda se revisa ahi, porque es el unico que se
 * ejecuta; lo que es un error se mire donde se mire, como un evento de inicio con flujos entrando, se revisa en
 * todo el diagrama.
 */
final class RevisionDelFlujo {

    private RevisionDelFlujo() {
    }

    static void revisar(MapaDelDiagrama mapa, Hallazgos hallazgos) {
        lanesVacias(mapa, hallazgos);
        eventosMalConectados(mapa, hallazgos);
        gateways(mapa, hallazgos);
        condiciones(mapa, hallazgos);
        mapa.poolDeLaEmpresa().ifPresent(propio -> revisarElPoolDeLaTienda(mapa, propio, hallazgos));
    }

    private static void revisarElPoolDeLaTienda(MapaDelDiagrama mapa, PoolResponse propio, Hallazgos hallazgos) {
        List<NodoDelDiagrama> nodos = mapa.nodosDe(propio.id());
        if (!mapa.seModelaPorDentro(propio.id())) {
            hallazgos.anotar(CodigoDeDiagnostico.E01, "Pool \"" + propio.nombre() + "\"", propio.id(),
                    "El pool de la tienda no tiene ninguna lane, asi que no hay donde poner el trabajo.",
                    "Crea al menos una lane y asignale el rol de proceso que hace ese trabajo.");
            return;
        }
        List<NodoDelDiagrama> inicios = nodos.stream().filter(NodoDelDiagrama::empiezaElProceso).toList();
        if (inicios.isEmpty()) {
            hallazgos.anotar(CodigoDeDiagnostico.E02, "Pool \"" + propio.nombre() + "\"", propio.id(),
                    "El pool de la tienda no tiene ningun evento de inicio: el proceso no tiene por donde empezar.",
                    "Agrega un evento de inicio, o uno de inicio por mensaje si el proceso arranca cuando llega uno.");
            return;
        }
        variosIniciosAMano(propio, inicios, hallazgos);
        caminos(mapa, propio, nodos, inicios, hallazgos);
    }

    /** E-15: un caso se abre por un solo inicio a mano; los demas tienen que arrancar con un mensaje. */
    private static void variosIniciosAMano(PoolResponse propio, List<NodoDelDiagrama> inicios, Hallazgos hallazgos) {
        long aMano = inicios.stream().filter(nodo -> nodo.tipoEvento() == TipoEvento.INICIO).count();
        if (aMano > 1) {
            hallazgos.anotar(CodigoDeDiagnostico.E15, "Pool \"" + propio.nombre() + "\"", propio.id(),
                    "El pool de la tienda tiene " + aMano + " eventos de inicio sin mensaje, y un caso se abre por "
                            + "uno solo.",
                    "Deja un unico evento de inicio; los demas pueden esperar un mensaje para arrancar.");
        }
    }

    /** E-03, E-04 y E-05: a donde llega el flujo desde el inicio y donde se queda sin camino. */
    private static void caminos(MapaDelDiagrama mapa, PoolResponse propio, List<NodoDelDiagrama> nodos,
            List<NodoDelDiagrama> inicios, Hallazgos hallazgos) {
        Set<Long> alcanzados = alcanzablesDesde(mapa, inicios);
        if (nodos.stream().noneMatch(nodo -> nodo.terminaElProceso() && alcanzados.contains(nodo.id()))) {
            hallazgos.anotar(CodigoDeDiagnostico.E03, "Pool \"" + propio.nombre() + "\"", propio.id(),
                    "Ningun evento de fin se alcanza desde el inicio: el proceso nunca termina.",
                    "Lleva el flujo hasta un evento de fin.");
        }
        for (NodoDelDiagrama nodo : nodos) {
            if (!alcanzados.contains(nodo.id())) {
                hallazgos.anotar(CodigoDeDiagnostico.E04, nodo.etiqueta(), nodo.id(),
                        "No se llega a este nodo desde ningun evento de inicio.",
                        "Conectalo con el flujo del proceso, o quitalo del diagrama.");
            }
            if (mapa.salidasDe(nodo.id()).isEmpty() && !nodo.terminaElProceso()) {
                hallazgos.anotar(CodigoDeDiagnostico.E05, nodo.etiqueta(), nodo.id(),
                        "No sale ningun flujo de este nodo y no es un evento de fin: el caso se quedaria aqui.",
                        "Conectalo con el paso siguiente, o termina el camino con un evento de fin.");
            }
        }
    }

    private static Set<Long> alcanzablesDesde(MapaDelDiagrama mapa, List<NodoDelDiagrama> inicios) {
        Set<Long> vistos = new HashSet<>();
        Deque<Long> porVisitar = new ArrayDeque<>();
        inicios.forEach(inicio -> {
            vistos.add(inicio.id());
            porVisitar.add(inicio.id());
        });
        while (!porVisitar.isEmpty()) {
            for (ArcoResponse salida : mapa.salidasDe(porVisitar.poll())) {
                if (vistos.add(salida.destinoId())) {
                    porVisitar.add(salida.destinoId());
                }
            }
        }
        return vistos;
    }

    /** A-10: una lane sin nodos no dice nada del proceso, y su rol no tiene trabajo. */
    private static void lanesVacias(MapaDelDiagrama mapa, Hallazgos hallazgos) {
        for (LaneResponse lane : mapa.diagrama().lanes()) {
            if (mapa.nodosDeLane(lane.id()).isEmpty()) {
                hallazgos.anotar(CodigoDeDiagnostico.A10, "Lane \"" + lane.nombre() + "\"", lane.id(),
                        "Esta lane no tiene ningun nodo.",
                        "Ponle el trabajo que hace su rol, o quitala del pool.");
            }
        }
    }

    /** E-09: la regla de los extremos, por si una fila vieja o una carga directa la dejo pasar. */
    private static void eventosMalConectados(MapaDelDiagrama mapa, Hallazgos hallazgos) {
        for (NodoDelDiagrama nodo : mapa.nodos()) {
            if (nodo.empiezaElProceso() && !mapa.entradasDe(nodo.id()).isEmpty()) {
                hallazgos.anotar(CodigoDeDiagnostico.E09, nodo.etiqueta(), nodo.id(),
                        "A un evento de inicio no puede llegarle ningun flujo, y a este le llegan "
                                + mapa.entradasDe(nodo.id()).size() + ".",
                        "Quita esos flujos, o cambia el tipo del evento por uno intermedio.");
            }
            if (nodo.terminaElProceso() && !mapa.salidasDe(nodo.id()).isEmpty()) {
                hallazgos.anotar(CodigoDeDiagnostico.E09, nodo.etiqueta(), nodo.id(),
                        "De un evento de fin no puede salir ningun flujo, y de este salen "
                                + mapa.salidasDe(nodo.id()).size() + ".",
                        "Quita esos flujos, o cambia el tipo del evento por uno intermedio.");
            }
        }
    }

    /** E-06, E-07, E-14, A-05 y A-14: si el gateway decide algo, y si lo decide del todo. */
    private static void gateways(MapaDelDiagrama mapa, Hallazgos hallazgos) {
        for (NodoDelDiagrama gateway : mapa.nodos()) {
            if (!gateway.esGateway()) {
                continue;
            }
            List<ArcoResponse> salidas = mapa.salidasDe(gateway.id());
            int entradas = mapa.entradasDe(gateway.id()).size();
            if (salidas.size() >= 2 && entradas >= 2) {
                hallazgos.anotar(CodigoDeDiagnostico.E14, gateway.etiqueta(), gateway.id(),
                        "Este gateway une varios caminos y los vuelve a repartir a la vez.",
                        "Separalo en dos gateways seguidos: uno que une y otro que reparte.");
            } else if (entradas <= 1 && salidas.size() < 2) {
                hallazgos.anotar(CodigoDeDiagnostico.E06, gateway.etiqueta(), gateway.id(),
                        "Este gateway no une caminos y tampoco los reparte: tiene " + salidas.size() + " salidas.",
                        "Dale al menos dos salidas, o quitalo y conecta los nodos directamente.");
            }
            if (gateway.eligePorCondicion()) {
                salidasSinCondicion(mapa, salidas, hallazgos);
                salidaPorDefecto(gateway, salidas, hallazgos);
                condicionesRepetidas(gateway, salidas, hallazgos);
            }
        }
    }

    private static void salidasSinCondicion(MapaDelDiagrama mapa, List<ArcoResponse> salidas,
            Hallazgos hallazgos) {
        for (ArcoResponse salida : salidas) {
            if (!salida.porDefecto() && !StringUtils.hasText(salida.condicion())) {
                hallazgos.anotar(CodigoDeDiagnostico.E07, mapa.nombreDelFlujo(salida), salida.id(),
                        "Esta salida del gateway no lleva condicion y tampoco es la salida por defecto.",
                        "Escribe su condicion, o marcala como la salida por defecto del gateway.");
            }
        }
    }

    /** A-05 y A-14: sin salida por defecto, un caso en el que ninguna condicion se cumple se queda sin camino. */
    private static void salidaPorDefecto(NodoDelDiagrama gateway, List<ArcoResponse> salidas, Hallazgos hallazgos) {
        if (salidas.size() < 2 || salidas.stream().anyMatch(ArcoResponse::porDefecto)) {
            return;
        }
        boolean exclusivo = gateway.tipoGateway() == TipoGateway.EXCLUSIVO;
        hallazgos.anotar(exclusivo ? CodigoDeDiagnostico.A05 : CodigoDeDiagnostico.A14, gateway.etiqueta(),
                gateway.id(),
                "El gateway no tiene salida por defecto: si ninguna condicion se cumple, el caso se queda sin camino.",
                "Marca como salida por defecto la que deba tomarse cuando no se cumpla ninguna condicion.");
    }

    private static void condicionesRepetidas(NodoDelDiagrama gateway, List<ArcoResponse> salidas,
            Hallazgos hallazgos) {
        Map<String, List<ArcoResponse>> porCondicion = salidas.stream()
                .filter(salida -> StringUtils.hasText(salida.condicion()))
                .collect(Collectors.groupingBy(salida -> salida.condicion().trim(), Collectors.toList()));
        List<String> repetidas = porCondicion.keySet().stream()
                .filter(condicion -> porCondicion.get(condicion).size() > 1)
                .sorted()
                .toList();
        for (String condicion : repetidas) {
            hallazgos.anotar(CodigoDeDiagnostico.A05, gateway.etiqueta(), gateway.id(),
                    "Dos salidas del gateway tienen la misma condicion (" + condicion + "), asi que la segunda "
                            + "nunca se toma.",
                    "Cambia una de las dos condiciones, o une las dos ramas en una sola salida.");
        }
    }

    /** E-08: la condicion se lee con la gramatica del motor, que es la que la evaluara cuando el caso corra. */
    private static void condiciones(MapaDelDiagrama mapa, Hallazgos hallazgos) {
        for (ArcoResponse arco : mapa.diagrama().arcos()) {
            if (!StringUtils.hasText(arco.condicion())) {
                continue;
            }
            Optional<String> problema = EvaluadorDeCondiciones.problema(arco.condicion());
            problema.ifPresent(detalle -> hallazgos.anotar(CodigoDeDiagnostico.E08, mapa.nombreDelFlujo(arco),
                    arco.id(), "La condicion no se entiende: " + detalle,
                    "Escribela como una comparacion, por ejemplo payment.status == APPROVED."));
        }
    }

}
