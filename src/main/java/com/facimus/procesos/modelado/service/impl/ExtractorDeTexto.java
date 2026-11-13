package com.facimus.procesos.modelado.service.impl;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.facimus.procesos.common.archivos.TipoDeArchivo;
import com.facimus.procesos.common.trabajos.TrabajoAplazadoException;

/**
 * PR 65: el texto de un documento, para buscarlo, con los parsers de Apache Tika de los tipos de la lista blanca que
 * tienen texto. Lo que llega es de quien lo subio, asi que se lee con desconfianza:
 * <ul>
 * <li>cada tipo va a su parser, elegido aqui por el tipo que ya se detecto al subirlo, y no por lo que Tika crea
 * ver;</li>
 * <li>lo que viene incrustado (un adjunto dentro de un PDF, una hoja dentro de un Word) no se abre;</li>
 * <li>un PDF no pasa por OCR;</li>
 * <li>el texto tiene tope: al llegar, se deja de leer y queda lo leido;</li>
 * <li>un PDF guarda en memoria, como mucho, {@link #MEMORIA_DE_UN_PDF} bytes de lo que descomprime; lo que pase va a
 * archivos de paso de PDFBox en la carpeta temporal de la JVM (Tika no deja elegir otra), que los borra al cerrar el
 * documento;</li>
 * <li>cada archivo tiene un tiempo, que cuenta desde que toma uno de los pocos lugares de lectura: si no termina, se
 * deja y se busca solo por su nombre. El hilo que lo leia se interrumpe; si el parser no lo atiende y el hilo no suelta
 * el archivo en una gracia, se da por colgado y su lugar se repone, hasta {@link #MAXIMO_COLGADOS} colgados a la vez:
 * un archivo hecho para colgar al parser ocupa un hilo, pero no deja sin lectura a los demas, y unos cuantos no se
 * llevan la maquina.</li>
 * </ul>
 * Un archivo que no se puede leer no es un error de nadie: el resultado dice por que, y la cola no lo reintenta. Que
 * ningun lugar quede libre en ese tiempo, en cambio, no dice nada del archivo: el trabajo se aplaza, y la cola lo
 * vuelve a intentar mas tarde sin gastarle un intento.
 * <p>PDFBox lee con las {@link FuentesFijas} del manual (PR 62): una fuente que el PDF no trae no lo pone a recorrer
 * las fuentes del equipo.
 */
