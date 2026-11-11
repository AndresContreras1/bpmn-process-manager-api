package com.facimus.procesos.modelado.bpmn;

import static org.assertj.core.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.xml.LimitesXml;
import com.facimus.procesos.common.xml.XmlRechazadoException;

/**
 * PR 54: el importador contra archivos rotos a proposito. Parte del corpus dorado, le aplica de una a cuatro
 * mutaciones (bytes cambiados, trozos borrados o repetidos, etiquetas, entidades, un DOCTYPE, anidamiento profundo,
 * referencias que no existen...) y le pide a la tuberia entera que responda una de dos cosas: un XML rechazado
 * (400) o una regla rota (409). Cualquier otra excepcion, o tardar mas de lo que permite el limite de tiempo, es un
 * fallo, y la entrada que lo provoco queda en {@code target/fuzz} para reproducirlo.
 *
 * <p>No usa Jazzer: es un generador propio con semilla, sin agente de instrumentacion, que corre igual en cada build
 * de la suite. En el build van pocas vueltas con una semilla fija; de noche el CI corre muchas mas con la semilla del
 * dia ({@code -Dfuzz.iteraciones}, {@code -Dfuzz.semilla}).
 */
class FuzzImportadorBpmnTest {

    private static final String[] CORPUS = {"miwg/A.1.0.bpmn", "miwg/A.2.0.bpmn", "miwg/A.2.1.bpmn",
        "miwg/A.3.0.bpmn", "miwg/A.4.0.bpmn", "miwg/A.4.1.bpmn", "herramientas/bpmn-io.bpmn", "herramientas/bpmn-io-descriptivo.bpmn",
        "herramientas/camunda-modeler.bpmn"};

    private static final String[] PIEZAS = {"<", ">", "/>", "</bpmn:task>", "<bpmn:task id=\"x\">",
        "<bpmn:sequenceFlow id=\"f\" sourceRef=\"nadie\" targetRef=\"nadie\"/>", "&amp;", "&#0;", "&#x1F600;",
        "\u0000", "]]>", "<![CDATA[", "<!DOCTYPE x [<!ENTITY e \"e\">]>", "&e;", "<?pi x?>", "<!-- -->",
        "id=\"\"", " id=\"dup\"", "xmlns:bpmn=\"urn:otro\"", "isHorizontal=\"false\"", "default=\"nada\"",
        "<bpmn:laneSet><bpmn:lane id=\"L\"><bpmn:childLaneSet/></bpmn:lane></bpmn:laneSet>",
        "<bpmn:participant id=\"P9\" processRef=\"P9\"/>", "<bpmn:messageFlow id=\"m9\" sourceRef=\"P9\" targetRef=\"P9\"/>",
        "<dc:Bounds x=\"NaN\" y=\"-1e308\" width=\"1e308\" height=\"-0\"/>", "﻿", "\uD800",
        "<bpm:mensaje siFalla=\"MANEJAR_ERROR\" manejoError=\"x\" respuesta=\"x\"/>"};

    @Test
    @DisplayName("Ningun archivo roto rompe el importador: o es un XML rechazado o una regla rota")
    void archivosRotos() throws Exception {
        int iteraciones = Integer.getInteger("fuzz.iteraciones", 400);
        long semilla = Long.getLong("fuzz.semilla", 54L);
        Random azar = new Random(semilla);
        List<byte[]> corpus = new ArrayList<>();
        for (String archivo : CORPUS) {
            try (InputStream entrada = getClass().getResourceAsStream("/bpmn/" + archivo)) {
                corpus.add(entrada.readAllBytes());
            }
        }
        ExecutorService hilo = Executors.newSingleThreadExecutor();
        try {
            for (int vuelta = 0; vuelta < iteraciones; vuelta++) {
                byte[] entrada = mutar(corpus.get(azar.nextInt(corpus.size())), azar);
                probar(entrada, hilo, semilla, vuelta);
            }
        } finally {
            hilo.shutdownNow();
        }
    }

