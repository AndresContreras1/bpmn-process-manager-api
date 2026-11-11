package com.facimus.procesos.modelado.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * PR 52: la geometria BPMN DI de un diagrama, con la misma cuenta que dibuja la web ({@code lienzo.ts}): los pools en
 * filas (clientes arriba, la organizacion con sus lanes en medio, los demas abajo), cada lane tan alta como sus
 * nodos, la x de cada nodo comun al diagrama entero y su y relativa a su lane. Lo que sale son coordenadas absolutas,
 * con el origen arriba a la izquierda: formas con sus limites y los de su etiqueta, y aristas con sus puntos.
 *
 * <p>Es una funcion pura, sin base ni Spring: la usan la migracion que convierte los diagramas que ya existian y el
 * servicio que la recalcula con cada cambio de un proceso.
 */
public final class DisposicionBpmn {

    static final double MARGEN = 16;
    static final double FRANJA = 30;
    public static final double ANCHO_TAREA = 128;
    public static final double ALTO_TAREA = 56;
    static final double LADO_GATEWAY = 50;
    static final double RADIO_EVENTO = 18;
    static final double RELLENO_X = 48;
    static final double RELLENO_ARRIBA = 24;
    static final double RELLENO_ABAJO = 40;
    static final double ALTO_CAJA_NEGRA = 56;
    static final double ALTO_VACIO = 96;
    static final double SEPARACION = 48;
    static final double ANCHO_MINIMO = 720;
    static final double ALTO_LINEA = 14;
    /** Lo que ocupa una letra de las etiquetas, en promedio: alcanza para un rectangulo de etiqueta. */
    static final double ANCHO_LETRA = 7;

    /** La fila de cada tipo de participante: arriba los clientes, en medio la organizacion y abajo los demas. */
    private static final Map<String, Integer> FILA = Map.of("CLIENTE", 0, "EMPRESA", 1, "PROVEEDOR", 2,
            "SISTEMA_EXTERNO", 2);

    private DisposicionBpmn() {
    }

    /** Un pool como lo necesita la cuenta. */
    public record PoolPlano(long id, String bpmnId, String tipoParticipante, int orden, boolean cajaNegra) {
    }

    public record LanePlana(long id, String bpmnId, long poolId, int orden) {
    }

    /** Lo que separa un evento de borde del siguiente sobre el borde de su actividad. */
    static final double PASO_BORDE = 36;
    /** Por debajo de esto, dos coordenadas son la misma. */
    private static final double CASI_CERO = 1e-9;

    /**
     * Una tarea, compuerta o evento: su clase (TAREA, COMPUERTA o EVENTO), su posicion y su nombre. PR 55: un evento
     * de borde dice a que nodo va pegado, y se dibuja sobre el borde de abajo de ese nodo, no donde diga su posicion.
     */
    public record NodoPlano(long id, String bpmnId, long laneId, String clase, int posicionX, int posicionY,
            String nombre, Long adjuntoA) {

        /** El de antes del PR 55, de un nodo que no va pegado a nada. */
        public NodoPlano(long id, String bpmnId, long laneId, String clase, int posicionX, int posicionY,
                String nombre) {
            this(id, bpmnId, laneId, clase, posicionX, posicionY, nombre, null);
        }

        boolean deBorde() {
            return adjuntoA != null;
        }
    }

    /**
     * PR 55: un artefacto. En una lane, su posicion es relativa a ella como la de un nodo; suelto, se dibuja debajo de
     * los pools, conservando como estan unos respecto de otros.
     */
    public record ArtefactoPlano(String bpmnId, String tipo, Long laneId, int posicionX, int posicionY, int ancho,
            int alto, String texto) {
    }

    /** PR 55: una asociacion, o una de datos, entre dos elementos que se nombran por su bpmn_id. */
    public record AsociacionPlana(String bpmnId, String tipo, String origen, String destino) {
    }

    public record ArcoPlano(String bpmnId, long origenId, long destinoId, String etiqueta) {
    }

