package com.facimus.procesos.modelado.bpmn;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.w3c.dom.Element;

/**
 * PR 53: StAX con sangria. Un elemento con hijos cierra en su propia linea; uno con solo texto queda en una. Asi el
 * archivo se lee en un editor y un cambio del diagrama es un cambio de pocas lineas en un diff.
 */
final class Salida {

    private static final Map<String, String> ESPACIOS = Map.of(
            "bpmn", EspaciosBpmn.BPMN,
            "bpmndi", EspaciosBpmn.BPMNDI,
            "dc", EspaciosBpmn.DC,
            "di", EspaciosBpmn.DI,
            "xsi", EspaciosBpmn.XSI,
            "bpm", EspaciosBpmn.BPM);

    private final XMLStreamWriter xml;
    /** Por cada elemento abierto, si ya tuvo un hijo: decide si su cierre va en otra linea. */
    private final Deque<Boolean> abiertos = new ArrayDeque<>();

    Salida(XMLStreamWriter xml) {
        this.xml = xml;
    }

    void empezar() throws XMLStreamException {
        xml.writeStartDocument("UTF-8", "1.0");
    }

    /** Los espacios propios y, despues, los de lo ajeno que trae el archivo (espacio → prefijo). */
    void declararEspacios(Map<String, String> ajenos) throws XMLStreamException {
        for (String prefijo : new String[] {"bpmn", "bpmndi", "dc", "di", "xsi", "bpm"}) {
            xml.writeNamespace(prefijo, ESPACIOS.get(prefijo));
        }
        for (Map.Entry<String, String> ajeno : ajenos.entrySet()) {
            xml.writeNamespace(ajeno.getValue(), ajeno.getKey());
        }
    }

    /** Un atributo de otro espacio, con el prefijo que se le dio en la raiz. */
    void atributoAjeno(String prefijo, String espacio, String nombre, String valor) throws XMLStreamException {
        xml.writeAttribute(prefijo, espacio, nombre, valor);
    }

    /** Un elemento ajeno entero, en su propia linea; por dentro va como vino. */
    void ajeno(Element elemento, Map<String, String> prefijos) throws XMLStreamException {
        antesDeUnHijo();
        ExtensionesAjenas.escribir(elemento, xml, prefijos);
    }

    void abrir(String prefijo, String nombre) throws XMLStreamException {
        antesDeUnHijo();
        xml.writeStartElement(prefijo, nombre, ESPACIOS.get(prefijo));
        abiertos.push(false);
    }

    /** Un elemento sin hijos: los atributos que siguen son suyos. */
    void vacio(String prefijo, String nombre) throws XMLStreamException {
        antesDeUnHijo();
        xml.writeEmptyElement(prefijo, nombre, ESPACIOS.get(prefijo));
    }

    /** Un elemento con solo texto, en una linea. */
    void conTexto(String prefijo, String nombre, String texto) throws XMLStreamException {
        antesDeUnHijo();
        xml.writeStartElement(prefijo, nombre, ESPACIOS.get(prefijo));
        xml.writeCharacters(texto);
        xml.writeEndElement();
    }

    /** Texto dentro del elemento abierto, que entonces cierra en la misma linea si no tiene hijos. */
    void texto(String texto) throws XMLStreamException {
        xml.writeCharacters(texto);
    }

    void cerrar() throws XMLStreamException {
        boolean tuvoHijos = abiertos.pop();
        if (tuvoHijos) {
            sangria(abiertos.size());
        }
        xml.writeEndElement();
    }

    /** Un atributo sin espacio de nombres; nulo, no se escribe. */
    void atributo(String nombre, Object valor) throws XMLStreamException {
        if (valor != null) {
            xml.writeAttribute(nombre, texto(valor));
        }
    }

    void atributo(String prefijo, String nombre, Object valor) throws XMLStreamException {
        if (valor != null) {
            xml.writeAttribute(prefijo, ESPACIOS.get(prefijo), nombre, texto(valor));
        }
    }

    void terminar() throws XMLStreamException {
        xml.writeCharacters("\n");
        xml.writeEndDocument();
        xml.flush();
    }

    private void antesDeUnHijo() throws XMLStreamException {
        if (!abiertos.isEmpty() && !abiertos.peek()) {
            abiertos.pop();
            abiertos.push(true);
        }
        sangria(abiertos.size());
    }

    private void sangria(int nivel) throws XMLStreamException {
        xml.writeCharacters("\n" + "  ".repeat(nivel));
    }

    /** Un numero entero se escribe sin decimales, como en el dibujo; uno con mitad, con su punto. */
    static String texto(Object valor) {
        if (valor instanceof Double numero) {
            return numero == Math.rint(numero) && !Double.isInfinite(numero)
                    ? Long.toString(numero.longValue())
                    : String.format(Locale.ROOT, "%s", numero);
        }
        return String.valueOf(valor);
    }
}
