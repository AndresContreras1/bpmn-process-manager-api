package com.facimus.procesos.common.xml;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.util.StreamReaderDelegate;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.w3c.dom.Document;
import org.w3c.dom.ls.LSInput;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

/**
 * PR 52: la unica forma de crear un parser de XML en el proyecto; una regla de ArchUnit prohibe crear las factorias en
 * cualquier otra parte. Lo que entra por la API es de quien lo manda, y un XML puede pedir al servidor que lea un
 * archivo, que llame a una URL o que multiplique una entidad hasta agotar la memoria. Aqui no:
 *
 * <ul>
 * <li>sin DTD: un DOCTYPE se rechaza, y con el las entidades internas y externas, el billion laughs y la explosion
 * cuadratica;</li>
 * <li>sin nada externo: {@code ACCESS_EXTERNAL_DTD}, {@code _SCHEMA} y {@code _STYLESHEET} vacios, resolutores que
 * rechazan, sin XInclude, y <em>secure processing</em> encendido;</li>
 * <li>con limites antes y durante la lectura ({@link LimitesXml}): tamano, elementos, profundidad, atributos, largo
 * del texto y tiempo;</li>
 * <li>solo UTF-8 y UTF-16: una codificacion rara es la forma de esconder un DOCTYPE a un filtro ingenuo.</li>
 * </ul>
 *
 * Lo que no se puede leer termina en {@link XmlRechazadoException}, que responde 400.
 */
public final class SecureXml {

    private static final Set<String> CODIFICACIONES = Set.of("UTF-8", "UTF-16", "UTF-16BE", "UTF-16LE");

    private static final String SIN_DTD = "El XML trae un DOCTYPE: los DTD y las entidades no se aceptan.";

    private SecureXml() {
    }

    /** StAX endurecido: la base de toda lectura, y la que aplica los limites. */
    public static XMLInputFactory entrada() {
        XMLInputFactory factoria = XMLInputFactory.newFactory();
        factoria.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factoria.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factoria.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        factoria.setProperty(XMLInputFactory.IS_VALIDATING, false);
        // Un texto es un solo evento: asi su largo se mide entero
        factoria.setProperty(XMLInputFactory.IS_COALESCING, true);
        factoria.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factoria.setXMLResolver((publico, sistema, base, espacio) -> {
            throw new XMLStreamException(SIN_DTD);
        });
        return factoria;
    }

    /**
     * Un lector que aplica los limites mientras se lee, y rechaza un DTD o una entidad si aparecen. El tamano se cuenta
     * sobre los bytes que entran, asi que tampoco sirve un archivo enorme que el parser no llegue a terminar.
     */
    public static XMLStreamReader lector(InputStream entrada, LimitesXml limites) {
        try {
            XMLStreamReader lector = entrada().createXMLStreamReader(new Contado(entrada, limites.bytes()));
            comprobarCodificacion(lector.getCharacterEncodingScheme());
            comprobarCodificacion(lector.getEncoding());
            return new Limitado(lector, limites);
        } catch (XMLStreamException e) {
            throw malFormado(e);
        }
    }

