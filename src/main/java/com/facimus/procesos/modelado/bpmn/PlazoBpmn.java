package com.facimus.procesos.modelado.bpmn;

import javax.xml.stream.XMLStreamException;

import org.w3c.dom.Element;

import com.facimus.procesos.modelado.dto.response.EscalamientoResponse;
import com.facimus.procesos.modelado.dto.response.PlazoResponse;
import com.facimus.procesos.modelado.model.AccionDeEscalamiento;
import com.facimus.procesos.modelado.model.UnidadDePlazo;

/**
 * PR 71: el plazo de una {@code userTask} o del caso en un {@code startEvent}, como {@code bpm:plazo}, y lo que escala un
 * {@code boundaryEvent} de temporizador que no interrumpe, como {@code bpm:escalamiento}, dentro de su
 * {@code extensionElements}. Otra herramienta los ignora; al importar, vuelven enteros.
 */
final class PlazoBpmn {

    static final String PLAZO = "plazo";
    static final String ESCALAMIENTO = "escalamiento";
    private static final String CANTIDAD = "cantidad";
    private static final String UNIDAD = "unidad";
    private static final String ACCION = "accion";
    private static final String USUARIO = "usuario";
    /** Un {@code bpm:plazo} que estaba pero no se entendia. */
    static final PlazoResponse ILEGIBLE = new PlazoResponse(0, null);

    private PlazoBpmn() {
    }

    static void escribir(Salida salida, PlazoResponse plazo) throws XMLStreamException {
        salida.vacio("bpm", PLAZO);
        salida.atributo(CANTIDAD, plazo.cantidad());
        salida.atributo(UNIDAD, plazo.unidad());
    }

    static void escribir(Salida salida, EscalamientoResponse escalamiento) throws XMLStreamException {
        salida.vacio("bpm", ESCALAMIENTO);
        salida.atributo(ACCION, escalamiento.accion());
        salida.atributo(USUARIO, escalamiento.usuarioId());
    }

    /**
     * El plazo que trae el nodo, o nulo si no trae ninguno. Uno que no se entiende (sin una unidad que exista, o sin
     * una cantidad entera dentro del tope) llega sin unidad, y la conversion lo deja fuera con su nota.
     */
    static PlazoResponse leerPlazo(Element nodo) {
        Element plazo = extension(nodo, PLAZO);
        if (plazo == null) {
            return null;
        }
        UnidadDePlazo unidad = enumerado(UnidadDePlazo.class, Dom.atributo(plazo, UNIDAD));
        Integer cantidad = entero(Dom.atributo(plazo, CANTIDAD));
        return unidad == null || cantidad == null || !unidad.admite(cantidad) ? ILEGIBLE
                : new PlazoResponse(cantidad, unidad);
    }

    /** El escalamiento que trae el nodo; su accion llega nula si no es una que exista, y la conversion lo dice. */
    static EscalamientoResponse leerEscalamiento(Element nodo) {
        Element escalamiento = extension(nodo, ESCALAMIENTO);
        if (escalamiento == null) {
            return null;
        }
        return new EscalamientoResponse(enumerado(AccionDeEscalamiento.class, Dom.atributo(escalamiento, ACCION)),
                largo(Dom.atributo(escalamiento, USUARIO)));
    }

    private static Element extension(Element nodo, String nombre) {
        Element extensiones = Dom.hijo(nodo, EspaciosBpmn.BPMN, "extensionElements");
        return extensiones == null ? null : Dom.hijo(extensiones, EspaciosBpmn.BPM, nombre);
    }

    private static <E extends Enum<E>> E enumerado(Class<E> tipo, String valor) {
        try {
            return valor == null ? null : Enum.valueOf(tipo, valor.strip());
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    private static Integer entero(String valor) {
        try {
            return valor == null ? null : Integer.valueOf(valor.strip());
        } catch (NumberFormatException _) {
            return null;
        }
    }

    /** Un supervisor que no es un numero se lee como ninguno: el dueno del proceso. */
    private static Long largo(String valor) {
        try {
            return valor == null ? null : Long.valueOf(valor.strip());
        } catch (NumberFormatException _) {
            return null;
        }
    }
}
