package com.facimus.procesos.modelado.service.impl;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParser;
import org.apache.tika.parser.odf.OpenDocumentParser;
import org.apache.tika.parser.pages.PagesConfig;
import org.apache.tika.parser.pages.TextPolicy;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.parser.txt.TXTParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.facimus.procesos.common.archivos.TipoDeArchivo;

/**
 * PR 65: el texto de un documento, para buscarlo, con los parsers de Apache Tika de los tipos de la lista blanca que
 * tienen texto. Lo que llega es de quien lo subio, asi que se lee con desconfianza:
 * <ul>
 * <li>cada tipo va a su parser, elegido aqui por el tipo que ya se detecto al subirlo, y no por lo que Tika crea
 * ver;</li>
 * <li>lo que viene incrustado (un adjunto dentro de un PDF, una hoja dentro de un Word) no se abre;</li>
 * <li>un PDF no pasa por OCR;</li>
 * <li>el texto tiene tope: al llegar, se deja de leer y queda lo leido;</li>
 * <li>cada archivo tiene un tiempo: si no termina, se deja y se busca solo por su nombre. El hilo que lo leia se
 * interrumpe; si el parser no lo atiende, sigue ocupado hasta terminar, y por eso los hilos son pocos y fijos: un
 * archivo hecho para colgar al parser ocupa uno, no la maquina.</li>
 * </ul>
 * Un archivo que no se puede leer no es un error de nadie: el resultado dice por que, y la cola no lo reintenta.
 * <p>PDFBox lee con las {@link FuentesFijas} del manual (PR 62): una fuente que el PDF no trae no lo pone a recorrer
 * las fuentes del equipo.
 */
@Component
public class ExtractorDeTexto implements AutoCloseable {

    private static final int LARGO_DEL_MOTIVO = 300;

    private final Map<TipoDeArchivo, Parser> parsers;
    private final int maximo;
    private final Duration espera;
    private final ExecutorService hilos;

    /**
     * @param maximo cuantos caracteres se guardan, como mucho, de un archivo
     * @param espera cuanto puede tardar un archivo
     * @param hilos cuantos archivos se leen a la vez
     */
    @Autowired
    public ExtractorDeTexto(@Value("${documentos.texto.maximo:1MB}") DataSize maximo,
            @Value("${documentos.texto.espera:30s}") Duration espera, @Value("${documentos.texto.hilos:2}") int hilos) {
        this(parsers(), (int) Math.min(maximo.toBytes(), Integer.MAX_VALUE), espera, hilos);
    }

    ExtractorDeTexto(Map<TipoDeArchivo, Parser> parsers, int maximo, Duration espera, int hilos) {
        FuentesFijas.instalar();
        this.parsers = Map.copyOf(parsers);
        this.maximo = maximo;
        this.espera = espera;
        this.hilos = Executors.newFixedThreadPool(hilos,
                Thread.ofPlatform().daemon().name("lectura-de-texto-", 0).factory());
    }

    /** Lo que se leyo de un archivo; sin texto, el motivo. Con texto y motivo, se leyo hasta el tope. */
    public record Resultado(String texto, String motivo) {

        static Resultado leido(String texto) {
            return new Resultado(texto, null);
        }

        static Resultado sinTexto(String motivo) {
            return new Resultado(null, motivo.length() > LARGO_DEL_MOTIVO ? motivo.substring(0, LARGO_DEL_MOTIVO)
                    : motivo);
        }
    }