    public record MensajePlano(long id, String bpmnId, long poolOrigenId, long poolDestinoId, Long nodoOrigenId,
            Long nodoDestinoId) {
    }

    public record Punto(double x, double y) {
    }

    public record Limites(double x, double y, double ancho, double alto) {
    }

    /** Un BPMNShape: el bpmn_id del elemento, su tipo, sus limites y los de su etiqueta, si tiene. */
    public record Forma(String elemento, String tipo, Limites limites, Limites etiqueta) {
    }

    /** Un BPMNEdge: el bpmn_id del flujo, si es de secuencia o de mensaje, sus puntos y su etiqueta, si tiene. */
    public record Arista(String elemento, String tipo, List<Punto> puntos, Limites etiqueta) {
    }

    public record Geometria(List<Forma> formas, List<Arista> aristas) {
    }

    /** Centro y medio tamano de un nodo ya ubicado, para trazar lo que entra y sale de el. */
    private record Ubicado(double cx, double cy, double medioAncho, double medioAlto, boolean esGateway) {
    }

    private record Caja(double x, double y, double ancho, double alto) {
    }

    /** Lo que se va armando mientras se ubican los pools: comun a todos, para no pasarlo pieza por pieza. */
    private record Lienzo(double minX, double anchoPool, List<Forma> formas, Map<Long, Ubicado> ubicados,
            List<ArtefactoPlano> artefactos) {
    }

    public static Geometria calcular(List<PoolPlano> pools, List<LanePlana> lanes, List<NodoPlano> nodos,
            List<ArcoPlano> arcos, List<MensajePlano> mensajes) {
        return calcular(pools, lanes, nodos, arcos, mensajes, List.of(), List.of());
    }

    /** PR 55: con los eventos de borde, los artefactos y lo que los une. */
    public static Geometria calcular(List<PoolPlano> pools, List<LanePlana> lanes, List<NodoPlano> nodos,
            List<ArcoPlano> arcos, List<MensajePlano> mensajes, List<ArtefactoPlano> artefactos,
            List<AsociacionPlana> asociaciones) {
        Set<Long> idsDeLanes = new HashSet<>();
        lanes.forEach(lane -> idsDeLanes.add(lane.id()));
        List<NodoPlano> enElFlujo = nodos.stream().filter(nodo -> !nodo.deBorde()).toList();
        List<ArtefactoPlano> enLanes = artefactos.stream()
                .filter(artefacto -> artefacto.laneId() != null && idsDeLanes.contains(artefacto.laneId())).toList();
        double minX = IntStream.concat(enElFlujo.stream().mapToInt(NodoPlano::posicionX),
                enLanes.stream().mapToInt(ArtefactoPlano::posicionX)).min().orElse(0);
        double derecha = Math.max(
                enElFlujo.stream().mapToDouble(nodo -> nodo.posicionX() + ANCHO_TAREA).max().orElse(ANCHO_TAREA),
                enLanes.stream().mapToDouble(artefacto -> (double) artefacto.posicionX() + artefacto.ancho()).max()
                        .orElse(ANCHO_TAREA));
        double anchoPool = Math.max(2 * FRANJA + derecha - minX + 2 * RELLENO_X, ANCHO_MINIMO);
        Lienzo lienzo = new Lienzo(minX, anchoPool, new ArrayList<>(), new HashMap<>(), enLanes);
        Map<Long, Caja> cajas = new LinkedHashMap<>();

        List<PoolPlano> ordenados = pools.stream()
                .sorted(Comparator.comparingInt((PoolPlano pool) -> FILA.getOrDefault(pool.tipoParticipante(), 2))
                        .thenComparingInt(PoolPlano::orden).thenComparingLong(PoolPlano::id))
                .toList();
        double y = MARGEN;
        for (PoolPlano pool : ordenados) {
            double alto = ubicarPool(pool, y, lanes, enElFlujo, lienzo);
            Caja caja = new Caja(MARGEN, y, anchoPool, alto);
            cajas.put(pool.id(), caja);
            lienzo.formas().add(new Forma(pool.bpmnId(), "PARTICIPANTE",
                    new Limites(caja.x(), caja.y(), caja.ancho(), caja.alto()), null));
            y += alto + SEPARACION;
        }
        ubicarBordes(nodos, lienzo);
        ubicarSueltos(artefactos.stream().filter(artefacto -> !enLanes.contains(artefacto)).toList(), y, lienzo);

        List<Arista> aristas = new ArrayList<>();
        for (ArcoPlano arco : arcos) {
            Ubicado origen = lienzo.ubicados().get(arco.origenId());
            Ubicado destino = lienzo.ubicados().get(arco.destinoId());
            if (origen != null && destino != null) {
                aristas.add(trazarArco(arco, origen, destino));
            }
        }
        aristas.addAll(trazarMensajes(mensajes, cajas, lienzo.ubicados(), anchoPool));
        aristas.addAll(trazarAsociaciones(asociaciones, lienzo.formas(), aristas));
        return new Geometria(List.copyOf(lienzo.formas()), List.copyOf(aristas));
    }

