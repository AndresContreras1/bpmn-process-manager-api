package com.facimus.procesos.gestion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.almacen.Almacen;
import com.facimus.procesos.common.almacen.AlmacenEnDisco;
import com.facimus.procesos.common.almacen.ArchivoNoEncontradoException;
import com.facimus.procesos.common.almacen.ClaveDeAlmacen;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.trabajos.Trabajo;
import com.facimus.procesos.gestion.service.impl.BorradoDeTiendas.TiendaBorrada;
import com.facimus.procesos.gestion.service.impl.BorradoDeTiendas.UltimaExportacion;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 61 y PR 66: el trabajo que borra del almacen lo de una tienda borrada y despues emite su certificado de borrado,
 * con un almacen en disco de verdad.
 */
class BorradoDelAlmacenTest {

    private static final Instant AHORA = Instant.parse("2026-12-13T03:30:05Z");
    private static final Duration RETENCION = Duration.ofDays(3650);

    @TempDir
    Path raiz;

    private final JsonMapper json = JsonMapper.builder().build();
    private final Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
    private final Auditoria auditoria = mock(Auditoria.class);
    private AlmacenEnDisco almacen;
    private BorradoDelAlmacen borrado;

    @BeforeEach
    void preparar() throws IOException {
        almacen = new AlmacenEnDisco(raiz);
        borrado = new BorradoDelAlmacen(almacen, auditoria, json, reloj, RETENCION);
        Path archivo = Files.writeString(raiz.resolve("x.txt"), "contenido");
        almacen.guardar(ClaveDeAlmacen.deDocumento(12, 1, 1, "a"), archivo, "text/plain");
        almacen.guardar(ClaveDeAlmacen.deExportacion(12, 4), archivo, "application/zip");
        almacen.guardar(ClaveDeAlmacen.deDocumento(13, 2, 1, "b"), archivo, "text/plain");
    }

    private static TiendaBorrada tienda(UltimaExportacion ultima) {
        Map<String, Integer> filas = new LinkedHashMap<>();
        filas.put("procesos_compartidos", 1);
        filas.put("usuarios", 3);
        return new TiendaBorrada(12L, LocalDateTime.of(2026, 11, 12, 9, 0), LocalDateTime.of(2026, 12, 13, 3, 30),
                filas, 4, ultima);
    }

    private void ejecutar(TiendaBorrada tienda) {
        borrado.ejecutar(new Trabajo(1, null, BorradoDeTiendas.BORRAR_ARCHIVOS, json.writeValueAsString(tienda), 1, 5));
    }

    @Test
    @DisplayName("borra el prefijo de la tienda y solo el suyo, y emite el certificado: que tienda, cuando, cuantas "
            + "filas de cada tabla, cuantos objetos y la huella de su ultima exportacion, sin datos de nadie")
    void certificado() throws IOException {
        ejecutar(tienda(new UltimaExportacion(4L, "e".repeat(64), LocalDateTime.of(2026, 11, 20, 8, 0))));

        assertThat(borrado.tipo()).isEqualTo("almacen.borrar-tienda");
        assertThat(raiz.resolve("empresas/12")).doesNotExist();
        assertThat(raiz.resolve("empresas/13/documentos/2/1-b")).exists();
        byte[] bytes;
        try (InputStream emitido = almacen.abrirInmutable("certificados/12/borrado")) {
            bytes = emitido.readAllBytes();
        }
        JsonNode certificado = json.readTree(bytes);
        assertThat(certificado.propertyNames()).containsExactly("formato", "tipo", "tienda", "bajaSolicitadaEn",
                "borradaEn", "emitidoEn", "zonaHoraria", "filas", "totalDeFilas", "almacen", "ultimaExportacion",
                "conservadas");
        assertThat(certificado.get("formato").asInt()).isEqualTo(1);
        assertThat(certificado.get("tipo").asString()).isEqualTo("certificado-de-borrado");
        assertThat(certificado.get("tienda").asLong()).isEqualTo(12);
        assertThat(certificado.get("bajaSolicitadaEn").asString()).isEqualTo("2026-11-12T09:00");
        assertThat(certificado.get("borradaEn").asString()).isEqualTo("2026-12-13T03:30");
        assertThat(certificado.get("emitidoEn").asString()).isEqualTo("2026-12-13T03:30:05");
        assertThat(certificado.get("zonaHoraria").asString()).isEqualTo("UTC");
        assertThat(certificado.get("filas").propertyNames()).containsExactly("procesos_compartidos", "usuarios");
        assertThat(certificado.get("filas").get("usuarios").asInt()).isEqualTo(3);
        assertThat(certificado.get("totalDeFilas").asInt()).isEqualTo(4);
        assertThat(certificado.get("almacen").get("prefijo").asString()).isEqualTo("empresas/12/");
        assertThat(certificado.get("almacen").get("objetosBorrados").asInt()).isEqualTo(2);
        assertThat(certificado.get("ultimaExportacion").get("id").asLong()).isEqualTo(4);
        assertThat(certificado.get("ultimaExportacion").get("sha256").asString()).isEqualTo("e".repeat(64));
        assertThat(certificado.get("ultimaExportacion").get("listaEn").asString()).isEqualTo("2026-11-20T08:00");
        assertThat(certificado.get("conservadas").valueStream().map(JsonNode::asString))
                .containsExactly("empresas", "bitacora", "anclas_bitacora");

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("certificado", Huella.de(bytes));
        detalle.put("clave", "certificados/12/borrado");
        detalle.put("inmutable", false);
        detalle.put("filas", 4);
        detalle.put("objetos", 2);
        verify(auditoria).registrar(12L, null, "TIENDA_BORRADA", "EMPRESA", 12L, detalle);
    }

