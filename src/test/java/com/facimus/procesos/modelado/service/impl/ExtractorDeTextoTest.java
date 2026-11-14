package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParser;
import org.apache.tika.parser.odf.OpenDocumentParser;
import org.apache.tika.parser.pages.TextPolicy;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.parser.txt.TXTParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import com.facimus.procesos.common.archivos.Archivos;
import com.facimus.procesos.common.archivos.ArchivosConTexto;
import com.facimus.procesos.common.archivos.TipoDeArchivo;
import com.facimus.procesos.common.trabajos.TrabajoAplazadoException;
import com.facimus.procesos.modelado.service.impl.ExtractorDeTexto.HilosOcupadosException;
import com.facimus.procesos.modelado.service.impl.ExtractorDeTexto.Resultado;

/** PR 65: el texto de cada tipo de documento, y cada tope que lo protege de un archivo hecho para danar. */
class ExtractorDeTextoTest {

    private static final String XHTML = "http://www.w3.org/1999/xhtml";
    private static final Duration ESPERA = Duration.ofSeconds(20);
    /** Cuanto espera turno un archivo, y cuanto tarda luego en leerse, en la prueba del tiempo de 2 s. */
    private static final Duration TURNO = Duration.ofMillis(1200);

    @TempDir
    private Path carpeta;

    private ExtractorDeTexto extractor;

    @BeforeAll
    static void fuentes() {
        // El PDF de prueba lo escribe PDFBox: con las fuentes del manual, sin recorrer las del equipo
        FuentesFijas.instalar();
    }

    @AfterEach
    void cerrar() {
        if (extractor != null) {
            extractor.close();
        }
    }

    private Path archivo(byte[] contenido) {
        try {
            return Files.write(Files.createTempFile(carpeta, "documento-", ".bin"), contenido);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Resultado leer(byte[] contenido, TipoDeArchivo tipo) {
        extractor = new ExtractorDeTexto(ExtractorDeTexto.parsers(), 10_000, ESPERA, 1);
        return extractor.extraer(archivo(contenido), tipo);
    }

    private Resultado conParser(Parser parser, int maximo, Duration espera) {
        Map<TipoDeArchivo, Parser> parsers = Map.of(TipoDeArchivo.TEXTO, parser);
        extractor = new ExtractorDeTexto(parsers, maximo, espera, 1);
        return extractor.extraer(archivo(new byte[] {1}), TipoDeArchivo.TEXTO);
    }

    @Test
    @DisplayName("de un PDF sale su texto, con tildes, sin pasar por OCR")
    void pdf() {
        Resultado resultado = leer(ArchivosConTexto.pdf("Política de crédito", "Aprobar el pedido"),
                TipoDeArchivo.PDF);

        assertThat(resultado.texto()).contains("Política de crédito").contains("Aprobar el pedido");
        assertThat(resultado.motivo()).isNull();
    }

    @Test
    @DisplayName("de un Word, un Excel, un texto y una hoja de OpenOffice sale su texto, separado por espacios")
    void office() {
        assertThat(leer(ArchivosConTexto.docx("Empacar el pedido", "Revisar la guía"), TipoDeArchivo.DOCX).texto())
                .contains("Empacar el pedido").contains("Revisar la guía");
        assertThat(leer(ArchivosConTexto.xlsx("Inventario", "Bodega norte"), TipoDeArchivo.XLSX).texto())
                .contains("Inventario").contains("Bodega norte");
        assertThat(leer(ArchivosConTexto.odt("Devolución", "en cinco días"), TipoDeArchivo.ODT).texto())
                .contains("Devolución").contains("en cinco días");
        assertThat(leer(ArchivosConTexto.ods("Proveedor", "Andina"), TipoDeArchivo.ODS).texto())
                .contains("Proveedor").contains("Andina");
    }

    @Test
    @DisplayName("el texto plano, el CSV y el Markdown se leen como texto, con sus tildes")
    void textoPlano() {
        byte[] texto = "Envolver con papel de regalo y cinta verde: crédito aprobado.\n".getBytes(StandardCharsets.UTF_8);

        assertThat(leer(texto, TipoDeArchivo.TEXTO).texto())
                .isEqualTo("Envolver con papel de regalo y cinta verde: crédito aprobado.");
        assertThat(leer("pedido;estado\n42;enviado\n".getBytes(StandardCharsets.UTF_8), TipoDeArchivo.CSV).texto())
                .contains("pedido;estado").contains("42;enviado");
        assertThat(leer("# Manual\n\nPaso **uno**".getBytes(StandardCharsets.UTF_8), TipoDeArchivo.MARKDOWN).texto())
                .contains("Manual").contains("uno");
    }

    @Test
    @DisplayName("cada tipo con texto tiene su parser, y PPTX va con el de Office; los demas no tienen")
    void parsers() {
        Map<TipoDeArchivo, Parser> parsers = ExtractorDeTexto.parsers();
        Set<TipoDeArchivo> conTexto = Arrays.stream(TipoDeArchivo.values()).filter(TipoDeArchivo::tieneTexto)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TipoDeArchivo.class)));

