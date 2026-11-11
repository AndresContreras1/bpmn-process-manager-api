package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

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

import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.modelado.dto.response.PresenciaResponse;
import com.facimus.procesos.modelado.model.Presencia;
import com.facimus.procesos.modelado.repository.PresenciaRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 58: el canal en vivo. Publicar es un {@code pg_notify} con la tienda, el proceso, el tipo y los datos; un aviso
 * recibido llega solo a las conexiones de ese proceso y esa tienda, y la presencia se lee de la base sin repetir
 * personas. El latido marca las conexiones vivas, cierra las cortadas y barre lo que nadie marco, avisando una vez
 * por proceso.
 */
@ExtendWith(MockitoExtension.class)
class CanalEnVivoTest {

    private static final Long EMPRESA = 1L;
    private static final Long PROCESO = 7L;

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private PresenciaRepository presenciaRepository;

    private final JsonMapper json = JsonMapper.builder().build();
    private CanalEnVivo canal;

    @BeforeEach
    void setUp() {
        canal = new CanalEnVivo(jdbc, json, presenciaRepository);
    }

    @AfterEach
    void cerrar() {
        canal.cerrar();
    }

    private String aviso(Long empresaId, Long procesoId, String tipo, String datos) {
        return "{\"empresaId\":" + empresaId + ",\"procesoId\":" + procesoId + ",\"tipo\":\"" + tipo
                + "\",\"datos\":" + datos + "}";
    }

    /** Lo que se le mando al emisor, con espera: los envios salen en hilos virtuales. */
    private SseEventBuilder enviado(SseEmitter emisor) throws IOException {
        ArgumentCaptor<SseEventBuilder> evento = ArgumentCaptor.forClass(SseEventBuilder.class);
        verify(emisor, timeout(2000)).send(evento.capture());
        return evento.getValue();
    }

    private static String texto(List<Object> partes) {
        StringBuilder texto = new StringBuilder();
        partes.stream().filter(String.class::isInstance).forEach(texto::append);
        return texto.toString();
    }