    private static void probar(byte[] entrada, ExecutorService hilo, long semilla, int vuelta) throws Exception {
        Future<Throwable> resultado = hilo.submit(() -> {
            try {
                EntradaBpmn.Leida leida = EntradaBpmn.leer(new ByteArrayInputStream(entrada), LimitesXml.BPMN);
                ConversionBpmn.convertir(LectorBpmn.leer(leida.bytes()), EsquemaBpmn.problemas(leida.bytes()),
                        leida.codificacion());
                return null;
            } catch (XmlRechazadoException | ReglaNegocioException esperado) {
                return null;
            } catch (Throwable inesperado) {
                return inesperado;
            }
        });
        Throwable problema;
        try {
            // El limite de tiempo de LimitesXml.BPMN es de 10 s; con margen para una maquina lenta del CI
            problema = resultado.get(Duration.ofSeconds(30).toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException colgado) {
            resultado.cancel(true);
            guardar(entrada, semilla, vuelta);
            fail("La vuelta " + vuelta + " (semilla " + semilla + ") paso de 30 s");
            return;
        }
        if (problema != null) {
            Path archivo = guardar(entrada, semilla, vuelta);
            throw new AssertionError("La vuelta " + vuelta + " (semilla " + semilla + ") rompio el importador con "
                    + problema + "; la entrada esta en " + archivo, problema);
        }
    }

    /** De una a cuatro mutaciones sobre una copia del archivo. */
    private static byte[] mutar(byte[] original, Random azar) {
        byte[] actual = original;
        int cuantas = 1 + azar.nextInt(4);
        for (int i = 0; i < cuantas; i++) {
            actual = switch (azar.nextInt(7)) {
                case 0 -> cambiarUnByte(actual, azar);
                case 1 -> borrarUnTrozo(actual, azar);
                case 2 -> repetirUnTrozo(actual, azar);
                case 3 -> insertar(actual, azar, PIEZAS[azar.nextInt(PIEZAS.length)]);
                case 4 -> insertar(actual, azar, "<a>".repeat(1 + azar.nextInt(200)));
                case 5 -> reemplazarUnValor(actual, azar);
                default -> intercambiarTrozos(actual, azar);
            };
        }
        return actual;
    }

    private static byte[] cambiarUnByte(byte[] bytes, Random azar) {
        byte[] copia = bytes.clone();
        if (copia.length > 0) {
            copia[azar.nextInt(copia.length)] = (byte) azar.nextInt(256);
        }
        return copia;
    }

    private static byte[] borrarUnTrozo(byte[] bytes, Random azar) {
        if (bytes.length < 2) {
            return bytes;
        }
        int desde = azar.nextInt(bytes.length);
        int largo = 1 + azar.nextInt(Math.min(200, bytes.length - desde));
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.write(bytes, 0, desde);
        salida.write(bytes, desde + largo, bytes.length - desde - largo);
        return salida.toByteArray();
    }

    private static byte[] repetirUnTrozo(byte[] bytes, Random azar) {
        if (bytes.length < 2) {
            return bytes;
        }
        int desde = azar.nextInt(bytes.length);
        int largo = 1 + azar.nextInt(Math.min(400, bytes.length - desde));
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.write(bytes, 0, desde + largo);
        salida.write(bytes, desde, largo);
        salida.write(bytes, desde + largo, bytes.length - desde - largo);
        return salida.toByteArray();
    }

    private static byte[] insertar(byte[] bytes, Random azar, String pieza) {
        int donde = bytes.length == 0 ? 0 : azar.nextInt(bytes.length);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.write(bytes, 0, donde);
        salida.writeBytes(pieza.getBytes(StandardCharsets.UTF_8));
        salida.write(bytes, donde, bytes.length - donde);
        return salida.toByteArray();
    }

    /** Un valor de atributo cualquiera pasa a ser otro: ids que chocan, referencias que no existen, numeros raros. */
    private static byte[] reemplazarUnValor(byte[] bytes, Random azar) {
        String texto = new String(bytes, StandardCharsets.UTF_8);
        int comilla = texto.indexOf('"', azar.nextInt(Math.max(1, texto.length())));
        int cierre = comilla < 0 ? -1 : texto.indexOf('"', comilla + 1);
        if (cierre < 0) {
            return bytes;
        }
        String[] valores = {"", "x", "dup", "-1", "1e999", "NaN", "EMPRESA", "MARCIANO", "true", "a".repeat(300)};
        return (texto.substring(0, comilla + 1) + valores[azar.nextInt(valores.length)] + texto.substring(cierre))
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] intercambiarTrozos(byte[] bytes, Random azar) {
        if (bytes.length < 4) {
            return bytes;
        }
        int mitad = bytes.length / 2;
        int a = azar.nextInt(mitad);
        int b = mitad + azar.nextInt(bytes.length - mitad);
        int largo = 1 + azar.nextInt(Math.min(100, Math.min(mitad - a, bytes.length - b)));
        byte[] copia = bytes.clone();
        System.arraycopy(bytes, b, copia, a, largo);
        System.arraycopy(bytes, a, copia, b, largo);
        return copia;
    }

    private static Path guardar(byte[] entrada, long semilla, int vuelta) throws IOException {
        Path carpeta = Path.of("target", "fuzz");
        Files.createDirectories(carpeta);
        Path archivo = carpeta.resolve("fallo-" + semilla + "-" + vuelta + ".bpmn");
        Files.write(archivo, entrada);
        return archivo;
    }
}
