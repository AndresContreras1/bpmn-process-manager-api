package com.facimus.procesos.gestion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.facimus.procesos.common.almacen.Almacen;
import com.facimus.procesos.common.almacen.AlmacenEnDisco;
import com.facimus.procesos.gestion.service.AnclaDeBitacoraService.Ancla;

import tools.jackson.databind.json.JsonMapper;

/** PR 61: el ancla diaria, con la base de mentira y un almacen en disco de verdad. */
@ExtendWith(MockitoExtension.class)
class AnclaDeBitacoraServiceImplTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 12);
    private static final Instant AHORA = Instant.parse("2026-11-12T00:15:00Z");

    @Mock
    private JdbcTemplate jdbc;

    @TempDir
    Path raiz;

    private Almacen almacen;
    private AnclaDeBitacoraServiceImpl anclas;

    @BeforeEach
    void preparar() {
        almacen = new AlmacenEnDisco(raiz);
        anclas = new AnclaDeBitacoraServiceImpl(jdbc, almacen, JsonMapper.builder().build(),
                Clock.fixed(AHORA, ZoneOffset.UTC), Duration.ofDays(400));
    }

    @Test
    @DisplayName("ancla cada tienda pendiente: el JSON va al almacen y la fila dice donde")
    void anclar() throws IOException {
        when(jdbc.queryForList(anyString(), eq(HOY))).thenReturn(List.of(
                Map.of("empresa_id", 1L, "secuencia", 57L, "hash", "a".repeat(64)),
                Map.of("empresa_id", 2L, "secuencia", 3, "hash", "b".repeat(64))));

        int ancladas = anclas.anclar(HOY);

        assertThat(ancladas).isEqualTo(2);
        try (InputStream ancla = almacen.abrir("anclas/1/2026-11-12")) {
            assertThat(new String(ancla.readAllBytes())).isEqualTo(
                    "{\"empresaId\":1,\"dia\":\"2026-11-12\",\"secuencia\":57,\"hash\":\"" + "a".repeat(64) + "\"}");
        }
        verify(jdbc).update(anyString(), eq(1L), eq(HOY), eq(57L), eq("a".repeat(64)), eq("anclas/1/2026-11-12"),
                eq(false), eq(LocalDateTime.of(2026, 11, 12, 0, 15)));
        verify(jdbc).update(anyString(), eq(2L), eq(HOY), eq(3L), eq("b".repeat(64)), eq("anclas/2/2026-11-12"),
                eq(false), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("si una tienda falla, las demas se anclan igual")
    void unaFalla() {
        Almacen roto = mock(Almacen.class);
        when(roto.guardarInmutable(eq("anclas/1/2026-11-12"), any(), any()))
                .thenThrow(new IllegalStateException("caido"));
        when(roto.guardarInmutable(eq("anclas/2/2026-11-12"), any(), any())).thenReturn(true);
        AnclaDeBitacoraServiceImpl conFalla = new AnclaDeBitacoraServiceImpl(jdbc, roto, JsonMapper.builder().build(),
                Clock.fixed(AHORA, ZoneOffset.UTC), Duration.ofDays(400));
        when(jdbc.queryForList(anyString(), eq(HOY))).thenReturn(List.of(
                Map.of("empresa_id", 1L, "secuencia", 5L, "hash", "a"),
                Map.of("empresa_id", 2L, "secuencia", 6L, "hash", "b")));

        assertThat(conFalla.anclar(HOY)).isEqualTo(1);
        verify(roto).guardarInmutable(eq("anclas/2/2026-11-12"), any(), eq(AHORA.plus(Duration.ofDays(400))));
        verify(jdbc, never()).update(anyString(), eq(1L), any(), any(), any(), any(), any(), any());
        verify(jdbc).update(anyString(), eq(2L), eq(HOY), eq(6L), eq("b"), eq("anclas/2/2026-11-12"), eq(true),
                any(LocalDateTime.class));
    }

    @Test
    @DisplayName("sin nada que anclar no escribe nada")
    void nada() {
        when(jdbc.queryForList(anyString(), eq(HOY))).thenReturn(List.of());

        assertThat(anclas.anclar(HOY)).isZero();
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("la ultima ancla se lee del almacen, no de la fila")
    void ultima() throws SQLException {
        anclarDeVerdad(1L, 57L, "c".repeat(64));
        fila(1L, 99L, "anclas/1/2026-11-12");

        assertThat(anclas.ultima(1L)).contains(new Ancla(HOY, 57, "c".repeat(64)));
    }

    @Test
    @DisplayName("PR 66: la ultima ancla se lee de donde se guardo inmutable, no del almacen de los documentos")
    void ultimaDelAlmacenInmutable() throws SQLException {
        Almacen s3 = mock(Almacen.class);
        when(s3.abrirInmutable("anclas/1/2026-11-12")).thenReturn(new ByteArrayInputStream(
                ("{\"secuencia\":57,\"hash\":\"" + "d".repeat(64) + "\"}").getBytes(StandardCharsets.UTF_8)));
        AnclaDeBitacoraServiceImpl conS3 = new AnclaDeBitacoraServiceImpl(jdbc, s3, JsonMapper.builder().build(),
                Clock.fixed(AHORA, ZoneOffset.UTC), Duration.ofDays(400));
        fila(1L, 99L, "anclas/1/2026-11-12");

        assertThat(conS3.ultima(1L)).contains(new Ancla(HOY, 57, "d".repeat(64)));
        verify(s3, never()).abrir(anyString());
    }

    @Test
    @DisplayName("sin anclas no hay ultima; si el almacen ya no la tiene, vuelve sin hash")
    @SuppressWarnings("unchecked")
    void sinAncla() throws SQLException {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(List.of());
        fila(2L, 8L, "anclas/2/2026-11-12");

        assertThat(anclas.ultima(1L)).isEmpty();
        assertThat(anclas.ultima(2L)).contains(new Ancla(HOY, 8, null));
    }

    /** La fila del ancla de una tienda, pasada por el mapeo del servicio como lo haria la base. */
    @SuppressWarnings("unchecked")
    private void fila(long empresaId, long secuencia, String clave) throws SQLException {
        ResultSet resultado = mock(ResultSet.class);
        when(resultado.getObject("dia", LocalDate.class)).thenReturn(HOY);
        when(resultado.getLong("secuencia")).thenReturn(secuencia);
        when(resultado.getString("clave")).thenReturn(clave);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(empresaId))).thenAnswer(llamada -> {
            RowMapper<Object> mapeo = llamada.getArgument(1);
            return List.of(mapeo.mapRow(resultado, 0));
        });
    }

    private void anclarDeVerdad(Long empresaId, Long secuencia, String hash) {
        when(jdbc.queryForList(anyString(), eq(HOY)))
                .thenReturn(List.of(Map.of("empresa_id", empresaId, "secuencia", secuencia, "hash", hash)));
        anclas.anclar(HOY);
    }
}