    @Test
    @DisplayName("sin exportaciones, el certificado lo dice con un nulo; sin fecha de baja, tambien")
    void sinExportacion() throws IOException {
        ejecutar(new TiendaBorrada(12L, null, LocalDateTime.of(2026, 12, 13, 3, 30), Map.of(), 0, null));

        try (InputStream emitido = almacen.abrirInmutable("certificados/12/borrado")) {
            JsonNode certificado = json.readTree(emitido.readAllBytes());
            assertThat(certificado.get("ultimaExportacion").isNull()).isTrue();
            assertThat(certificado.get("bajaSolicitadaEn").isNull()).isTrue();
        }
    }

    @Test
    @DisplayName("un certificado ya emitido no se emite otra vez: el reintento de la cola no borra ni anota nada")
    void yaEmitido() {
        Almacen conCertificado = mock(Almacen.class);
        when(conCertificado.abrirInmutable("certificados/12/borrado")).thenReturn(InputStream.nullInputStream());
        BorradoDelAlmacen reintento = new BorradoDelAlmacen(conCertificado, auditoria, json, reloj, RETENCION);

        reintento.ejecutar(new Trabajo(1, null, BorradoDeTiendas.BORRAR_ARCHIVOS, json.writeValueAsString(tienda(null)),
                2, 5));

        verify(conCertificado, never()).borrarPrefijo(anyString());
        verify(conCertificado, never()).guardarInmutable(anyString(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("el certificado va al almacen inmutable por su retencion, despues de borrar, y la bitacora dice si "
            + "quedo bloqueado")
    void inmutable() {
        Almacen s3 = mock(Almacen.class);
        when(s3.abrirInmutable(anyString())).thenThrow(new ArchivoNoEncontradoException("certificados/12/borrado"));
        when(s3.borrarPrefijo("empresas/12/")).thenReturn(7);
        when(s3.guardarInmutable(eq("certificados/12/borrado"), any(), any())).thenReturn(true);
        BorradoDelAlmacen conS3 = new BorradoDelAlmacen(s3, auditoria, json, reloj, RETENCION);

        conS3.ejecutar(new Trabajo(1, null, BorradoDeTiendas.BORRAR_ARCHIVOS, json.writeValueAsString(tienda(null)),
                1, 5));

        verify(s3).guardarInmutable(eq("certificados/12/borrado"), any(), eq(AHORA.plus(RETENCION)));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> detalle = ArgumentCaptor.forClass(Map.class);
        verify(auditoria).registrar(eq(12L), isNull(), eq("TIENDA_BORRADA"), eq("EMPRESA"), eq(12L), detalle.capture());
        assertThat(detalle.getValue()).containsEntry("inmutable", true).containsEntry("objetos", 7);
    }
}
