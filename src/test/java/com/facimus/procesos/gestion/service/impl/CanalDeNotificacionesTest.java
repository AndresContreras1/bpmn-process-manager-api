package com.facimus.procesos.gestion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 63: el canal de las notificaciones. Publicar es un {@code pg_notify} con la tienda, la persona, el tipo y los
 * datos; un aviso recibido llega solo a las conexiones de esa persona en esa tienda. El latido cierra las cortadas.
 */
@ExtendWith(MockitoExtension.class)
class CanalDeNotificacionesTest {

    private static final Long EMPRESA = 1L;
    private static final Long BETO = 5L;

    @Mock
    private JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();
    private CanalDeNotificaciones canal;

    @BeforeEach
    void setUp() {
        canal = new CanalDeNotificaciones(jdbc, json);
    }

    @AfterEach
    void cerrar() {
        canal.cerrar();
    }

    private static String aviso(Long empresaId, Long usuarioId) {
        return "{\"empresaId\":" + empresaId + ",\"usuarioId\":" + usuarioId
                + ",\"tipo\":\"conteo\",\"datos\":{\"noLeidas\":3}}";
    }

    private static String texto(SseEventBuilder evento) {
        StringBuilder texto = new StringBuilder();
        evento.build().stream().map(DataWithMediaType::getData).forEach(texto::append);
        return texto.toString();
    }

    @Test
    @DisplayName("Publicar manda un pg_notify al canal notificaciones con la tienda, la persona, el tipo y los datos")
    @SuppressWarnings("unchecked")
    void publicar() {
        canal.publicar(EMPRESA, BETO, "conteo", Map.of("noLeidas", 3));

        ArgumentCaptor<Object> carga = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).query(eq("select pg_notify(?, ?)"), any(ResultSetExtractor.class), eq("notificaciones"),
                carga.capture());
        JsonNode aviso = json.readTree((String) carga.getValue());
        assertThat(aviso.get("empresaId").longValue()).isEqualTo(EMPRESA);
        assertThat(aviso.get("usuarioId").longValue()).isEqualTo(BETO);
        assertThat(aviso.get("tipo").asString()).isEqualTo("conteo");
        assertThat(aviso.get("datos").get("noLeidas").intValue()).isEqualTo(3);
        assertThat(canal.canal()).isEqualTo("notificaciones");
    }

    @Test
    @DisplayName("Un aviso va a las conexiones de su persona en su tienda, con su tipo y sus datos, y a ninguna otra")
    void recibir() throws IOException {
        SseEmitter suya = mock(SseEmitter.class);
        SseEmitter otraPersona = mock(SseEmitter.class);
        SseEmitter otraTienda = mock(SseEmitter.class);
        canal.registrar("a", EMPRESA, BETO, suya);
        canal.registrar("b", EMPRESA, 6L, otraPersona);
        canal.registrar("c", 2L, BETO, otraTienda);

        canal.recibir(aviso(EMPRESA, BETO));

        ArgumentCaptor<SseEventBuilder> evento = ArgumentCaptor.forClass(SseEventBuilder.class);
        verify(suya, timeout(2000)).send(evento.capture());
        assertThat(texto(evento.getValue())).contains("event:conteo", "\"noLeidas\":3");
        verify(otraPersona, never()).send(any(SseEventBuilder.class));
        verify(otraTienda, never()).send(any(SseEventBuilder.class));
        assertThat(canal.abiertas(EMPRESA, BETO)).isEqualTo(1);
        assertThat(canal.abiertas(2L, BETO)).isEqualTo(1);
    }

    @Test
    @DisplayName("Una conexion que se corta al enviarle se cierra con error; una quitada ya no recibe nada")
    void cortada() throws IOException {
        SseEmitter cortada = mock(SseEmitter.class);
        SseEmitter quitada = mock(SseEmitter.class);
        IOException corte = new IOException("se cerro la pestana");
        doThrow(corte).when(cortada).send(any(SseEventBuilder.class));
        canal.registrar("a", EMPRESA, BETO, cortada);
        canal.registrar("b", EMPRESA, BETO, quitada);
        canal.quitar("b");

        canal.recibir(aviso(EMPRESA, BETO));

        verify(cortada, timeout(2000)).completeWithError(corte);
        verify(quitada, never()).send(any(SseEventBuilder.class));
        assertThat(canal.abiertas(EMPRESA, BETO)).isEqualTo(1);
    }

    @Test
    @DisplayName("El latido manda un comentario a cada conexion y cierra con error la que no responde")
    void latir() throws IOException {
        SseEmitter viva = mock(SseEmitter.class);
        SseEmitter muerta = mock(SseEmitter.class);
        IllegalStateException corte = new IllegalStateException("ya cerrada");
        doThrow(corte).when(muerta).send(any(SseEventBuilder.class));
        canal.registrar("a", EMPRESA, BETO, viva);
        canal.registrar("b", EMPRESA, BETO, muerta);

        canal.latir();

        ArgumentCaptor<SseEventBuilder> evento = ArgumentCaptor.forClass(SseEventBuilder.class);
        verify(viva).send(evento.capture());
        assertThat(texto(evento.getValue())).contains(":latido");
        verify(viva, never()).completeWithError(any());
        verify(muerta).completeWithError(corte);
        assertThat(canal.abiertas(EMPRESA, BETO)).isEqualTo(2);
    }

    @Test
    @DisplayName("Cerrado el canal, sus envios ya no salen: la aplicacion se esta apagando")
    void cerrado() {
        canal.registrar("a", EMPRESA, BETO, mock(SseEmitter.class));
        canal.cerrar();
        String aviso = aviso(EMPRESA, BETO);

        assertThatThrownBy(() -> canal.recibir(aviso)).isInstanceOf(RejectedExecutionException.class);
    }
}