    /**
     * Lee el documento entero con los limites y devuelve sus bytes, ya comprobados: lo que se parsee despues con DOM
     * o SAX ya paso por aqui.
     */
    public static byte[] comprobar(InputStream entrada, LimitesXml limites) {
        byte[] bytes;
        try {
            bytes = entrada.readNBytes((int) Math.min(Integer.MAX_VALUE - 8L, limites.bytes() + 1));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (bytes.length > limites.bytes()) {
            throw new XmlRechazadoException("El XML pasa de " + limites.bytes() + " bytes.");
        }
        XMLStreamReader lector = lector(new ByteArrayInputStream(bytes), limites);
        try {
            while (lector.hasNext()) {
                lector.next();
            }
            lector.close();
        } catch (XMLStreamException e) {
            throw malFormado(e);
        }
        return bytes;
    }

    /** DOM, despues de comprobar el documento con los limites. */
    public static Document documento(InputStream entrada, LimitesXml limites) {
        byte[] bytes = comprobar(entrada, limites);
        try {
            return documentos().parse(new ByteArrayInputStream(bytes));
        } catch (SAXException e) {
            throw new XmlRechazadoException("El XML no se pudo leer: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Un DocumentBuilder endurecido. Lo que lee conviene pasarlo antes por {@link #comprobar}. */
    public static DocumentBuilder documentos() {
        DocumentBuilderFactory factoria = DocumentBuilderFactory.newInstance();
        factoria.setNamespaceAware(true);
        factoria.setXIncludeAware(false);
        factoria.setExpandEntityReferences(false);
        try {
            factoria.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factoria.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factoria.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factoria.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factoria.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factoria.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factoria.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            DocumentBuilder constructor = factoria.newDocumentBuilder();
            constructor.setEntityResolver((publico, sistema) -> {
                throw new SAXException(SIN_DTD);
            });
            return constructor;
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("El parser DOM no acepta el endurecimiento", e);
        }
    }

    /** Un SAXParser endurecido, con las mismas protecciones que DOM. */
    public static SAXParser sax() {
        SAXParserFactory factoria = SAXParserFactory.newInstance();
        factoria.setNamespaceAware(true);
        factoria.setXIncludeAware(false);
        try {
            factoria.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factoria.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factoria.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factoria.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factoria.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            SAXParser parser = factoria.newSAXParser();
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return parser;
        } catch (ParserConfigurationException | SAXNotRecognizedException | SAXNotSupportedException e) {
            throw new IllegalStateException("El parser SAX no acepta el endurecimiento", e);
        } catch (SAXException e) {
            throw new IllegalStateException("El parser SAX no se pudo crear", e);
        }
    }

    /** Transformadores sin hojas de estilo, DTD ni URIs externas. */
    public static TransformerFactory transformadores() {
        TransformerFactory factoria = TransformerFactory.newInstance();
        try {
            factoria.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (TransformerConfigurationException e) {
            throw new IllegalStateException("El transformador no acepta secure processing", e);
        }
        factoria.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factoria.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        factoria.setURIResolver((href, base) -> {
            throw new TransformerException("Las referencias externas no se aceptan: " + href);
        });
        return factoria;
    }

    /** El transformador identidad, para escribir un DOM. */
    public static Transformer transformador() {
        try {
            return transformadores().newTransformer();
        } catch (TransformerConfigurationException e) {
            throw new IllegalStateException("El transformador no se pudo crear", e);
        }
    }

    /** Esquemas XSD sin importar nada de fuera: los esquemas que se validen se cargan del classpath. */
    public static SchemaFactory esquemas() {
        SchemaFactory factoria = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try {
            factoria.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factoria.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factoria.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (SAXNotRecognizedException | SAXNotSupportedException e) {
            throw new IllegalStateException("La factoria de esquemas no acepta el endurecimiento", e);
        }
        return factoria;
    }

    /** Un validador del esquema que tampoco busca nada fuera. */
    public static Validator validador(Schema esquema) {
        Validator validador = esquema.newValidator();
        try {
            validador.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validador.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (SAXNotRecognizedException | SAXNotSupportedException e) {
            throw new IllegalStateException("El validador no acepta el endurecimiento", e);
        }
        return validador;
    }

    /**
     * PR 53: StAX para escribir. Escribir no lee nada de fuera, pero la factoria sale de aqui como todas, y asi la
     * regla de ArchUnit no tiene excepciones.
     */
    public static XMLStreamWriter escritor(OutputStream salida) {
        try {
            return XMLOutputFactory.newFactory().createXMLStreamWriter(salida, StandardCharsets.UTF_8.name());
        } catch (XMLStreamException e) {
            throw new IllegalStateException("El escritor XML no se pudo crear", e);
        }
    }

    /**
     * PR 53: un esquema del classpath con sus {@code import} e {@code include}, que se resuelven solo dentro de la
     * misma carpeta. De cada {@code schemaLocation} se mira solo el nombre del archivo; uno que no este en la carpeta
     * no se carga. Aunque un esquema pida algo de la red o del disco, no sale a buscarlo: {@code ACCESS_EXTERNAL_SCHEMA}
     * sigue vacio y lo que se lee llega ya abierto.
     *
     * @param carpeta la carpeta del classpath, sin barra al principio ni al final
     * @param principales los esquemas de los que se parte; los que estos importen se encuentran solos
     */
    public static Schema esquemaDelClasspath(String carpeta, String... principales) {
        SchemaFactory factoria = esquemas();
        factoria.setResourceResolver((tipo, espacio, publico, sistema, base) -> deLaCarpeta(carpeta, sistema));
        Source[] fuentes = new Source[principales.length];
        for (int i = 0; i < principales.length; i++) {
            fuentes[i] = new StreamSource(abrir(carpeta, principales[i]), carpeta + "/" + principales[i]);
        }
        try {
            return factoria.newSchema(fuentes);
        } catch (SAXException e) {
            throw new IllegalStateException("Los esquemas de " + carpeta + " no se pudieron cargar", e);
        }
    }

    private static LSInput deLaCarpeta(String carpeta, String sistema) {
        if (sistema == null) {
            return null;
        }
        String nombre = sistema.substring(Math.max(sistema.lastIndexOf('/'), sistema.lastIndexOf('\\')) + 1);
        return new EntradaDeEsquema(carpeta + "/" + nombre, abrir(carpeta, nombre));
    }

    private static InputStream abrir(String carpeta, String nombre) {
        if (!nombre.matches("[A-Za-z0-9_.-]+\\.xsd")) {
            throw new IllegalStateException("El esquema " + nombre + " no es un archivo de " + carpeta);
        }
        InputStream recurso = SecureXml.class.getClassLoader().getResourceAsStream(carpeta + "/" + nombre);
        if (recurso == null) {
            throw new IllegalStateException("El esquema " + nombre + " no esta en " + carpeta);
        }
        return recurso;
    }

    private static void comprobarCodificacion(String codificacion) {
        if (codificacion != null && !CODIFICACIONES.contains(codificacion.toUpperCase(Locale.ROOT))) {
            throw new XmlRechazadoException("El XML viene en " + codificacion + ": solo se acepta UTF-8 o UTF-16.");
        }
    }

    private static XmlRechazadoException malFormado(XMLStreamException e) {
        // Un limite que salto dentro del parser llega envuelto: se devuelve tal cual
        for (Throwable causa = e; causa != null; causa = causa.getCause()) {
            if (causa instanceof XmlRechazadoException rechazo) {
                return rechazo;
            }
        }
        String detalle = e.getMessage() != null && e.getMessage().contains("DOCTYPE") ? SIN_DTD : e.getMessage();
        return new XmlRechazadoException("El XML no se pudo leer: " + detalle, e);
    }

    /** Un esquema importado, ya abierto: el parser no tiene que ir a buscarlo a ninguna parte. */
    private static final class EntradaDeEsquema implements LSInput {

        private String sistema;
        private InputStream bytes;

        EntradaDeEsquema(String sistema, InputStream bytes) {
            this.sistema = sistema;
            this.bytes = bytes;
        }

        @Override
        public Reader getCharacterStream() {
            return null;
        }

        @Override
        public void setCharacterStream(Reader characterStream) {
            // Solo se entrega en bytes
        }

        @Override
        public InputStream getByteStream() {
            return bytes;
        }

        @Override
        public void setByteStream(InputStream byteStream) {
            this.bytes = byteStream;
        }

        @Override
        public String getStringData() {
            return null;
        }

        @Override
        public void setStringData(String stringData) {
            // Solo se entrega en bytes
        }

        @Override
        public String getSystemId() {
            return sistema;
        }

        @Override
        public void setSystemId(String systemId) {
            this.sistema = systemId;
        }

        @Override
        public String getPublicId() {
            return null;
        }

        @Override
        public void setPublicId(String publicId) {
            // Los esquemas se nombran por su archivo
        }

        @Override
        public String getBaseURI() {
            return null;
        }

        @Override
        public void setBaseURI(String baseURI) {
            // Cada esquema se resuelve en la misma carpeta
        }

        @Override
        public String getEncoding() {
            return null;
        }

        @Override
        public void setEncoding(String encoding) {
            // La declara cada esquema
        }

        @Override
        public boolean getCertifiedText() {
            return false;
        }

        @Override
        public void setCertifiedText(boolean certifiedText) {
            // No aplica a un esquema
        }
    }

    /** Cuenta los bytes que entran y corta al pasar el limite. */
    private static final class Contado extends FilterInputStream {

        private final long limite;
        private long leidos;

        Contado(InputStream entrada, long limite) {
            super(entrada);
            this.limite = limite;
        }

        @Override
        public int read() throws IOException {
            int byteLeido = super.read();
            if (byteLeido >= 0) {
                contar(1);
            }
            return byteLeido;
        }

        @Override
        public int read(byte[] destino, int desde, int largo) throws IOException {
            int cuantos = super.read(destino, desde, largo);
            if (cuantos > 0) {
                contar(cuantos);
            }
            return cuantos;
        }

        private void contar(int cuantos) {
            leidos += cuantos;
            if (leidos > limite) {
                throw new XmlRechazadoException("El XML pasa de " + limite + " bytes.");
            }
        }
    }

    /** El lector que aplica los limites evento a evento. */
    private static final class Limitado extends StreamReaderDelegate {

        private final LimitesXml limites;
        private final Instant vence;
        private int elementos;
        private int profundidad;

        Limitado(XMLStreamReader lector, LimitesXml limites) {
            super(lector);
            this.limites = limites;
            this.vence = Instant.now().plus(limites.tiempo());
        }

        @Override
        public int next() throws XMLStreamException {
            int evento = super.next();
            if (!Instant.now().isBefore(vence)) {
                throw new XmlRechazadoException("El XML tardo mas de " + limites.tiempo().toSeconds()
                        + " segundos en leerse.");
            }
            switch (evento) {
                case XMLStreamConstants.DTD, XMLStreamConstants.ENTITY_REFERENCE,
                        XMLStreamConstants.ENTITY_DECLARATION, XMLStreamConstants.NOTATION_DECLARATION ->
                    throw new XmlRechazadoException(SIN_DTD);
                case XMLStreamConstants.START_ELEMENT -> abrir();
                case XMLStreamConstants.END_ELEMENT -> profundidad--;
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE,
                        XMLStreamConstants.COMMENT -> medirTexto();
                default -> {
                    // Instrucciones de proceso y fin del documento: nada que contar
                }
            }
            return evento;
        }

        private void medirTexto() {
            if (getTextLength() > limites.texto()) {
                throw new XmlRechazadoException("Un texto del XML pasa de " + limites.texto() + " caracteres.");
            }
        }

        private void abrir() {
            if (++elementos > limites.elementos()) {
                throw new XmlRechazadoException("El XML pasa de " + limites.elementos() + " elementos.");
            }
            if (++profundidad > limites.profundidad()) {
                throw new XmlRechazadoException("El XML anida mas de " + limites.profundidad() + " niveles.");
            }
            int atributos = getAttributeCount();
            if (atributos > limites.atributosPorElemento()) {
                throw new XmlRechazadoException("Un elemento del XML pasa de " + limites.atributosPorElemento()
                        + " atributos.");
            }
            for (int i = 0; i < atributos; i++) {
                if (getAttributeValue(i).length() > limites.texto()) {
                    throw new XmlRechazadoException("Un atributo del XML pasa de " + limites.texto()
                            + " caracteres.");
                }
            }
        }
    }
}
