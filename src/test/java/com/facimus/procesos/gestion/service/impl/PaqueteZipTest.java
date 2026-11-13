package com.facimus.procesos.gestion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.gestion.service.Paquete.FilasCsv;
import com.facimus.procesos.gestion.service.Paquete.FilasJson;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** PR 66: el ZIP de una exportacion: lo que lleva, su manifiesto con huellas, sus rutas y su CSV. */
class PaqueteZipTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final Destino destino = new Destino();
    private final PaqueteZip paquete = new PaqueteZip(destino, json);

    @Test
    @DisplayName("cada archivo queda con su contenido, el manifiesto va al final con el tamano y el SHA-256 de cada "
            + "uno, y el resumen mide el ZIP entero")
    void manifiestoConHuellas() throws IOException {
        paquete.json("organizacion.json", Map.of("nombre", "Acme"));
        paquete.texto("procesos/7-despacho/borrador.bpmn", "<definitions/>");
        paquete.copiar("procesos/7-despacho/documentos/12/v1-guia.pdf",
                new ByteArrayInputStream(new byte[] {37, 80, 68, 70}));
        try (FilasJson filas = paquete.filasJson("casos/casos.json")) {
            filas.agregar(Map.of("id", 1));
            filas.agregar(Map.of("id", 2));
        }
        try (FilasCsv filas = paquete.filasCsv("casos/casos.csv", List.of("id", "referencia"))) {
            filas.agregar(List.of(1, "ORD-1"));
        }

        PaqueteZip.Resumen resumen = paquete.terminar(cabecera());
        paquete.close();

        Map<String, byte[]> archivos = abrir(destino.toByteArray());
        assertThat(archivos.keySet()).containsExactly("organizacion.json", "procesos/7-despacho/borrador.bpmn",
                "procesos/7-despacho/documentos/12/v1-guia.pdf", "casos/casos.json", "casos/casos.csv",
                "manifest.json");
        assertThat(new String(archivos.get("procesos/7-despacho/borrador.bpmn"), StandardCharsets.UTF_8))
                .isEqualTo("<definitions/>");
        assertThat(archivos.get("procesos/7-despacho/documentos/12/v1-guia.pdf")).containsExactly(37, 80, 68, 70);
        assertThat(json.readTree(archivos.get("casos/casos.json"))).hasSize(2);
        assertThat(json.readTree(archivos.get("casos/casos.json")).get(1).get("id").asInt()).isEqualTo(2);

        JsonNode manifiesto = json.readTree(archivos.get("manifest.json"));
        assertThat(manifiesto.get("formato").asInt()).isEqualTo(1);
        assertThat(manifiesto.get("tienda").asString()).isEqualTo("Acme");
        assertThat(manifiesto.get("archivos")).hasSize(5);
        for (JsonNode archivo : manifiesto.get("archivos")) {
            byte[] bytes = archivos.get(archivo.get("ruta").asString());
            assertThat(archivo.get("tamano").asLong()).as(archivo.get("ruta").asString()).isEqualTo(bytes.length);
            assertThat(archivo.get("sha256").asString()).isEqualTo(Huella.de(bytes));
        }
        assertThat(resumen.archivos()).isEqualTo(5);
        assertThat(resumen.tamano()).isEqualTo(destino.size());
        assertThat(resumen.sha256()).isEqualTo(Huella.de(destino.toByteArray()));
        assertThat(destino.cerrado).as("el flujo es de quien lo abrio").isFalse();
    }

    @Test
    @DisplayName("un arreglo JSON sin filas es un arreglo vacio, y con filas una por linea")
    void filasJson() throws IOException {
        try (FilasJson _ = paquete.filasJson("vacio.json")) {
            // ninguna fila
        }
        try (FilasJson filas = paquete.filasJson("dos.json")) {
            filas.agregar(Map.of("a", "x"));
            filas.agregar(List.of(1, 2));
        }
        paquete.terminar(cabecera());

        Map<String, byte[]> archivos = abrir(destino.toByteArray());
        assertThat(new String(archivos.get("vacio.json"), StandardCharsets.UTF_8)).isEqualTo("[\n]\n");
        assertThat(new String(archivos.get("dos.json"), StandardCharsets.UTF_8))
                .isEqualTo("[\n{\"a\":\"x\"},\n[1,2]\n]\n");
    }

    @Test
    @DisplayName("el CSV va con su cabecera, CRLF al final de cada linea y cada celda escapada")
    void csv() throws IOException {
        try (FilasCsv filas = paquete.filasCsv("casos.csv", List.of("id", "referencia", "nota"))) {
            filas.agregar(Arrays.asList(1, "=HYPERLINK(\"x\")", null));
            filas.agregar(List.of(-5, "a,b", "dijo \"hola\"\nadios"));
        }
        paquete.terminar(cabecera());

        assertThat(new String(abrir(destino.toByteArray()).get("casos.csv"), StandardCharsets.UTF_8)).isEqualTo(
                "id,referencia,nota\r\n1,\"'=HYPERLINK(\"\"x\"\")\",\r\n-5,\"a,b\",\"dijo \"\"hola\"\"\nadios\"\r\n");
    }

    @Test
    @DisplayName("una fila con otro numero de celdas que la cabecera, o un CSV sin columnas, se rechazan")
    void csvMalArmado() {
        List<String> sinColumnas = List.of();
        assertThatThrownBy(() -> paquete.filasCsv("nada.csv", sinColumnas))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sin columnas");
        try (FilasCsv filas = paquete.filasCsv("casos.csv", List.of("id", "estado"))) {
            List<Object> una = List.of(1);
            assertThatThrownBy(() -> filas.agregar(una)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("1 celdas y la cabecera 2");
        }
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"../fuera.txt", "/raiz.txt", "a//b.txt", "a/../b.txt", ".oculto", "a/.oculto",
        "a\\b.txt", "a/", "procesos/7 despacho/x.json", "tilde-á.txt", "-guion.txt", "manifest.json"})
    @DisplayName("una ruta que se sale, absoluta, vacia por tramos, oculta o con algo fuera de lo permitido se rechaza")
    void rutasMalas(String ruta) {
        assertThatThrownBy(() -> paquete.texto(ruta, "x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ni vacia, ni nula, ni de mas de 255 caracteres; 255 si")
    void largoDeLaRuta() {
        String larga = "a".repeat(PaqueteZip.LARGO_MAXIMO_DE_RUTA);
        assertThatThrownBy(() -> paquete.texto("", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> paquete.texto(null, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> paquete.texto(larga + "a", "x")).isInstanceOf(IllegalArgumentException.class);

        paquete.texto(larga, "x");
    }

    @Test
    @DisplayName("una ruta no se repite, y un archivo no ocupa el lugar de una carpeta ni al reves")
    void rutasQueChocan() {
        paquete.texto("procesos/7/proceso.json", "{}");
        paquete.texto("casos", "x");

        assertThatThrownBy(() -> paquete.texto("procesos/7/proceso.json", "{}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ya esta");
        assertThatThrownBy(() -> paquete.texto("procesos/7", "x"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ya esta");
        assertThatThrownBy(() -> paquete.texto("procesos", "x"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ya esta");
        assertThatThrownBy(() -> paquete.texto("casos/casos.json", "x"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pasa por el archivo casos");
        paquete.texto("procesos/8/proceso.json", "{}");
        paquete.texto("procesos/7/otro.json", "{}");
    }

    @Test
    @DisplayName("un archivo se escribe entero antes de empezar el siguiente, y uno cerrado no recibe mas filas")
    void deAUnArchivo() {
        FilasJson abiertas = paquete.filasJson("uno.json");
        assertThatThrownBy(() -> paquete.texto("dos.txt", "x")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("uno.json abierto");
        abiertas.close();
        abiertas.close();
        Map<String, Integer> fila = Map.of("id", 1);
        assertThatThrownBy(() -> abiertas.agregar(fila)).isInstanceOf(IllegalStateException.class);

        FilasCsv csv = paquete.filasCsv("dos.csv", List.of("id"));
        csv.close();
        List<Integer> celdas = List.of(1);
        assertThatThrownBy(() -> csv.agregar(celdas)).isInstanceOf(IllegalStateException.class);
        paquete.texto("tres.txt", "x");
    }

    @Test
    @DisplayName("terminado, el paquete no recibe nada mas ni se termina otra vez")
    void terminado() {
        paquete.texto("uno.txt", "x");
        paquete.terminar(cabecera());

        assertThatThrownBy(() -> paquete.texto("dos.txt", "x")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya se termino");
        Map<String, Object> otra = cabecera();
        assertThatThrownBy(() -> paquete.terminar(otra)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("el medidor cuenta y resume cada byte, escrito de a uno o de a varios, y no cierra lo de abajo")
    void medidor() throws IOException {
        Destino abajo = new Destino();
        PaqueteZip.Medidor medidor = new PaqueteZip.Medidor(abajo);

        medidor.write('a');
        medidor.write("xbcx".getBytes(StandardCharsets.US_ASCII), 1, 2);
        medidor.flush();
        medidor.close();

        assertThat(medidor.bytes()).isEqualTo(3);
        assertThat(medidor.sha256()).isEqualTo(Huella.de("abc"));
        assertThat(abajo.toString(StandardCharsets.US_ASCII)).isEqualTo("abc");
        assertThat(abajo.vaciado).isTrue();
        assertThat(abajo.cerrado).isFalse();
    }

    private static Map<String, Object> cabecera() {
        Map<String, Object> cabecera = new LinkedHashMap<>();
        cabecera.put("formato", 1);
        cabecera.put("tienda", "Acme");
        return cabecera;
    }

    /** Cada archivo del ZIP, en su orden. */
    static Map<String, byte[]> abrir(byte[] zip) throws IOException {
        Map<String, byte[]> archivos = new LinkedHashMap<>();
        try (ZipInputStream entrada = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry archivo;
            while ((archivo = entrada.getNextEntry()) != null) {
                archivos.put(archivo.getName(), entrada.readAllBytes());
            }
        }
        return archivos;
    }

    /** Un destino que dice si lo vaciaron o lo cerraron. */
    private static final class Destino extends ByteArrayOutputStream {

        private boolean cerrado;
        private boolean vaciado;

        @Override
        public void flush() {
            vaciado = true;
        }

        @Override
        public void close() {
            cerrado = true;
        }
    }
}
