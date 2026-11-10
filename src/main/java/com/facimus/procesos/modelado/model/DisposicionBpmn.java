package com.facimus.procesos.modelado.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PR 52: la geometria BPMN DI de un diagrama, con la misma cuenta que dibuja la web ({@code lienzo.ts}): los pools en
 * filas (clientes arriba, la organizacion con sus lanes en medio, los demas abajo), cada lane tan alta como sus
 * nodos, la x de cada nodo comun a todo el diagrama y su y relativa a su lane. Lo que sale son coordenadas absolutas,
 * con el origen arriba a la izquierda: formas con sus limites y los de su etiqueta, y aristas con sus puntos.
 *
 * <p>Es una funcion pura, sin base ni Spring: la usan la migracion que convierte los diagramas que ya existian y el
 * servicio que la recalcula con cada cambio de un proceso.
 */
public final class DisposicionBpmn {

    static final double MARGEN = 16;
    static final double FRANJA = 30;
    static final double ANCHO_TAREA = 128;
    static final double ALTO_TAREA = 56;
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

    /** Una tarea, compuerta o evento: su clase (TAREA, COMPUERTA o EVENTO), su posicion y su nombre. */
    public record NodoPlano(long id, String bpmnId, long laneId, String clase, int posicionX, int posicionY,
            String nombre) {
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

    public static Geometria calcular(List<PoolPlano> pools, List<LanePlana> lanes, List<NodoPlano> nodos,
            List<ArcoPlano> arcos, List<MensajePlano> mensajes) {
        List<Forma> formas = new ArrayList<>();
        List<Arista> aristas = new ArrayList<>();
        double minX = nodos.stream().mapToInt(NodoPlano::posicionX).min().orElse(0);
        double maxX = nodos.stream().mapToInt(NodoPlano::posicionX).max().orElse(0);
        double anchoPool = Math.max(2 * FRANJA + maxX - minX + ANCHO_TAREA + 2 * RELLENO_X, ANCHO_MINIMO);
        Map<Long, Ubicado> ubicados = new HashMap<>();
        Map<Long, Caja> cajas = new LinkedHashMap<>();

        List<PoolPlano> ordenados = pools.stream()
                .sorted(Comparator.comparingInt((PoolPlano pool) -> FILA.getOrDefault(pool.tipoParticipante(), 2))
                        .thenComparingInt(PoolPlano::orden).thenComparingLong(PoolPlano::id))
                .toList();
        double y = MARGEN;
        for (PoolPlano pool : ordenados) {
            List<LanePlana> suyas = lanes.stream().filter(lane -> lane.poolId() == pool.id())
                    .sorted(Comparator.comparingInt(LanePlana::orden).thenComparingLong(LanePlana::id)).toList();
            double yLane = y;
            if (!pool.cajaNegra()) {
                for (LanePlana lane : suyas) {
                    List<NodoPlano> dentro = nodos.stream().filter(nodo -> nodo.laneId() == lane.id()).toList();
                    double minY = dentro.stream().mapToInt(NodoPlano::posicionY).min().orElse(0);
                    double maxY = dentro.stream().mapToInt(NodoPlano::posicionY).max().orElse(0);
                    double alto = dentro.isEmpty() ? ALTO_VACIO
                            : RELLENO_ARRIBA + maxY - minY + ALTO_TAREA + RELLENO_ABAJO;
                    formas.add(new Forma(lane.bpmnId(), "LANE",
                            new Limites(MARGEN + FRANJA, yLane, anchoPool - FRANJA, alto), null));
                    for (NodoPlano nodo : dentro) {
                        double cx = MARGEN + 2 * FRANJA + RELLENO_X + ANCHO_TAREA / 2 + nodo.posicionX() - minX;
                        double cy = yLane + RELLENO_ARRIBA + ALTO_TAREA / 2 + nodo.posicionY() - minY;
                        Ubicado ubicado = ubicar(nodo, cx, cy);
                        ubicados.put(nodo.id(), ubicado);
                        formas.add(forma(nodo, ubicado));
                    }
                    yLane += alto;
                }
            }
            double alto = pool.cajaNegra() ? ALTO_CAJA_NEGRA : suyas.isEmpty() ? ALTO_VACIO : yLane - y;
            Caja caja = new Caja(MARGEN, y, anchoPool, alto);
            cajas.put(pool.id(), caja);
            formas.add(new Forma(pool.bpmnId(), "PARTICIPANTE", new Limites(caja.x(), caja.y(), caja.ancho(),
                    caja.alto()), null));
            y += alto + SEPARACION;
        }

        for (ArcoPlano arco : arcos) {
            Ubicado origen = ubicados.get(arco.origenId());
            Ubicado destino = ubicados.get(arco.destinoId());
            if (origen != null && destino != null) {
                aristas.add(trazarArco(arco, origen, destino));
            }
        }
        aristas.addAll(trazarMensajes(mensajes, cajas, ubicados, anchoPool));
        return new Geometria(List.copyOf(formas), List.copyOf(aristas));
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
            Caja origen = cajas.get(mensaje.poolOrigenId());
            Caja destino = cajas.get(mensaje.poolDestinoId());
            boolean baja = destino.y() > origen.y();
            double hacia = baja ? 1 : -1;
            double columna = inicio + paso * (sueltos.indexOf(mensaje.id()) + 0.5);
            Ubicado desde = ubicados.get(clave(mensaje.nodoOrigenId()));
            Ubicado hasta = ubicados.get(clave(mensaje.nodoDestinoId()));
            double yHueco = baja ? origen.y() + origen.alto() + SEPARACION / 2 : origen.y() - SEPARACION / 2;
            double x1 = desde != null ? desde.cx() : hasta != null ? hasta.cx() : columna;
            double x2 = hasta != null ? hasta.cx() : x1;
            double y1 = desde != null ? desde.cy() + hacia * desde.medioAlto() : baja ? origen.y() + origen.alto()
                    : origen.y();
            double y2 = hasta != null ? hasta.cy() - hacia * hasta.medioAlto() : baja ? destino.y()
                    : destino.y() + destino.alto();
            List<Punto> puntos = x1 == x2 ? List.of(new Punto(x1, y1), new Punto(x2, y2))
                    : List.of(new Punto(x1, y1), new Punto(x1, yHueco), new Punto(x2, yHueco), new Punto(x2, y2));
            aristas.add(new Arista(mensaje.bpmnId(), "MENSAJE", puntos, null));
        }
        return aristas;
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
