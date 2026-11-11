package com.facimus.procesos.modelado.bpmn;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.facimus.procesos.common.xml.LimitesXml;
import com.facimus.procesos.common.xml.SecureXml;

/**
 * PR 54: lo que un archivo de otra herramienta trae en un elemento y el modelo no tiene, para devolverlo intacto al
 * exportar (D25): los atributos de otros espacios de nombres ({@code camunda:assignee}) y los hijos de su
 * {@code extensionElements} que no son {@code bpm:}. Se guarda en un envoltorio propio:
 *
 * <pre>
 * &lt;c:conservado xmlns:c="urn:bpmn-process-manager:conservado:1" xmlns:camunda="..." camunda:assignee="ana"&gt;
 *   &lt;camunda:properties&gt;...&lt;/camunda:properties&gt;
 * &lt;/c:conservado&gt;
 * </pre>
 *
 * <p>Saneado: solo elementos, atributos y texto; los comentarios y las instrucciones de proceso no pasan. Cada prefijo
 * se vuelve a declarar donde se usa, asi que el envoltorio se lee solo, sin el archivo del que salio.
 */
public final class ExtensionesAjenas {

    public static final String ENVOLTORIO = "urn:bpmn-process-manager:conservado:1";

    /** Lo que un elemento puede traer de ajeno, como mucho: mas que eso no es configuracion, es otro documento. */
    public static final int TOPE_POR_ELEMENTO = 16 * 1024;

    /** Lo que se conserva de un archivo entero. */
    public static final int TOPE_POR_ARCHIVO = 512 * 1024;

    private static final Set<String> PROPIOS = Set.of(EspaciosBpmn.BPMN, EspaciosBpmn.BPMNDI, EspaciosBpmn.DC,
            EspaciosBpmn.DI, EspaciosBpmn.XSI, EspaciosBpmn.BPM, ENVOLTORIO, XMLConstants.XML_NS_URI,
            XMLConstants.XMLNS_ATTRIBUTE_NS_URI);

    private ExtensionesAjenas() {
    }