    private static List<Object> partes(SseEventBuilder evento) {
        return evento.build().stream().map(DataWithMediaType::getData).toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> notificados(int veces) {
        ArgumentCaptor<Object> carga = ArgumentCaptor.forClass(Object.class);
        verify(jdbc, times(veces)).query(eq("select pg_notify(?, ?)"), any(ResultSetExtractor.class), eq("en_vivo"),
                carga.capture());
        return carga.getAllValues().stream().map(String.class::cast).toList();
    }

    @Test
    @DisplayName("Publicar manda un pg_notify al canal en_vivo con la tienda, el proceso, el tipo y los datos")
    void publicar_mandaElAvisoPorPgNotify() {
        canal.publicar(EMPRESA, PROCESO, "bloqueo", Map.of("usuarioId", 2L, "nombre", "Ana"));

        JsonNode aviso = json.readTree(notificados(1).getFirst());
        assertThat(aviso.get("empresaId").longValue()).isEqualTo(EMPRESA);
        assertThat(aviso.get("procesoId").longValue()).isEqualTo(PROCESO);
        assertThat(aviso.get("tipo").asString()).isEqualTo("bloqueo");
        assertThat(aviso.get("datos").get("usuarioId").longValue()).isEqualTo(2L);
        assertThat(aviso.get("datos").get("nombre").asString()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("Quitar una conexion dice si seguia registrada, y una quitada ya no recibe avisos")
    void quitar_diceSiSeguiaRegistrada() throws IOException {
        SseEmitter emisor = mock(SseEmitter.class);
        canal.registrar("c1", EMPRESA, PROCESO, emisor);

        assertThat(canal.quitar("c1")).isTrue();
        assertThat(canal.quitar("c1")).isFalse();
        assertThat(canal.quitar("nunca")).isFalse();

        canal.recibir(aviso(EMPRESA, PROCESO, "guardado", "{\"autor\":\"Ana\"}"));
        verify(emisor, never()).send(any(SseEventBuilder.class));
    }

    @Test
    @DisplayName("Un aviso va a las conexiones de su proceso y su tienda, con su tipo y sus datos, y a ninguna otra")
    void recibir_soloAlProcesoYLaTiendaDelAviso() throws IOException {
        SseEmitter mismo = mock(SseEmitter.class);
        SseEmitter otroProceso = mock(SseEmitter.class);
        SseEmitter otraTienda = mock(SseEmitter.class);
        canal.registrar("a", EMPRESA, PROCESO, mismo);
        canal.registrar("b", EMPRESA, 8L, otroProceso);
        canal.registrar("c", 2L, PROCESO, otraTienda);

        canal.recibir(aviso(EMPRESA, PROCESO, "guardado", "{\"autor\":\"Ana\"}"));

        List<Object> partes = partes(enviado(mismo));
        assertThat(texto(partes)).contains("event:guardado");
        assertThat(partes).contains(json.readTree("{\"autor\":\"Ana\"}"));
        verify(otroProceso, never()).send(any(SseEventBuilder.class));
        verify(otraTienda, never()).send(any(SseEventBuilder.class));
        verify(presenciaRepository, never()).findAllByProcesoIdAndEmpresaIdOrderByIdAsc(any(), any());
    }

    @Test
    @DisplayName("Un aviso de presencia lleva la lista de personas leida de la base")
    void recibir_presencia_llevaLasPersonas() throws IOException {
        SseEmitter emisor = mock(SseEmitter.class);
        canal.registrar("a", EMPRESA, PROCESO, emisor);
        LocalDateTime desde = LocalDateTime.of(2026, 11, 11, 9, 30);
        when(presenciaRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, EMPRESA))
                .thenReturn(List.of(presencia(1L, 2L, "Ana", desde)));

        canal.recibir(aviso(EMPRESA, PROCESO, CanalEnVivo.PRESENCIA, "{}"));

        List<Object> partes = partes(enviado(emisor));
        assertThat(texto(partes)).contains("event:presencia");
        assertThat(partes).contains(Map.of("personas", List.of(new PresenciaResponse(2L, "Ana", desde))));
    }

    @Test
    @DisplayName("Un aviso sin nadie mirando en esta replica no lee la base ni envia nada")
    void recibir_sinConexiones_noHaceNada() {
        SseEmitter otro = mock(SseEmitter.class);
        canal.registrar("b", EMPRESA, 8L, otro);

        canal.recibir(aviso(EMPRESA, PROCESO, CanalEnVivo.PRESENCIA, "{}"));

        verify(presenciaRepository, never()).findAllByProcesoIdAndEmpresaIdOrderByIdAsc(any(), any());
    }

    @Test
    @DisplayName("Una conexion que se corta al enviarle un aviso se cierra con el error")
    void recibir_conexionCortada_seCierraConError() throws IOException {
        SseEmitter emisor = mock(SseEmitter.class);
        IOException cortada = new IOException("cortada");
        doThrow(cortada).when(emisor).send(any(SseEventBuilder.class));
        canal.registrar("a", EMPRESA, PROCESO, emisor);

        canal.recibir(aviso(EMPRESA, PROCESO, "guardado", "{}"));

        verify(emisor, timeout(2000)).completeWithError(cortada);
    }

    @Test
    @DisplayName("Cerrado el canal, los envios ya no se aceptan")
    void cerrar_noAceptaMasEnvios() {
        canal.registrar("a", EMPRESA, PROCESO, mock(SseEmitter.class));

        canal.cerrar();

        assertThatThrownBy(() -> canal.recibir(aviso(EMPRESA, PROCESO, "guardado", "{}")))
                .isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    @DisplayName("La presencia cuenta a cada persona una vez, con su primera pestana, y se lee como sistema")
    void personas_cadaUnaUnaVez() {
        LocalDateTime primera = LocalDateTime.of(2026, 11, 11, 9, 0);
        LocalDateTime segunda = LocalDateTime.of(2026, 11, 11, 9, 5);
        LocalDateTime deBeto = LocalDateTime.of(2026, 11, 11, 9, 2);
        AtomicReference<Optional<ContextoDeTienda.Alcance>> alcance = new AtomicReference<>();
        when(presenciaRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, EMPRESA)).thenAnswer(inv -> {
            alcance.set(ContextoDeTienda.actual());
            return List.of(presencia(1L, 2L, "Ana", primera), presencia(2L, 3L, "Beto", deBeto),
                    presencia(3L, 2L, "Ana", segunda));
        });

        List<PresenciaResponse> personas = canal.personas(EMPRESA, PROCESO);

        assertThat(personas).containsExactly(new PresenciaResponse(2L, "Ana", primera),
                new PresenciaResponse(3L, "Beto", deBeto));
        assertThat(alcance.get()).contains(ContextoDeTienda.Alcance.SISTEMA);
    }

    @Test
    @DisplayName("El latido manda un comentario, marca las vivas como sistema y cierra las que no responden")
    @SuppressWarnings("unchecked")
    void latir_marcaLasVivasYCierraLasCortadas() throws IOException {
        SseEmitter viva = mock(SseEmitter.class);
        SseEmitter cortada = mock(SseEmitter.class);
        IllegalStateException error = new IllegalStateException("cerrada");
        doThrow(error).when(cortada).send(any(SseEventBuilder.class));
        canal.registrar("viva", EMPRESA, PROCESO, viva);
        canal.registrar("cortada", EMPRESA, PROCESO, cortada);
        AtomicReference<Optional<ContextoDeTienda.Alcance>> alcance = new AtomicReference<>();
        when(presenciaRepository.latir(anyCollection(), any(LocalDateTime.class))).thenAnswer(inv -> {
            alcance.set(ContextoDeTienda.actual());
            return 1;
        });
        LocalDateTime antes = LocalDateTime.now();

        canal.latir();

        LocalDateTime despues = LocalDateTime.now();
        ArgumentCaptor<SseEventBuilder> evento = ArgumentCaptor.forClass(SseEventBuilder.class);
        verify(viva).send(evento.capture());
        assertThat(texto(partes(evento.getValue()))).contains(":latido");
        verify(cortada).completeWithError(error);
        verify(viva, never()).completeWithError(any());
        ArgumentCaptor<Collection<String>> marcadas = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<LocalDateTime> ahora = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(presenciaRepository).latir(marcadas.capture(), ahora.capture());
        assertThat(marcadas.getValue()).containsExactly("viva");
        assertThat(ahora.getValue()).isAfterOrEqualTo(antes).isBeforeOrEqualTo(despues);
        assertThat(alcance.get()).contains(ContextoDeTienda.Alcance.SISTEMA);
    }

    @Test
    @DisplayName("Sin conexiones vivas el latido no marca nada, pero igual barre lo de mas de un minuto")
    void latir_sinVivas_soloBarre() {
        LocalDateTime antes = LocalDateTime.now();

        canal.latir();

        LocalDateTime despues = LocalDateTime.now();
        verify(presenciaRepository, never()).latir(anyCollection(), any());
        ArgumentCaptor<LocalDateTime> limite = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(presenciaRepository).findAllByUltimoLatidoBefore(limite.capture());
        assertThat(limite.getValue()).isAfterOrEqualTo(antes.minusSeconds(60))
                .isBeforeOrEqualTo(despues.minusSeconds(60));
        verify(presenciaRepository, never()).borrar(anyCollection());
        verify(jdbc, never()).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));
    }