    /**
     * PR 55: los eventos de borde, sobre el borde de abajo de su actividad y de izquierda a derecha, en el orden en
     * que se crearon. Un evento pegado a algo que no esta dibujado no se dibuja.
     */
    private static void ubicarBordes(List<NodoPlano> nodos, Lienzo lienzo) {
        Map<Long, Integer> yaPegados = new HashMap<>();
        nodos.stream().filter(NodoPlano::deBorde).sorted(Comparator.comparingLong(NodoPlano::id)).forEach(borde -> {
            Ubicado actividad = lienzo.ubicados().get(borde.adjuntoA());
            if (actividad == null) {
                return;
            }
            int orden = yaPegados.merge(borde.adjuntoA(), 1, Integer::sum) - 1;
            double cx = actividad.cx() - actividad.medioAncho() + RADIO_EVENTO + 4 + orden * PASO_BORDE;
            Ubicado ubicado = new Ubicado(cx, actividad.cy() + actividad.medioAlto(), RADIO_EVENTO, RADIO_EVENTO,
                    false);
            lienzo.ubicados().put(borde.id(), ubicado);
            lienzo.formas().add(forma(borde, ubicado));
        });
    }

    /** PR 55: los artefactos sueltos, debajo del ultimo pool, como estan unos respecto de otros. */
    private static void ubicarSueltos(List<ArtefactoPlano> sueltos, double y, Lienzo lienzo) {
        double minX = sueltos.stream().mapToInt(ArtefactoPlano::posicionX).min().orElse(0);
        double minY = sueltos.stream().mapToInt(ArtefactoPlano::posicionY).min().orElse(0);
        for (ArtefactoPlano artefacto : sueltos) {
            lienzo.formas().add(formaDelArtefacto(artefacto, MARGEN + artefacto.posicionX() - minX,
                    y + artefacto.posicionY() - minY));
        }
    }

    /** El artefacto en su sitio, con su etiqueta: debajo de un dato, arriba a la izquierda de un grupo. */
    private static Forma formaDelArtefacto(ArtefactoPlano artefacto, double x, double y) {
        Limites limites = new Limites(x, y, artefacto.ancho(), artefacto.alto());
        Limites etiqueta = switch (artefacto.tipo()) {
            case "OBJETO_DATOS", "ALMACEN_DATOS" -> etiquetaDebajo(artefacto.texto(), 20, 2,
                    x + artefacto.ancho() / 2.0, y + artefacto.alto() + 14, true);
            case "GRUPO" -> etiquetaDebajo(artefacto.texto(), 40, 1, x + 8, y + 18, false);
            default -> null;
        };
        return new Forma(artefacto.bpmnId(), "ARTEFACTO", limites, etiqueta);
    }

