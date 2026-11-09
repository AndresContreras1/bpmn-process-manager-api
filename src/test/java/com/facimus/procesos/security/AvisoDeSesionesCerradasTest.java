package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import com.facimus.procesos.gestion.event.SesionesCerradas;

/** El aviso que reciben las demas instancias cuando se cierran sesiones: que lleva y en cuantas partes. */
class AvisoDeSesionesCerradasTest {

    private static final String NOTIFY = "select pg_notify(?, ?)";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AvisoDeSesionesCerradas aviso = new AvisoDeSesionesCerradas(jdbc);

    @Test
    @DisplayName("Los codigos de las sesiones cerradas salen por el canal de PostgreSQL, separados por comas")
    void avisar_mandaLosCodigosPorElCanal() {
        aviso.avisar(new SesionesCerradas(List.of("cerrada-1", "cerrada-2")));

        verify(jdbc).query(eq(NOTIFY), any(ResultSetExtractor.class), eq(AvisoDeSesionesCerradas.CANAL),
                eq("cerrada-1,cerrada-2"));
    }

    @Test
    @DisplayName("Muchas sesiones van en varios avisos, cada uno por debajo de los 8000 bytes que acepta PostgreSQL")
    void avisar_muchasSesiones_vanEnVariosAvisos() {
        List<String> codigos = IntStream.range(0, 250).mapToObj(i -> UUID.randomUUID().toString()).toList();
        ArgumentCaptor<Object> lotes = ArgumentCaptor.forClass(Object.class);

        aviso.avisar(new SesionesCerradas(codigos));

        verify(jdbc, times(3)).query(eq(NOTIFY), any(ResultSetExtractor.class), eq(AvisoDeSesionesCerradas.CANAL),
                lotes.capture());
        assertThat(lotes.getAllValues()).allSatisfy(lote ->
                assertThat(((String) lote).getBytes(StandardCharsets.UTF_8).length).isLessThan(8000));
        assertThat(String.join(",", lotes.getAllValues().stream().map(String.class::cast).toList()))
                .isEqualTo(String.join(",", codigos));
    }

    @Test
    @DisplayName("Sin sesiones cerradas no sale ningun aviso")
    void avisar_sinSesiones_noMandaNada() {
        aviso.avisar(new SesionesCerradas(List.of()));

        verifyNoInteractions(jdbc);
    }
}
