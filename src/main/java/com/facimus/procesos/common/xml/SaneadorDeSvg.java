package com.facimus.procesos.common.xml;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * PR 62: el dibujo de una version, que la web hace con bpmn-js y entrega al publicar. El servidor no dibuja BPMN, pero
 * tampoco guarda lo que un navegador le mande: un SVG puede llevar scripts, manejadores de eventos, objetos HTML o
 * referencias a otros sitios. Se lee con {@link SecureXml}, con sus limites, y se rehace con una lista blanca de lo que
 * un diagrama usa (formas, texto, marcadores y estilos sin URL de afuera). Lo demas se quita.
 */
public final class SaneadorDeSvg {

    private static final String SVG = "http://www.w3.org/2000/svg";

    /** Un diagrama grande cabe holgado; mas que esto no es un diagrama. */
    public static final LimitesXml LIMITES = new LimitesXml(4L * 1024 * 1024, 200_000, 64, 32, 512 * 1024,
            Duration.ofSeconds(10));

    private static final Set<String> ELEMENTOS = Set.of("svg", "g", "defs", "marker", "path", "rect", "circle",
            "ellipse", "line", "polyline", "polygon", "text", "tspan", "title", "desc");

    private static final Set<String> ATRIBUTOS = Set.of("id", "class", "d", "x", "y", "x1", "y1", "x2", "y2", "cx",
            "cy", "r", "rx", "ry", "dx", "dy", "width", "height", "points", "transform", "fill", "fill-opacity",
            "fill-rule", "stroke", "stroke-width", "stroke-dasharray", "stroke-linecap", "stroke-linejoin",
            "stroke-opacity", "stroke-miterlimit", "opacity", "font-family", "font-size", "font-weight", "font-style",
            "text-anchor", "dominant-baseline", "viewBox", "version", "markerWidth", "markerHeight", "refX", "refY",
            "orient", "markerUnits", "marker-start", "marker-end", "marker-mid", "preserveAspectRatio", "style",
            "data-element-id", "visibility", "display", "letter-spacing", "lineHeight");

    /** Una URL solo puede apuntar a algo del mismo dibujo: {@code url(#marcador)}. */
    private static final Pattern URL = Pattern.compile("url\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_INTERNA = Pattern.compile("url\\(\\s*['\"]?#[\\w.:-]+['\"]?\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PELIGROSO = Pattern.compile("javascript:|expression|@import|behavior|-moz-binding",
            Pattern.CASE_INSENSITIVE);

    private SaneadorDeSvg() {
    }

    /** El SVG limpio, serializado. Un XML que no es un SVG, o que pasa los limites, se rechaza. */
    public static String sanear(byte[] svg) {
        Document original = SecureXml.documento(new ByteArrayInputStream(svg), LIMITES);
        Element raiz = original.getDocumentElement();
        if (!"svg".equals(raiz.getLocalName()) || !SVG.equals(raiz.getNamespaceURI())) {
            throw new XmlRechazadoException("El dibujo no es un SVG.");
        }
        Document limpio = SecureXml.documentos().newDocument();
        limpio.appendChild(copiar(raiz, limpio));
        return serializar(limpio);
    }

    private static Element copiar(Element origen, Document destino) {
        Element copia = destino.createElementNS(SVG, origen.getLocalName());
        NamedNodeMap atributos = origen.getAttributes();
        for (int i = 0; i < atributos.getLength(); i++) {
            Attr atributo = (Attr) atributos.item(i);
            String nombre = atributo.getLocalName() == null ? atributo.getName() : atributo.getLocalName();
            if (atributo.getNamespaceURI() == null && ATRIBUTOS.contains(nombre) && valorSeguro(atributo.getValue())) {
                copia.setAttribute(nombre, atributo.getValue());
            }
        }
        for (Node hijo : hijos(origen)) {
            if (hijo.getNodeType() == Node.ELEMENT_NODE && SVG.equals(hijo.getNamespaceURI())
                    && ELEMENTOS.contains(hijo.getLocalName())) {
                copia.appendChild(copiar((Element) hijo, destino));
            } else if (hijo.getNodeType() == Node.TEXT_NODE) {
                copia.appendChild(destino.createTextNode(hijo.getNodeValue()));
            }
        }
        return copia;
    }

    /** Sin URL de afuera ni lo que un navegador viejo ejecuta desde un estilo. */
    private static boolean valorSeguro(String valor) {
        String minusculas = valor.toLowerCase(Locale.ROOT);
        if (PELIGROSO.matcher(minusculas).find()) {
            return false;
        }
        return !URL.matcher(valor).find() || URL.matcher(URL_INTERNA.matcher(valor).replaceAll("")).results()
                .findAny().isEmpty();
    }

    private static List<Node> hijos(Element elemento) {
        List<Node> hijos = new ArrayList<>();
        for (Node hijo = elemento.getFirstChild(); hijo != null; hijo = hijo.getNextSibling()) {
            hijos.add(hijo);
        }
        return hijos;
    }

    private static String serializar(Document documento) {
        try {
            Transformer transformador = SecureXml.transformador();
            transformador.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformador.setOutputProperty(OutputKeys.INDENT, "no");
            StringWriter salida = new StringWriter();
            transformador.transform(new DOMSource(documento), new StreamResult(salida));
            return salida.toString();
        } catch (TransformerException e) {
            throw new XmlRechazadoException("El dibujo no se pudo escribir: " + e.getMessage(), e);
        }
    }
}