        assertThat(parsers.keySet()).isEqualTo(conTexto);
        assertThat(parsers.get(TipoDeArchivo.PDF)).isInstanceOfSatisfying(PDFParser.class,
                pdf -> {
                    assertThat(pdf.getPDFParserConfig().getPages().getText()).isEqualTo(TextPolicy.EXTRACT);
                    assertThat(pdf.getPDFParserConfig().isExtractInlineImages()).isFalse();
                    assertThat(pdf.getPDFParserConfig().getMaxMainMemoryBytes()).isEqualTo(32L * 1024 * 1024);
                });
        assertThat(parsers.get(TipoDeArchivo.DOCX)).isInstanceOf(OOXMLParser.class);
        assertThat(parsers.get(TipoDeArchivo.XLSX)).isInstanceOf(OOXMLParser.class);
        assertThat(parsers.get(TipoDeArchivo.PPTX)).isInstanceOf(OOXMLParser.class);
        assertThat(parsers.get(TipoDeArchivo.ODT)).isInstanceOf(OpenDocumentParser.class);
        assertThat(parsers.get(TipoDeArchivo.ODS)).isInstanceOf(OpenDocumentParser.class);
        assertThat(parsers.get(TipoDeArchivo.TEXTO)).isInstanceOf(TXTParser.class);
        assertThat(parsers.get(TipoDeArchivo.CSV)).isInstanceOf(TXTParser.class);
        assertThat(parsers.get(TipoDeArchivo.MARKDOWN)).isInstanceOf(TXTParser.class);
    }

    @Test
    @DisplayName("el tope de la configuracion se cuenta en caracteres")
    void configuracion() {
        extractor = new ExtractorDeTexto(5, ESPERA, 1);

        assertThat(extractor.extraer(archivo("abcdefgh".getBytes(StandardCharsets.UTF_8)), TipoDeArchivo.TEXTO))
                .isEqualTo(new Resultado("abcde", "El texto pasa de 5 caracteres: se guardan los primeros."));
    }

    @Test
    @DisplayName("una imagen, un SVG o un BPMN no se leen: no tienen texto que buscar")
    void sinTexto() {
        assertThat(leer(Archivos.PNG, TipoDeArchivo.PNG))
                .isEqualTo(new Resultado(null, "Un archivo de tipo image/png no tiene texto que leer."));
        assertThat(leer(Archivos.SVG_CON_SCRIPT, TipoDeArchivo.SVG).texto()).isNull();
        assertThat(leer(Archivos.BPMN, TipoDeArchivo.BPMN).texto()).isNull();
    }

    @Test
    @DisplayName("un PDF roto no se lee y dice por que; uno sin texto tampoco")
    void rotos() {
        Resultado roto = leer("%PDF-1.4\nesto no es un PDF".getBytes(StandardCharsets.US_ASCII), TipoDeArchivo.PDF);
        Resultado vacio = leer(" \n\t \n".getBytes(StandardCharsets.UTF_8), TipoDeArchivo.TEXTO);

        assertThat(roto.texto()).isNull();
        assertThat(roto.motivo()).isNotBlank();
        assertThat(vacio).isEqualTo(new Resultado(null, "El archivo no tiene texto que leer."));
    }

    @Test
    @DisplayName("los espacios, saltos y caracteres de control quedan en un solo espacio, sin el caracter nulo")
    void espacios() {
        Resultado resultado = conParser(escribe("  uno \n\t dos\u0000tres cuatro\u0007cinco  "), 100, ESPERA);

        assertThat(resultado).isEqualTo(new Resultado("uno dos tres cuatro cinco", null));
    }

    @Test
    @DisplayName("entre dos elementos, y donde el parser dice espacio ignorable, va un espacio")
    void entreElementos() {
        Parser parser = new Falso() {
            @Override
            public void parse(TikaInputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                    throws SAXException {
                abrir(handler);
                handler.startElement(XHTML, "td", "td", new AttributesImpl());
                caracteres(handler, "uno");
                handler.endElement(XHTML, "td", "td");
                caracteres(handler, "dos");
                handler.ignorableWhitespace(new char[] {' '}, 0, 1);
                caracteres(handler, "tres");
                cerrar(handler);
            }
        };

        assertThat(conParser(parser, 100, ESPERA).texto()).isEqualTo("uno dos tres");
    }

    @Test
    @DisplayName("hasta el tope se guarda entero; uno mas y se corta ahi, se deja de leer y lo dice")
    void tope() {
        assertThat(conParser(escribe("0123456789"), 10, ESPERA)).isEqualTo(new Resultado("0123456789", null));

        AtomicBoolean siguio = new AtomicBoolean();
        Parser largo = new Falso() {
            @Override
            public void parse(TikaInputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                    throws SAXException {
                abrir(handler);
                caracteres(handler, "0123456789AB");
                siguio.set(true);
                cerrar(handler);
            }
        };
        Resultado cortado = conParser(largo, 10, ESPERA);

        assertThat(cortado).isEqualTo(new Resultado("0123456789",
                "El texto pasa de 10 caracteres: se guardan los primeros."));
        assertThat(siguio).isFalse();
    }

    @Test
    @DisplayName("el tope cuenta los espacios que se agregan, y no parte en dos un caracter de dos mitades")
    void topeConEspaciosYEmojis() {
        assertThat(conParser(escribe("abc   defg"), 5, ESPERA).texto()).isEqualTo("abc d");
        assertThat(conParser(escribe("abcd😀"), 5, ESPERA).texto()).isEqualTo("abcd");
    }

    @Test
    @DisplayName("si el parser se rompe al llegar al tope, igual queda lo leido")
    void topeEnvuelto() {
        Parser envuelve = new Falso() {
            @Override
            public void parse(TikaInputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                    throws org.apache.tika.exception.TikaException {
                try {
                    abrir(handler);
                    caracteres(handler, "0123456789AB");
                } catch (SAXException e) {
                    throw new org.apache.tika.exception.TikaException("envuelto", e);
                }
            }
        };

        assertThat(conParser(envuelve, 10, ESPERA).texto()).isEqualTo("0123456789");
    }

    @Test
    @DisplayName("un parser que falla, con una excepcion o un error, deja el archivo sin texto y con el motivo")
    void fallas() {
        Parser revienta = new Falso() {
            @Override
            public void parse(TikaInputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) {
                throw new IllegalStateException("x".repeat(500));
            }
        };
        Parser pila = new Falso() {
            @Override
            public void parse(TikaInputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) {
                throw new StackOverflowError();
            }
        };

        Resultado excepcion = conParser(revienta, 100, ESPERA);
        extractor.close();
        Resultado error = conParser(pila, 100, ESPERA);

        assertThat(excepcion.texto()).isNull();
        assertThat(excepcion.motivo()).hasSize(300).startsWith("IllegalStateException: xxx");
        assertThat(error).isEqualTo(new Resultado(null, "StackOverflowError"));
    }

    @Test
    @DisplayName("un archivo que no termina a tiempo se deja sin texto, y el hilo que lo leia se interrumpe")
    void tiempo() throws InterruptedException {
        CountDownLatch soltar = new CountDownLatch(1);
        CountDownLatch interrumpido = new CountDownLatch(1);
        Parser lento = new Falso() {
            @Override
            public void parse(TikaInputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) {
                try {
                    soltar.await();
                } catch (InterruptedException _) {
                    interrumpido.countDown();
                }
            }
        };

        try {
            Resultado resultado = conParser(lento, 100, Duration.ofSeconds(1));

            assertThat(resultado).isEqualTo(new Resultado(null, "Leerlo tardó más de 1 s."));
            assertThat(interrumpido.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            soltar.countDown();
        }
    }

    @Test
    @DisplayName("el tiempo cuenta desde que un hilo lo empieza a leer, no mientras espera turno detras de otro")
    void tiempoDesdeQueEmpieza() throws Exception {
        CountDownLatch empezoElPrimero = new CountDownLatch(1);
        CountDownLatch soltarElPrimero = new CountDownLatch(1);
        CountDownLatch empezoElSegundo = new CountDownLatch(1);
        CountDownLatch soltarElSegundo = new CountDownLatch(1);
        Parser primero = leeAlSoltarlo("uno", empezoElPrimero, soltarElPrimero);
        Parser segundo = leeAlSoltarlo("dos", empezoElSegundo, soltarElSegundo);
        extractor = new ExtractorDeTexto(Map.of(TipoDeArchivo.TEXTO, primero, TipoDeArchivo.CSV, segundo), 100,
                Duration.ofSeconds(2), 1);
        Path uno = archivo(new byte[] {1});
        Path dos = archivo(new byte[] {2});
        CompletableFuture<Resultado> antes = new CompletableFuture<>();
        CompletableFuture<Resultado> despues = new CompletableFuture<>();
        try {
            Thread.ofPlatform().start(() -> antes.complete(extractor.extraer(uno, TipoDeArchivo.TEXTO)));
            assertThat(empezoElPrimero.await(10, TimeUnit.SECONDS)).isTrue();
            Thread.ofPlatform().start(() -> despues.complete(extractor.extraer(dos, TipoDeArchivo.CSV)));

            // Espera su turno 1,2 s y se lee en otros 1,2: pasa de 2 s desde que se pidio, pero no desde que empezo
            await().during(TURNO).atMost(TURNO.multipliedBy(5)).until(() -> !despues.isDone());
            soltarElPrimero.countDown();
            assertThat(empezoElSegundo.await(10, TimeUnit.SECONDS)).isTrue();
            await().during(TURNO).atMost(TURNO.multipliedBy(5)).until(() -> !despues.isDone());
            soltarElSegundo.countDown();

            assertThat(despues.get(10, TimeUnit.SECONDS)).isEqualTo(new Resultado("dos", null));
            assertThat(antes.get(10, TimeUnit.SECONDS)).isEqualTo(new Resultado("uno", null));
        } finally {
            soltarElPrimero.countDown();
            soltarElSegundo.countDown();
        }
    }

    @Test
    @DisplayName("un hilo que no suelta su archivo se da por colgado y su lugar se repone, hasta el tope de colgados")
    void colgados() {
        CountDownLatch soltar = new CountDownLatch(1);
        Parser terco = new Falso() {
            @Override
            public void parse(TikaInputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) {
                boolean suelto = false;
                while (!suelto) {
                    try {
                        suelto = soltar.await(1, TimeUnit.MINUTES);
                    } catch (InterruptedException _) {
                        // No atiende la interrupcion: sigue hasta que lo suelten
                    }
                }
            }
        };
        extractor = new ExtractorDeTexto(Map.of(TipoDeArchivo.TEXTO, terco, TipoDeArchivo.CSV, escribe("hola")), 100,
                Duration.ofSeconds(1), 1, Duration.ofMillis(200), 1);
        Path colgado = archivo(new byte[] {1});
        Path otroColgado = archivo(new byte[] {2});
        Path siguiente = archivo(new byte[] {3});
        Resultado vencido = new Resultado(null, "Leerlo tardó más de 1 s.");
        try {
            assertThat(extractor.extraer(colgado, TipoDeArchivo.TEXTO)).isEqualTo(vencido);
            assertThat(extractor.colgados()).isEqualTo(1);
            assertThat(extractor.extraer(siguiente, TipoDeArchivo.CSV)).as("el lugar del colgado se repuso")
                    .isEqualTo(new Resultado("hola", null));

            // Con el tope de colgados lleno, el siguiente colgado ocupa su lugar y el que sigue se aplaza
            assertThat(extractor.extraer(otroColgado, TipoDeArchivo.TEXTO)).isEqualTo(vencido);
            assertThat(extractor.colgados()).isEqualTo(1);
            assertThatThrownBy(() -> extractor.extraer(siguiente, TipoDeArchivo.CSV))
                    .isInstanceOf(HilosOcupadosException.class).isInstanceOf(TrabajoAplazadoException.class)
                    .hasMessage(
                            "Ningún hilo de lectura quedó libre en 1 s: el texto del documento se lee en otro intento.");

            // Al terminar, los colgados se descuentan y su lugar vuelve una sola vez
            soltar.countDown();
            await().atMost(Duration.ofSeconds(10)).until(() -> extractor.colgados() == 0);
            assertThat(extractor.extraer(siguiente, TipoDeArchivo.CSV)).isEqualTo(new Resultado("hola", null));
        } finally {
            soltar.countDown();
        }
    }

    @Test
    @DisplayName("un hilo que suelta su archivo en la gracia no cuenta como colgado")
    void sueltaEnLaGracia() {
        CountDownLatch soltar = new CountDownLatch(1);
        Parser tardio = new Falso() {
            @Override
            public void parse(TikaInputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) {
                boolean suelto = false;
                while (!suelto) {
                    try {
                        suelto = soltar.await(1, TimeUnit.MINUTES);
                    } catch (InterruptedException _) {
                        // Atiende la interrupcion un poco despues
                        suelto = true;
                    }
                }
            }
        };
        extractor = new ExtractorDeTexto(Map.of(TipoDeArchivo.TEXTO, tardio, TipoDeArchivo.CSV, escribe("hola")), 100,
                Duration.ofSeconds(1), 1, Duration.ofSeconds(5), 1);
        try {
            assertThat(extractor.extraer(archivo(new byte[] {1}), TipoDeArchivo.TEXTO))
                    .isEqualTo(new Resultado(null, "Leerlo tardó más de 1 s."));
            assertThat(extractor.colgados()).isZero();
            assertThat(extractor.extraer(archivo(new byte[] {2}), TipoDeArchivo.CSV))
                    .isEqualTo(new Resultado("hola", null));
        } finally {
            soltar.countDown();
        }
    }

    @Test
    @DisplayName("si interrumpen a quien espera, no se da por leido: falla para que la cola lo intente otra vez")
    void interrumpido() {
        CountDownLatch soltar = new CountDownLatch(1);
        Parser lento = new Falso() {
            @Override
            public void parse(TikaInputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) {
                try {
                    soltar.await();
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        extractor = new ExtractorDeTexto(Map.of(TipoDeArchivo.TEXTO, lento), 100, ESPERA, 1);
        Path documento = archivo(new byte[] {1});

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> extractor.extraer(documento, TipoDeArchivo.TEXTO))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Se interrumpió la lectura del texto de un documento.");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
            soltar.countDown();
        }
    }

    @Test
    @DisplayName("lo incrustado en un documento no se abre")
    void sinIncrustados() {
        Parser mira = new Falso() {
            @Override
            public void parse(TikaInputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                    throws SAXException, IOException {
                EmbeddedDocumentExtractor incrustados = context.get(EmbeddedDocumentExtractor.class);
                abrir(handler);
                caracteres(handler, incrustados.shouldParseEmbedded(new Metadata(), context) ? "abre" : "cerrado");
                incrustados.parseEmbedded(stream, handler, new Metadata(), context, true);
                cerrar(handler);
            }
        };

        assertThat(conParser(mira, 100, ESPERA).texto()).isEqualTo("cerrado");
    }

    @Test
    @DisplayName("cerrado, ya no lee nada")
    void cerrado() {
        extractor = new ExtractorDeTexto(Map.of(TipoDeArchivo.TEXTO, escribe("hola")), 100, ESPERA, 1);
        Path documento = archivo(new byte[] {1});

        extractor.close();

        assertThatThrownBy(() -> extractor.extraer(documento, TipoDeArchivo.TEXTO))
                .isInstanceOf(RejectedExecutionException.class);
    }

    /** Un parser que avisa que empezo, sigue leyendo hasta que la prueba lo suelta y escribe su texto. */
    private static Parser leeAlSoltarlo(String texto, CountDownLatch empezado, CountDownLatch soltar) {
        return new Falso() {
            @Override
            public void parse(TikaInputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                    throws SAXException {
                empezado.countDown();
                try {
                    soltar.await();
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                    return;
                }
                abrir(handler);
                caracteres(handler, texto);
                cerrar(handler);
            }
        };
    }

    /** Un parser que escribe un texto dentro del cuerpo de un XHTML, como los de Tika. */
    private static Parser escribe(String texto) {
        return new Falso() {
            @Override
            public void parse(TikaInputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                    throws SAXException {
                abrir(handler);
                caracteres(handler, texto);
                cerrar(handler);
            }
        };
    }

    private abstract static class Falso implements Parser {

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return Set.of(MediaType.TEXT_PLAIN);
        }

        static void abrir(ContentHandler handler) throws SAXException {
            handler.startDocument();
            handler.startElement(XHTML, "html", "html", new AttributesImpl());
            handler.startElement(XHTML, "body", "body", new AttributesImpl());
        }

        static void caracteres(ContentHandler handler, String texto) throws SAXException {
            handler.characters(texto.toCharArray(), 0, texto.length());
        }

        static void cerrar(ContentHandler handler) throws SAXException {
            handler.endElement(XHTML, "body", "body");
            handler.endElement(XHTML, "html", "html");
            handler.endDocument();
        }
    }
}