    /** El envoltorio de lo ajeno del elemento, o nulo si no trae nada. */
    static String de(Element elemento) {
        List<Attr> atributos = atributosAjenos(elemento);
        List<Element> hijos = new ArrayList<>();
        for (Element extensiones : Dom.hijos(elemento, EspaciosBpmn.BPMN, "extensionElements")) {
            for (Element hijo : Dom.hijos(extensiones)) {
                if (!PROPIOS.contains(hijo.getNamespaceURI())) {
                    hijos.add(hijo);
                }
            }
        }
        if (atributos.isEmpty() && hijos.isEmpty()) {
            return null;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        XMLStreamWriter xml = SecureXml.escritor(bytes);
        try {
            Ambito ambito = new Ambito();
            ambito.entrar();
            xml.writeStartElement("c", "conservado", ENVOLTORIO);
            xml.writeNamespace("c", ENVOLTORIO);
            ambito.unir("c", ENVOLTORIO);
            for (Attr atributo : atributos) {
                String prefijo = ambito.declarar(atributo.getPrefix(), atributo.getNamespaceURI(), xml);
                xml.writeAttribute(prefijo, atributo.getNamespaceURI(), atributo.getLocalName(), atributo.getValue());
            }
            for (Element hijo : hijos) {
                copiar(hijo, xml, ambito);
            }
            xml.writeEndElement();
            xml.close();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Lo ajeno de " + elemento.getAttribute("id") + " no se pudo guardar", e);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    /** Un envoltorio guardado, listo para escribirse en un archivo. */
    static Element leer(String envoltorio) {
        return SecureXml.documento(new ByteArrayInputStream(envoltorio.getBytes(StandardCharsets.UTF_8)),
                LimitesXml.BPMN).getDocumentElement();
    }

    /** Los atributos ajenos del envoltorio, que van en el elemento. */
    static List<Attr> atributos(Element envoltorio) {
        return atributosAjenos(envoltorio);
    }

    /** Los espacios de nombres que usa lo ajeno, con el prefijo que traian, para declararlos una vez en la raiz. */
    static Map<String, String> espacios(Iterable<Element> envoltorios) {
        Map<String, String> espacios = new LinkedHashMap<>();
        for (Element envoltorio : envoltorios) {
            recorrerEspacios(envoltorio, espacios);
        }
        espacios.remove(ENVOLTORIO);
        return espacios;
    }

    /**
     * Copia un elemento ajeno a otro escritor. Los prefijos son los de {@code prefijos} (espacio → prefijo), que ya
     * se declararon en la raiz del archivo, asi que aqui no se declara nada.
     */
    static void escribir(Element elemento, XMLStreamWriter xml, Map<String, String> prefijos) throws XMLStreamException {
        String espacio = elemento.getNamespaceURI();
        boolean vacio = !elemento.hasChildNodes();
        String local = elemento.getLocalName() != null ? elemento.getLocalName() : elemento.getTagName();
        if (espacio == null && vacio) {
            xml.writeEmptyElement(local);
        } else if (espacio == null) {
            xml.writeStartElement(local);
        } else if (vacio) {
            xml.writeEmptyElement(prefijos.get(espacio), elemento.getLocalName(), espacio);
        } else {
            xml.writeStartElement(prefijos.get(espacio), elemento.getLocalName(), espacio);
        }
        NamedNodeMap todos = elemento.getAttributes();
        for (int i = 0; i < todos.getLength(); i++) {
            Attr atributo = (Attr) todos.item(i);
            String suyo = atributo.getNamespaceURI();
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(suyo)) {
                continue;
            }
            if (suyo == null) {
                xml.writeAttribute(nombreLocal(atributo), atributo.getValue());
            } else {
                xml.writeAttribute(prefijos.get(suyo), suyo, atributo.getLocalName(), atributo.getValue());
            }
        }
        if (vacio) {
            return;
        }
        for (Node hijo = elemento.getFirstChild(); hijo != null; hijo = hijo.getNextSibling()) {
            if (hijo instanceof Element suyo) {
                escribir(suyo, xml, prefijos);
            } else if (hijo.getNodeType() == Node.TEXT_NODE || hijo.getNodeType() == Node.CDATA_SECTION_NODE) {
                xml.writeCharacters(hijo.getNodeValue());
            }
        }
        xml.writeEndElement();
    }

    private static List<Attr> atributosAjenos(Element elemento) {
        List<Attr> ajenos = new ArrayList<>();
        NamedNodeMap todos = elemento.getAttributes();
        for (int i = 0; i < todos.getLength(); i++) {
            Attr atributo = (Attr) todos.item(i);
            String espacio = atributo.getNamespaceURI();
            if (espacio != null && !PROPIOS.contains(espacio)) {
                ajenos.add(atributo);
            }
        }
        return ajenos;
    }

    private static void copiar(Element elemento, XMLStreamWriter xml, Ambito ambito) throws XMLStreamException {
        ambito.entrar();
        String espacio = elemento.getNamespaceURI();
        if (espacio == null) {
            xml.writeStartElement(elemento.getLocalName() != null ? elemento.getLocalName() : elemento.getTagName());
        } else {
            String prefijo = ambito.prefijoLibre(elemento.getPrefix(), espacio);
            xml.writeStartElement(prefijo, elemento.getLocalName(), espacio);
            ambito.declarar(prefijo, espacio, xml);
        }
        NamedNodeMap todos = elemento.getAttributes();
        for (int i = 0; i < todos.getLength(); i++) {
            Attr atributo = (Attr) todos.item(i);
            String suyo = atributo.getNamespaceURI();
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(suyo)) {
                continue;
            }
            if (suyo == null) {
                xml.writeAttribute(nombreLocal(atributo), atributo.getValue());
            } else {
                String prefijo = ambito.declarar(atributo.getPrefix(), suyo, xml);
                xml.writeAttribute(prefijo, suyo, atributo.getLocalName(), atributo.getValue());
            }
        }
        for (Node hijo = elemento.getFirstChild(); hijo != null; hijo = hijo.getNextSibling()) {
            if (hijo instanceof Element suyo) {
                copiar(suyo, xml, ambito);
            } else if (hijo.getNodeType() == Node.TEXT_NODE || hijo.getNodeType() == Node.CDATA_SECTION_NODE) {
                xml.writeCharacters(hijo.getNodeValue());
            }
        }
        xml.writeEndElement();
        ambito.salir();
    }

    private static void recorrerEspacios(Element elemento, Map<String, String> espacios) {
        anotar(elemento.getNamespaceURI(), elemento.getPrefix(), espacios);
        NamedNodeMap todos = elemento.getAttributes();
        for (int i = 0; i < todos.getLength(); i++) {
            Attr atributo = (Attr) todos.item(i);
            if (!XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(atributo.getNamespaceURI())) {
                anotar(atributo.getNamespaceURI(), atributo.getPrefix(), espacios);
            }
        }
        for (Element hijo : Dom.hijos(elemento)) {
            recorrerEspacios(hijo, espacios);
        }
    }

    private static void anotar(String espacio, String prefijo, Map<String, String> espacios) {
        if (espacio != null && !XMLConstants.XML_NS_URI.equals(espacio)) {
            espacios.putIfAbsent(espacio, prefijo == null ? "ns" : prefijo);
        }
    }

    private static String nombreLocal(Attr atributo) {
        return atributo.getLocalName() != null ? atributo.getLocalName() : atributo.getName();
    }

    /** Los prefijos en vigor mientras se copia, nivel por nivel, y los que hay que declarar al entrar en uno. */
    private static final class Ambito {

        private final Deque<Map<String, String>> niveles = new ArrayDeque<>();
        private int generados;

        void entrar() {
            niveles.push(new HashMap<>());
        }

        void salir() {
            niveles.pop();
        }

        void unir(String prefijo, String espacio) {
            niveles.peek().put(prefijo, espacio);
        }

        /** El prefijo con que se escribe el espacio: el que traia si esta libre o ya es suyo, si no uno nuevo. */
        String prefijoLibre(String preferido, String espacio) {
            if (preferido != null && (espacio.equals(enVigor(preferido)) || enVigor(preferido) == null)) {
                return preferido;
            }
            for (Map<String, String> nivel : niveles) {
                for (Map.Entry<String, String> union : nivel.entrySet()) {
                    if (union.getValue().equals(espacio) && espacio.equals(enVigor(union.getKey()))) {
                        return union.getKey();
                    }
                }
            }
            String nuevo;
            do {
                nuevo = "ns" + ++generados;
            } while (enVigor(nuevo) != null);
            return nuevo;
        }

        /** Declara el prefijo en el elemento abierto si todavia no esta en vigor con ese espacio, y lo devuelve. */
        String declarar(String preferido, String espacio, XMLStreamWriter xml) throws XMLStreamException {
            String prefijo = prefijoLibre(preferido, espacio);
            if (!espacio.equals(enVigor(prefijo))) {
                xml.writeNamespace(prefijo, espacio);
                unir(prefijo, espacio);
            }
            return prefijo;
        }

        private String enVigor(String prefijo) {
            for (Map<String, String> nivel : niveles) {
                if (nivel.containsKey(prefijo)) {
                    return nivel.get(prefijo);
                }
            }
            return null;
        }
    }
}