@Component
public class ExtractorDeTexto implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ExtractorDeTexto.class);

    private static final int LARGO_DEL_MOTIVO = 300;

    /** Cuanto se espera, tras interrumpirlo, a que el hilo de un archivo vencido lo suelte. */
    static final Duration GRACIA_POR_OMISION = Duration.ofSeconds(5);

    /** Cuantos hilos colgados se reponen, como mucho; pasado eso, uno colgado ocupa su lugar hasta terminar. */
    static final int MAXIMO_COLGADOS = 8;

    private static final int LEYENDO = 0;
    private static final int TERMINADA = 1;
    private static final int COLGADA = 2;

    /** Cuanto de lo que descomprime un PDF se queda en memoria; el resto, en disco. */
    static final long MEMORIA_DE_UN_PDF = 32L * 1024 * 1024;

    private final Map<TipoDeArchivo, Parser> parsers;
    private final int maximo;
    private final Duration espera;
    private final Duration gracia;
    private final int maximoColgados;
    /** Los lugares de lectura: cuantos archivos se leen a la vez, sin contar los colgados que ya se repusieron. */
    private final Semaphore libres;
    private final AtomicInteger colgados = new AtomicInteger();
    private final ExecutorService hilos = Executors
            .newThreadPerTaskExecutor(Thread.ofPlatform().daemon().name("lectura-de-texto-", 0).factory());

    /**
     * @param maximo cuantos caracteres se guardan, como mucho, de un archivo
     * @param espera cuanto puede tardar un archivo
     * @param hilos cuantos archivos se leen a la vez
     */
    @Autowired
    public ExtractorDeTexto(@Value("${documentos.texto.caracteres:1000000}") int maximo,
            @Value("${documentos.texto.espera:30s}") Duration espera, @Value("${documentos.texto.hilos:2}") int hilos) {
        this(parsers(), maximo, espera, hilos);
    }

    ExtractorDeTexto(Map<TipoDeArchivo, Parser> parsers, int maximo, Duration espera, int hilos) {
        this(parsers, maximo, espera, hilos, GRACIA_POR_OMISION, MAXIMO_COLGADOS);
    }

    ExtractorDeTexto(Map<TipoDeArchivo, Parser> parsers, int maximo, Duration espera, int hilos, Duration gracia,
            int maximoColgados) {
        FuentesFijas.instalar();
        this.parsers = Map.copyOf(parsers);
        this.maximo = maximo;
        this.espera = espera;
        this.gracia = gracia;
        this.maximoColgados = maximoColgados;
        this.libres = new Semaphore(hilos, true);
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

    /**
     * El texto del archivo, que es del tipo dicho. El tiempo de leerlo empieza cuando toma un lugar, no al pedirlo: un
     * archivo que espera turno no pierde su tiempo detras de otro. Si en ese mismo tiempo no queda un lugar libre,
     * porque los ocupan archivos que no terminan, se aplaza sin decir nada del archivo.
     *
     * @throws HilosOcupadosException si no pudo empezar a leerlo
     */
    public Resultado extraer(Path archivo, TipoDeArchivo tipo) {
        Parser parser = parsers.get(tipo);
        if (parser == null) {
            return Resultado.sinTexto("Un archivo de tipo " + tipo.mime() + " no tiene texto que leer.");
        }
        try {
            if (!libres.tryAcquire(espera.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new HilosOcupadosException(espera);
            }
            return leerATiempo(archivo, parser);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió la lectura del texto de un documento.", e);
        }
    }

    /** Con un lugar ya tomado, que devuelve el hilo al terminar, o este al darlo por colgado. */
    private Resultado leerATiempo(Path archivo, Parser parser) throws InterruptedException {
        Lectura lectura = new Lectura();
        Future<Resultado> futuro;
        try {
            futuro = hilos.submit(() -> lectura.correr(archivo, parser));
        } catch (RejectedExecutionException e) {
            libres.release();
            throw e;
        }
        try {
            return futuro.get(espera.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException _) {
            lectura.vencer();
            return Resultado.sinTexto("Leerlo tardó más de " + espera.toSeconds() + " s.");
        } catch (ExecutionException e) {
            return Resultado.sinTexto(motivo(e.getCause()));
        } catch (InterruptedException e) {
            lectura.interrumpir();
            throw e;
        }
    }

    /** Cuantos hilos colgados se repusieron y siguen con su archivo. */
    int colgados() {
        return colgados.get();
    }

    /**
     * Un archivo en lectura: quien lo lee, y si termino o se dio por colgado. Lo que pase primero decide quien devuelve
     * el lugar: el hilo al terminar, o quien lo da por colgado, que lo repone; el colgado, al terminar, solo se
     * descuenta.
     */
    private final class Lectura {

        private final CountDownLatch terminada = new CountDownLatch(1);
        private final AtomicInteger estado = new AtomicInteger(LEYENDO);
        private final AtomicReference<Thread> hilo = new AtomicReference<>();

        Resultado correr(Path archivo, Parser parser) {
            hilo.set(Thread.currentThread());
            try {
                return leer(archivo, parser);
            } finally {
                if (estado.compareAndSet(LEYENDO, TERMINADA)) {
                    libres.release();
                } else {
                    colgados.decrementAndGet();
                }
                terminada.countDown();
            }
        }

        void interrumpir() {
            Thread leyendo = hilo.get();
            if (leyendo != null) {
                leyendo.interrupt();
            }
        }

        /** Se le acabo el tiempo: se interrumpe, y si no suelta el archivo en la gracia, se da por colgado. */
        void vencer() throws InterruptedException {
            interrumpir();
            if (terminada.await(gracia.toMillis(), TimeUnit.MILLISECONDS)) {
                return;
            }
            if (colgados.incrementAndGet() > maximoColgados) {
                colgados.decrementAndGet();
                log.warn("Un hilo de lectura no suelta un archivo vencido y ya hay {} colgados: ocupa su lugar hasta "
                        + "terminar", maximoColgados);
            } else if (estado.compareAndSet(LEYENDO, COLGADA)) {
                libres.release();
                log.warn("Un hilo de lectura no suelta un archivo vencido: se da por colgado y su lugar se repone");
            } else {
                colgados.decrementAndGet();
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
    }

    private static String motivo(Throwable causa) {
        String mensaje = causa.getMessage();
        return causa.getClass().getSimpleName() + (mensaje == null ? "" : ": " + mensaje);
    }

    @Override
    public void close() {
        hilos.shutdownNow();
    }

    /** Ningun lugar de lectura quedo libre a tiempo: el archivo no se leyo, y no por culpa suya. */
    public static final class HilosOcupadosException extends TrabajoAplazadoException {

        HilosOcupadosException(Duration espera) {
            super("Ningún hilo de lectura quedó libre en " + espera.toSeconds()
                    + " s: el texto del documento se lee en otro intento.");
        }
    }

    /** Los parsers de cada tipo con texto: PDF sin OCR, los de Office y OpenOffice, y el de texto plano. */
    static Map<TipoDeArchivo, Parser> parsers() {
        PagesConfig soloTexto = new PagesConfig();
        soloTexto.setText(TextPolicy.EXTRACT);
        PDFParserConfig configuracion = new PDFParserConfig();
        configuracion.setPages(soloTexto);
        configuracion.setExtractInlineImages(false);
        configuracion.setMaxMainMemoryBytes(MEMORIA_DE_UN_PDF);
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
