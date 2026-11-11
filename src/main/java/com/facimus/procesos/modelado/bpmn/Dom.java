package com.facimus.procesos.modelado.bpmn;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** PR 54: lo poco de DOM que hace falta para leer un BPMN: hijos por nombre, atributos y texto. */
final class Dom {

    private Dom() {
    }

    /** Los hijos elemento, en orden. */
    static List<Element> hijos(Element padre) {
        List<Element> hijos = new ArrayList<>();
        for (Node hijo = padre.getFirstChild(); hijo != null; hijo = hijo.getNextSibling()) {
            if (hijo instanceof Element elemento) {
                hijos.add(elemento);
            }
        }
        return hijos;
    }

    /** Los hijos de ese espacio y nombre local. */
    static List<Element> hijos(Element padre, String espacio, String nombre) {
        return hijos(padre).stream()
                .filter(hijo -> espacio.equals(hijo.getNamespaceURI()) && nombre.equals(hijo.getLocalName()))
                .toList();
    }

    static Element hijo(Element padre, String espacio, String nombre) {
        List<Element> hijos = hijos(padre, espacio, nombre);
        return hijos.isEmpty() ? null : hijos.getFirst();
    }

    /** El atributo sin espacio de nombres, o nulo si no esta o esta vacio. */
    static String atributo(Element elemento, String nombre) {
        String valor = elemento.getAttribute(nombre);
        return valor == null || valor.isBlank() ? null : valor;
    }

    static String atributo(Element elemento, String espacio, String nombre) {
        String valor = elemento.getAttributeNS(espacio, nombre);
        return valor == null || valor.isBlank() ? null : valor;
    }

    /** Una referencia {@code xsd:QName}: el id, sin el prefijo que algunas herramientas le ponen. */
    static String referencia(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.strip();
        int dosPuntos = limpio.indexOf(':');
        return dosPuntos >= 0 ? limpio.substring(dosPuntos + 1) : limpio;
    }

    /** El texto del elemento, con los espacios y saltos de linea de un nombre de varias lineas unidos en uno. */
    static String nombre(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.replaceAll("\\s+", " ").strip();
        return limpio.isEmpty() ? null : limpio;
    }

    static String texto(Element elemento) {
        if (elemento == null) {
            return null;
        }
        String texto = elemento.getTextContent();
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