    /** El texto del archivo, que es del tipo dicho. */
    public Resultado extraer(Path archivo, TipoDeArchivo tipo) {
        Parser parser = parsers.get(tipo);
        if (parser == null) {
            return Resultado.sinTexto("Un archivo de tipo " + tipo.mime() + " no tiene texto que leer.");
        }
        Future<Resultado> lectura = hilos.submit(() -> leer(archivo, parser));
        try {
            return lectura.get(espera.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException _) {
            lectura.cancel(true);
            return Resultado.sinTexto("Leerlo tardó más de " + espera.toSeconds() + " s.");
        } catch (ExecutionException e) {
            return Resultado.sinTexto(motivo(e.getCause()));
        } catch (InterruptedException e) {
            lectura.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió la lectura del texto de un documento.", e);
        }
    }

    private Resultado leer(Path archivo, Parser parser) {
        TextoAcotado texto = new TextoAcotado(maximo);
        ParseContext contexto = new ParseContext();
        contexto.set(EmbeddedDocumentExtractor.class, new SinIncrustados());
        try (TikaInputStream entrada = TikaInputStream.get(archivo)) {
            parser.parse(entrada, new BodyContentHandler(texto), new Metadata(), contexto);
        } catch (IOException | SAXException | TikaException e) {
            // Llegar al tope corta el analisis con una excepcion, que algunos parsers envuelven en la suya
            if (!texto.lleno()) {
                return Resultado.sinTexto(motivo(e));
            }
        }
        String leido = texto.texto();
        if (leido.isEmpty()) {
            return Resultado.sinTexto("El archivo no tiene texto que leer.");
        }
        return texto.lleno() ? new Resultado(leido, "El texto pasa de " + maximo + " caracteres: se guardan los primeros.")
                : Resultado.leido(leido);
    }

    private static String motivo(Throwable causa) {
        String mensaje = causa.getMessage();
        return causa.getClass().getSimpleName() + (mensaje == null ? "" : ": " + mensaje);
    }

    @Override
    public void close() {
        hilos.shutdownNow();
    }

    /** Los parsers de cada tipo con texto: PDF sin OCR, los de Office y OpenOffice, y el de texto plano. */
    static Map<TipoDeArchivo, Parser> parsers() {
        PagesConfig soloTexto = new PagesConfig();
        soloTexto.setText(TextPolicy.EXTRACT);
        PDFParserConfig configuracion = new PDFParserConfig();
        configuracion.setPages(soloTexto);
        configuracion.setExtractInlineImages(false);
        Parser office = new OOXMLParser();
        Parser openOffice = new OpenDocumentParser();
        Parser textoPlano = new TXTParser();
        Map<TipoDeArchivo, Parser> porTipo = new EnumMap<>(TipoDeArchivo.class);
        porTipo.put(TipoDeArchivo.PDF, new PDFParser(configuracion));
        porTipo.put(TipoDeArchivo.DOCX, office);
        porTipo.put(TipoDeArchivo.XLSX, office);
        porTipo.put(TipoDeArchivo.PPTX, office);
        porTipo.put(TipoDeArchivo.ODT, openOffice);
        porTipo.put(TipoDeArchivo.ODS, openOffice);
        porTipo.put(TipoDeArchivo.TEXTO, textoPlano);
        porTipo.put(TipoDeArchivo.CSV, textoPlano);
        porTipo.put(TipoDeArchivo.MARKDOWN, textoPlano);
        return porTipo;
    }

    /** Lo incrustado en un documento no se lee: ni se descomprime ni pasa por otro parser. */
    private static final class SinIncrustados implements EmbeddedDocumentExtractor {

        @Override
        public boolean shouldParseEmbedded(Metadata metadata, ParseContext context) {
            return false;
        }

        @Override
        public void parseEmbedded(TikaInputStream stream, ContentHandler handler, Metadata metadata,
                ParseContext context, boolean outputHtml) {
            // Nunca llega: shouldParseEmbedded siempre dice que no
        }
    }

    /**
     * El texto que va saliendo del parser, con cada tramo de espacios, saltos y caracteres de control hecho un espacio
     * (PostgreSQL no guarda un caracter nulo), hasta el tope. Al llegar corta el analisis: lo que falta no se lee.
     */
    static final class TextoAcotado extends DefaultHandler {

        private final StringBuilder texto = new StringBuilder();
        private final int maximo;
        private boolean espacio;
        private boolean lleno;

        TextoAcotado(int maximo) {
            this.maximo = maximo;
        }

        @Override
        public void characters(char[] caracteres, int desde, int largo) throws SAXException {
            for (int i = desde; i < desde + largo; i++) {
                agregar(caracteres[i]);
            }
        }

        @Override
        public void ignorableWhitespace(char[] caracteres, int desde, int largo) {
            espacio = true;
        }

        /** Entre dos parrafos, o dos celdas, va un espacio aunque el documento no lo escriba. */
        @Override
        public void endElement(String uri, String nombreLocal, String nombre) {
            espacio = true;
        }

        private void agregar(char caracter) throws SAXException {
            if (Character.isWhitespace(caracter) || Character.isSpaceChar(caracter)
                    || Character.isISOControl(caracter)) {
                espacio = true;
                return;
            }
            if (espacio && !texto.isEmpty()) {
                anotar(' ');
            }
            espacio = false;
            anotar(caracter);
        }

        private void anotar(char caracter) throws SAXException {
            if (texto.length() >= maximo) {
                lleno = true;
                throw new SAXException("El texto llego a su tope.");
            }
            texto.append(caracter);
        }

        boolean lleno() {
            return lleno;
        }

        /** Lo leido, sin la mitad de un caracter que el tope haya partido en dos. */
        String texto() {
            int largo = texto.length();
            if (largo > 0 && Character.isHighSurrogate(texto.charAt(largo - 1))) {
                largo--;
            }
            return texto.substring(0, largo).strip();
        }
    }
}