    @Test
    @DisplayName("Barrer borra las presencias sin latido y avisa una vez a cada proceso que tenia alguna")
    void latir_barreLasMuertasYAvisaUnaVezPorProceso() {
        Empresa otra = Empresa.builder().id(2L).build();
        Presencia una = presencia(11L, 2L, "Ana", LocalDateTime.now().minusMinutes(5));
        Presencia otraPestana = presencia(12L, 2L, "Ana", LocalDateTime.now().minusMinutes(5));
        Presencia deOtraTienda = Presencia.builder().id(13L).empresa(otra).procesoId(9L).usuarioId(4L).nombre("Caro")
                .build();
        when(presenciaRepository.findAllByUltimoLatidoBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(una, otraPestana, deOtraTienda));

        canal.latir();

        verify(presenciaRepository).borrar(List.of(11L, 12L, 13L));
        List<JsonNode> avisos = new ArrayList<>();
        notificados(2).forEach(carga -> avisos.add(json.readTree(carga)));
        assertThat(avisos).extracting(aviso -> aviso.get("empresaId").longValue() + "/"
                + aviso.get("procesoId").longValue() + "/" + aviso.get("tipo").asString())
                .containsExactlyInAnyOrder("1/7/presencia", "2/9/presencia");
    }

    private Presencia presencia(Long id, Long usuarioId, String nombre, LocalDateTime desde) {
        return Presencia.builder().id(id).empresa(Empresa.builder().id(EMPRESA).build()).procesoId(PROCESO)
                .usuarioId(usuarioId).nombre(nombre).conexion("con-" + id).desde(desde).ultimoLatido(desde).build();
    }
}