    /**
     * PR 55: cada asociacion en linea recta, del borde de un extremo al borde del otro. Un extremo que es un flujo se
     * toma por la mitad de su recorrido; una asociacion con un extremo sin dibujar no se dibuja.
     */
    private static List<Arista> trazarAsociaciones(List<AsociacionPlana> asociaciones, List<Forma> formas,
            List<Arista> flujos) {
        Map<String, Limites> cajas = new HashMap<>();
        formas.forEach(forma -> cajas.put(forma.elemento(), forma.limites()));
        for (Arista flujo : flujos) {
            Punto medio = flujo.puntos().get(flujo.puntos().size() / 2);
            if (flujo.puntos().size() % 2 == 0) {
                Punto antes = flujo.puntos().get(flujo.puntos().size() / 2 - 1);
                medio = new Punto((antes.x() + medio.x()) / 2, (antes.y() + medio.y()) / 2);
            }
            cajas.putIfAbsent(flujo.elemento(), new Limites(medio.x(), medio.y(), 0, 0));
        }
        List<Arista> aristas = new ArrayList<>();
        for (AsociacionPlana asociacion : asociaciones) {
            Limites origen = cajas.get(asociacion.origen());
            Limites destino = cajas.get(asociacion.destino());
            if (origen == null || destino == null) {
                continue;
            }
            Punto centroOrigen = centro(origen);
            Punto centroDestino = centro(destino);
            aristas.add(new Arista(asociacion.bpmnId(), "ASOCIACION".equals(asociacion.tipo()) ? "ASOCIACION"
                    : "DATOS", List.of(borde(origen, centroDestino), borde(destino, centroOrigen)), null));
        }
        return aristas;
    }

    private static Punto centro(Limites caja) {
        return new Punto(caja.x() + caja.ancho() / 2, caja.y() + caja.alto() / 2);
    }

    /** Donde la recta del centro de la caja hacia {@code hacia} cruza su borde. */
    private static Punto borde(Limites caja, Punto hacia) {
        Punto centro = centro(caja);
        double dx = hacia.x() - centro.x();
        double dy = hacia.y() - centro.y();
        double tx = Math.abs(dx) < CASI_CERO ? Double.MAX_VALUE : caja.ancho() / 2 / Math.abs(dx);
        double ty = Math.abs(dy) < CASI_CERO ? Double.MAX_VALUE : caja.alto() / 2 / Math.abs(dy);
        double t = Math.min(Math.min(tx, ty), 1);
        return new Punto(redondeado(centro.x() + dx * t), redondeado(centro.y() + dy * t));
    }

    private static double redondeado(double valor) {
        return Math.round(valor * 10) / 10.0;
    }

    /**
     * Las lanes de un pool, una debajo de otra, con sus nodos; devuelve el alto del pool. Una caja negra no muestra
     * lanes, y un pool sin lanes queda con el alto de uno vacio.
     */
    private static double ubicarPool(PoolPlano pool, double y, List<LanePlana> lanes, List<NodoPlano> nodos,
            Lienzo lienzo) {
        if (pool.cajaNegra()) {
            return ALTO_CAJA_NEGRA;
        }
        List<LanePlana> suyas = lanes.stream().filter(lane -> lane.poolId() == pool.id())
                .sorted(Comparator.comparingInt(LanePlana::orden).thenComparingLong(LanePlana::id)).toList();
        if (suyas.isEmpty()) {
            return ALTO_VACIO;
        }
        double yLane = y;
        for (LanePlana lane : suyas) {
            yLane += ubicarLane(lane, yLane, nodos, lienzo);
        }
        return yLane - y;
    }

