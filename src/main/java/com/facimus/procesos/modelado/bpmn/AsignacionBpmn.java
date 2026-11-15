package com.facimus.procesos.modelado.bpmn;

import javax.xml.stream.XMLStreamException;

import org.w3c.dom.Element;

import com.facimus.procesos.modelado.dto.response.AsignacionResponse;
import com.facimus.procesos.modelado.model.TipoAsignacion;

/**
 * PR 70: a quien le llega una {@code userTask}, como {@code bpm:asignacion} dentro de su {@code extensionElements}, con
 * el tipo y lo que ese tipo necesita: {@code usuario} (el id de la persona fija) o {@code campo} (la variable del
 * caso). La que espera en la bandeja de su rol no se escribe. Otra herramienta lo ignora; al importar, vuelve entera.
 */
final class AsignacionBpmn {

    static final String ASIGNACION = "asignacion";
    private static final String TIPO = "tipo";
    private static final String USUARIO = "usuario";
    private static final String CAMPO = "campo";

    private AsignacionBpmn() {
    }

    static void escribir(Salida salida, AsignacionResponse asignacion) throws XMLStreamException {
        salida.vacio("bpm", ASIGNACION);
        salida.atributo(TIPO, asignacion.tipo());
        salida.atributo(USUARIO, asignacion.usuarioId());
        salida.atributo(CAMPO, asignacion.campo());
    }

    /**
     * La asignacion que trae el nodo, o nula si no trae ninguna. No decide nada: un tipo que no existe llega sin tipo,
     * y la conversion la deja fuera con su nota; un usuario que no es un numero llega vacio, y lo dice el diagnostico.
     */
    static AsignacionResponse leer(Element nodo) {
        Element extensiones = Dom.hijo(nodo, EspaciosBpmn.BPMN, "extensionElements");
        Element asignacion = extensiones == null ? null : Dom.hijo(extensiones, EspaciosBpmn.BPM, ASIGNACION);
        if (asignacion == null) {
            return null;
        }
        String campo = Dom.atributo(asignacion, CAMPO);
        return new AsignacionResponse(tipo(Dom.atributo(asignacion, TIPO)), usuario(Dom.atributo(asignacion, USUARIO)),
                campo == null ? null : campo.strip());
    }

    private static TipoAsignacion tipo(String valor) {
        try {
            return valor == null ? null : TipoAsignacion.valueOf(valor.strip());
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    private static Long usuario(String valor) {
        try {
            return valor == null ? null : Long.valueOf(valor.strip());
        } catch (NumberFormatException _) {
            return null;
        }
    }
}