    /** Una lane con sus nodos y sus artefactos: tan alta como ellos, o como una vacia. Devuelve su alto. */
    private static double ubicarLane(LanePlana lane, double yLane, List<NodoPlano> nodos, Lienzo lienzo) {
        List<NodoPlano> dentro = nodos.stream().filter(nodo -> nodo.laneId() == lane.id()).toList();
        List<ArtefactoPlano> suyos = lienzo.artefactos().stream()
                .filter(artefacto -> artefacto.laneId() == lane.id()).toList();
        double minY = IntStream.concat(dentro.stream().mapToInt(NodoPlano::posicionY),
                suyos.stream().mapToInt(ArtefactoPlano::posicionY)).min().orElse(0);
        double fondo = Math.max(
                dentro.stream().mapToDouble(nodo -> nodo.posicionY() + ALTO_TAREA).max().orElse(0),
                suyos.stream().mapToDouble(artefacto -> (double) artefacto.posicionY() + artefacto.alto()).max()
                        .orElse(0));
        double alto = dentro.isEmpty() && suyos.isEmpty() ? ALTO_VACIO
                : RELLENO_ARRIBA + fondo - minY + RELLENO_ABAJO;
        lienzo.formas().add(new Forma(lane.bpmnId(), "LANE",
                new Limites(MARGEN + FRANJA, yLane, lienzo.anchoPool() - FRANJA, alto), null));
        for (NodoPlano nodo : dentro) {
            double cx = MARGEN + 2 * FRANJA + RELLENO_X + ANCHO_TAREA / 2 + nodo.posicionX() - lienzo.minX();
            double cy = yLane + RELLENO_ARRIBA + ALTO_TAREA / 2 + nodo.posicionY() - minY;
            Ubicado ubicado = ubicar(nodo, cx, cy);
            lienzo.ubicados().put(nodo.id(), ubicado);
            lienzo.formas().add(forma(nodo, ubicado));
        }
        for (ArtefactoPlano artefacto : suyos) {
            lienzo.formas().add(formaDelArtefacto(artefacto,
                    MARGEN + 2 * FRANJA + RELLENO_X + artefacto.posicionX() - lienzo.minX(),
                    yLane + RELLENO_ARRIBA + artefacto.posicionY() - minY));
        }
        return alto;
    }

    private static Ubicado ubicar(NodoPlano nodo, double cx, double cy) {
        return switch (nodo.clase()) {
            case "COMPUERTA" -> new Ubicado(cx, cy, LADO_GATEWAY / 2, LADO_GATEWAY / 2, true);
            case "EVENTO" -> new Ubicado(cx, cy, RADIO_EVENTO, RADIO_EVENTO, false);
            default -> new Ubicado(cx, cy, ANCHO_TAREA / 2, ALTO_TAREA / 2, false);
        };
    }

    /** La forma del nodo y su etiqueta: dentro de la tarea, debajo del rombo o del circulo, como en el dibujo. */
    private static Forma forma(NodoPlano nodo, Ubicado u) {
        Limites limites = new Limites(u.cx() - u.medioAncho(), u.cy() - u.medioAlto(), 2 * u.medioAncho(),
                2 * u.medioAlto());
        Limites etiqueta = switch (nodo.clase()) {
            case "COMPUERTA" -> etiquetaDebajo(nodo.nombre(), 22, 2, u.cx() - 6, u.cy() + u.medioAlto() + 12,
                    false);
            case "EVENTO" -> etiquetaDebajo(nodo.nombre(), 20, 2, u.cx(), u.cy() + RADIO_EVENTO + 14, true);
            default -> null;
        };
        return new Forma(nodo.bpmnId(), nodo.clase(), limites, etiqueta);
    }

    /**
     * El rectangulo de una etiqueta debajo de un nodo: tantas lineas como parta el texto y tan ancho como la mas
     * larga. {@code primeraBase} es la linea base de la primera linea, como en el SVG.
     */
    private static Limites etiquetaDebajo(String texto, int maxCaracteres, int maxLineas, double x,
            double primeraBase, boolean centrada) {
        List<String> lineas = partirTexto(texto, maxCaracteres, maxLineas);
        if (lineas.isEmpty()) {
            return null;
        }
        double ancho = lineas.stream().mapToInt(String::length).max().orElse(0) * ANCHO_LETRA;
        double alto = lineas.size() * ALTO_LINEA;
        return new Limites(centrada ? x - ancho / 2 : x, primeraBase - ALTO_LINEA + 3, ancho, alto);
    }

    /** Ruta en angulo recto de un nodo a otro, y donde va su etiqueta: la misma de {@code trazarArco} en la web. */
    private static Arista trazarArco(ArcoPlano arco, Ubicado origen, Ubicado destino) {
        double dx = destino.cx() - origen.cx();
        double dy = destino.cy() - origen.cy();
        double haciaX = dx >= 0 ? 1 : -1;
        double haciaY = dy >= 0 ? 1 : -1;
        List<Punto> puntos;
        double etiquetaX;
        double etiquetaY;
        boolean desdeElInicio;
        if (Math.abs(dx) < origen.medioAncho() + destino.medioAncho() + 16) {
            double y1 = origen.cy() + haciaY * origen.medioAlto();
            double y2 = destino.cy() - haciaY * destino.medioAlto();
            double yMedio = (y1 + y2) / 2;
            puntos = List.of(new Punto(origen.cx(), y1), new Punto(origen.cx(), yMedio),
                    new Punto(destino.cx(), yMedio), new Punto(destino.cx(), y2));
            etiquetaX = origen.cx() + 6;
            etiquetaY = y1 + haciaY * ALTO_LINEA;
            desdeElInicio = true;
        } else {
            double x2 = destino.cx() - haciaX * destino.medioAncho();
            if (Math.abs(dy) < 2) {
                double x1 = origen.cx() + haciaX * origen.medioAncho();
                puntos = List.of(new Punto(x1, origen.cy()), new Punto(x2, origen.cy()));
                etiquetaX = (x1 + x2) / 2;
                etiquetaY = origen.cy() - 6;
                desdeElInicio = false;
            } else if (origen.esGateway()) {
                double y1 = origen.cy() + haciaY * origen.medioAlto();
                puntos = List.of(new Punto(origen.cx(), y1), new Punto(origen.cx(), destino.cy()),
                        new Punto(x2, destino.cy()));
                etiquetaX = origen.cx() + 8;
                etiquetaY = destino.cy() - 6;
                desdeElInicio = true;
            } else {
                double x1 = origen.cx() + haciaX * origen.medioAncho();
                double xMedio = (x1 + x2) / 2;
                puntos = List.of(new Punto(x1, origen.cy()), new Punto(xMedio, origen.cy()),
                        new Punto(xMedio, destino.cy()), new Punto(x2, destino.cy()));
                etiquetaX = (x1 + xMedio) / 2;
                etiquetaY = origen.cy() - 6;
                desdeElInicio = false;
            }
        }
        Limites etiqueta = null;
        if (arco.etiqueta() != null && !arco.etiqueta().isBlank()) {
            double ancho = arco.etiqueta().length() * ANCHO_LETRA;
            etiqueta = new Limites(desdeElInicio ? etiquetaX : etiquetaX - ancho / 2, etiquetaY - ALTO_LINEA + 3, ancho,
                    ALTO_LINEA);
        }
        return new Arista(arco.bpmnId(), "SECUENCIA", puntos, etiqueta);
    }

    /**
     * Cada mensaje sale del nodo al que esta anclado y entra en el que lo espera, cruzando el hueco entre los pools; un
     * extremo sin nodo arranca o termina en el borde de su pool, y los mensajes sin ningun nodo se reparten a lo ancho.
     */
    private static List<Arista> trazarMensajes(List<MensajePlano> mensajes, Map<Long, Caja> cajas,
            Map<Long, Ubicado> ubicados, double anchoPool) {
        List<MensajePlano> validos = mensajes.stream()
                .filter(mensaje -> cajas.containsKey(mensaje.poolOrigenId()) && cajas.containsKey(mensaje.poolDestinoId())
                        && mensaje.poolOrigenId() != mensaje.poolDestinoId())
                .sorted(Comparator.comparingLong(MensajePlano::id))
                .toList();
        List<Long> sueltos = validos.stream()
                .filter(mensaje -> !ubicados.containsKey(clave(mensaje.nodoOrigenId()))
                        && !ubicados.containsKey(clave(mensaje.nodoDestinoId())))
                .map(MensajePlano::id)
                .toList();
        double inicio = MARGEN + 2 * FRANJA + 24;
        double paso = (anchoPool - 2 * FRANJA - 48) / Math.max(sueltos.size(), 1);
        List<Arista> aristas = new ArrayList<>();
        for (MensajePlano mensaje : validos) {
            double columna = inicio + paso * (sueltos.indexOf(mensaje.id()) + 0.5);
            aristas.add(trazarMensaje(mensaje, cajas.get(mensaje.poolOrigenId()), cajas.get(mensaje.poolDestinoId()),
                    ubicados.get(clave(mensaje.nodoOrigenId())), ubicados.get(clave(mensaje.nodoDestinoId())), columna));
        }
        return aristas;
    }

    /**
     * Un mensaje entre dos pools. {@code desde} y {@code hasta} son sus nodos, o {@code null} si ese extremo es el
     * pool; {@code columna} es la x que le toca si no tiene ninguno.
     */
    private static Arista trazarMensaje(MensajePlano mensaje, Caja origen, Caja destino, Ubicado desde, Ubicado hasta,
            double columna) {
        boolean baja = destino.y() > origen.y();
        double hacia = baja ? 1 : -1;
        double yHueco = baja ? origen.y() + origen.alto() + SEPARACION / 2 : origen.y() - SEPARACION / 2;
        double x1 = xDeSalida(desde, hasta, columna);
        double x2 = hasta != null ? hasta.cx() : x1;
        double y1 = desde != null ? desde.cy() + hacia * desde.medioAlto() : bordeHacia(origen, baja);
        double y2 = hasta != null ? hasta.cy() - hacia * hasta.medioAlto() : bordeHacia(destino, !baja);
        List<Punto> puntos = x1 == x2 ? List.of(new Punto(x1, y1), new Punto(x2, y2))
                : List.of(new Punto(x1, y1), new Punto(x1, yHueco), new Punto(x2, yHueco), new Punto(x2, y2));
        return new Arista(mensaje.bpmnId(), "MENSAJE", puntos, null);
    }

    /** Sin nodo de salida, el mensaje sale a la altura del que lo recibe, y si tampoco hay, por su columna. */
    private static double xDeSalida(Ubicado desde, Ubicado hasta, double columna) {
        if (desde != null) {
            return desde.cx();
        }
        return hasta != null ? hasta.cx() : columna;
    }

    /** El borde de un pool que mira hacia abajo o hacia arriba. */
    private static double bordeHacia(Caja pool, boolean abajo) {
        return abajo ? pool.y() + pool.alto() : pool.y();
    }

    private static long clave(Long id) {
        return id == null ? -1 : id;
    }

    /** Parte un texto en lineas de hasta maxCaracteres sin cortar palabras, como la web. */
    static List<String> partirTexto(String texto, int maxCaracteres, int maxLineas) {
        List<String> lineas = new ArrayList<>();
        if (texto == null || texto.isBlank()) {
            return lineas;
        }
        String actual = "";
        for (String palabra : texto.trim().split("\\s+")) {
            String conPalabra = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (conPalabra.length() <= maxCaracteres) {
                actual = conPalabra;
            } else {
                if (!actual.isEmpty()) {
                    lineas.add(actual);
                }
                actual = recortar(palabra, maxCaracteres);
            }
        }
        if (!actual.isEmpty()) {
            lineas.add(actual);
        }
        if (lineas.size() > maxLineas) {
            List<String> visibles = new ArrayList<>(lineas.subList(0, maxLineas));
            visibles.set(maxLineas - 1, recortar(visibles.get(maxLineas - 1) + "…", maxCaracteres));
            return visibles;
        }
        return lineas;
    }

    private static String recortar(String texto, int maxCaracteres) {
        return texto.length() <= maxCaracteres ? texto : texto.substring(0, maxCaracteres - 1) + "…";
    }
}
