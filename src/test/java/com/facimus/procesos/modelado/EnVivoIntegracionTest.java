package com.facimus.procesos.modelado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.facimus.procesos.common.DemasiadosIntentosException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.dto.response.BloqueoResponse;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.DatosDeArco;
import com.facimus.procesos.modelado.service.EnVivoService;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.PoolService;
import com.facimus.procesos.security.SesionEnCookies;

import tools.jackson.databind.json.JsonMapper;

/** PR 58: la presencia, el bloqueo suave y los avisos, con el LISTEN de verdad. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestPropertySource(properties = {"avisos.escuchar=true", "modelado.en-vivo.vencimiento-bloqueo=PT2S",
    "modelado.en-vivo.conexiones-por-usuario=2"})
class EnVivoIntegracionTest {

    private static final String CLAVE = "marea-violeta-del-sur";

    private static int tiendas;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private EmpresaService empresaService;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ProcesoService procesoService;
    @Autowired
    private RolProcesoService rolProcesoService;
    @Autowired
    private PoolService poolService;
    @Autowired
    private LaneService laneService;
    @Autowired
    private ActividadService actividadService;
    @Autowired
    private EventoService eventoService;
    @Autowired
    private ArcoService arcoService;
    @Autowired
    private EnVivoService enVivo;
    @Autowired
    private JdbcTemplate jdbc;

    private Long empresaId;
    private Long ana;
    private Long beto;
    private Long procesoId;
    private Long lane;
    private String correoAna;

    /** Un emisor que guarda lo que le mandan, en vez de escribirlo en una respuesta. */
    private static final class Oyente extends SseEmitter {

        private final List<String> recibido = new CopyOnWriteArrayList<>();

        @Override
        public void send(SseEventBuilder evento) throws IOException {
            StringBuilder texto = new StringBuilder();
            evento.build().forEach(parte -> texto.append(parte.getData()));
            recibido.add(texto.toString());
        }

        boolean oyo(String tipo) {
            return recibido.stream().anyMatch(uno -> uno.contains("event:" + tipo));
        }

        boolean oyoConTexto(String tipo, String contenido) {
            return recibido.stream().anyMatch(uno -> uno.contains("event:" + tipo) && uno.contains(contenido));
        }
    }

    @BeforeEach
    void modelar() {
        int numero = ++tiendas;
        correoAna = "ana" + numero + "@envivo.test";
        empresaId = empresaService.registrar("Tienda en vivo " + numero, "9481" + numero + "000-1", correoAna, "Ana",
                correoAna, CLAVE).id();
        ana = usuarioRepository.findByEmail(correoAna).orElseThrow().getId();
        beto = usuarioService.crearColaborador(empresaId, null, "Beto", "beto" + numero + "@envivo.test", CLAVE,
                RolAcceso.EDITOR).id();
        procesoId = procesoService.crear(empresaId, ana, "Order fulfillment", "Checkout to delivery", "Fulfillment").id();
        Long tienda = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        lane = laneService.crear(empresaId, ana, tienda, "Sales",
                rolProcesoService.crear(empresaId, ana, "Sales", null).id()).id();
    }

    @Test
    @DisplayName("El primero que edita lo tiene; el segundo ve de quien es")
    void adquirir() {
        BloqueoResponse primero = enVivo.adquirir(empresaId, ana, procesoId);
        BloqueoResponse segundo = enVivo.adquirir(empresaId, beto, procesoId);

        assertThat(primero.mio()).isTrue();
        assertThat(primero.nombre()).isEqualTo("Ana");
        assertThat(segundo.mio()).isFalse();
        assertThat(segundo.libre()).isFalse();
        assertThat(segundo.nombre()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("Sin latido se pierde, y lo puede tomar otro")
    void vence() {
        enVivo.adquirir(empresaId, ana, procesoId);
        jdbc.update("update bloqueos_edicion set ultimo_latido = ultimo_latido - interval '1 hour' where proceso_id = ?",
                procesoId);

        assertThat(enVivo.estado(empresaId, beto, procesoId).libre()).isTrue();
        assertThat(enVivo.adquirir(empresaId, beto, procesoId).mio()).isTrue();
    }

    @Test
    @DisplayName("Tomar el control queda en la bitacora, y a quien lo perdio su latido le dice 409")
    void tomarControl() {
        enVivo.adquirir(empresaId, ana, procesoId);

        BloqueoResponse tomado = enVivo.tomarControl(empresaId, beto, procesoId);

        assertThat(tomado.mio()).isTrue();
        assertThat(tomado.nombre()).isEqualTo("Beto");
        assertThatThrownBy(() -> enVivo.latir(empresaId, ana, procesoId)).isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Beto");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "select count(*) from bitacora where empresa_id = ? and accion = 'CONTROL_TOMADO'", Integer.class,
                empresaId)).isEqualTo(1));
    }

    @Test
    @DisplayName("Soltar lo deja libre; quien no lo tiene no lo suelta")
    void soltar() {
        enVivo.adquirir(empresaId, ana, procesoId);

        assertThat(enVivo.soltar(empresaId, beto, procesoId).nombre()).isEqualTo("Ana");
        assertThat(enVivo.soltar(empresaId, ana, procesoId).libre()).isTrue();
    }

    @Test
    @DisplayName("Otra tienda no se conecta ni ve el bloqueo")
    void aislamiento() {
        int numero = ++tiendas;
        String correo = "otra" + numero + "@envivo.test";
        Long otra = empresaService.registrar("Otra " + numero, "9481" + numero + "000-1", correo, "Otra", correo, CLAVE)
                .id();
        Long suya = usuarioRepository.findByEmail(correo).orElseThrow().getId();

        assertThatThrownBy(() -> enVivo.conectar(otra, suya, procesoId)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> enVivo.adquirir(otra, suya, procesoId))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Una persona no abre mas conexiones del tope")
    void tope() {
        enVivo.conectar(empresaId, ana, procesoId, new Oyente());
        enVivo.conectar(empresaId, ana, procesoId, new Oyente());

        assertThatThrownBy(() -> enVivo.conectar(empresaId, ana, procesoId, new Oyente()))
                .isInstanceOf(DemasiadosIntentosException.class);
    }

    @Test
    @DisplayName("Quien mira oye la presencia, lo que se guarda, el bloqueo y el control tomado, con NOTIFY")
    void avisos() {
        Oyente oyente = new Oyente();
        enVivo.conectar(empresaId, ana, procesoId, oyente);
        await().atMost(Duration.ofSeconds(10)).until(() -> oyente.oyoConTexto("presencia", "Ana"));

        actividadService.crear(empresaId, beto, lane, "Review", null, TipoActividad.USUARIO, 0, 0);
        await().atMost(Duration.ofSeconds(10)).until(() -> oyente.oyoConTexto("guardado", "Beto"));

        enVivo.adquirir(empresaId, ana, procesoId);
        await().atMost(Duration.ofSeconds(10)).until(() -> oyente.oyo("bloqueo"));

        enVivo.tomarControl(empresaId, beto, procesoId);
        await().atMost(Duration.ofSeconds(10)).until(() -> oyente.oyoConTexto("control-tomado", "Ana"));
    }

    @Test
    @DisplayName("Publicar se avisa con el numero de la version")
    void publicado() {
        Long inicio = eventoService.crear(empresaId, ana, lane, "Start", TipoEvento.INICIO, 0, 0).id();
        Long tarea = actividadService.crear(empresaId, ana, lane, "Review", null, TipoActividad.USUARIO, 180, 0).id();
        Long fin = eventoService.crear(empresaId, ana, lane, "End", TipoEvento.FIN, 360, 0).id();
        arcoService.crear(empresaId, ana, DatosDeArco.entre(inicio, tarea));
        arcoService.crear(empresaId, ana, DatosDeArco.entre(tarea, fin));
        Oyente oyente = new Oyente();
        enVivo.conectar(empresaId, beto, procesoId, oyente);

        procesoService.cambiarEstado(empresaId, procesoId, ana, EstadoProceso.PUBLICADO,
                procesoService.obtener(empresaId, procesoId, false).version());

        await().atMost(Duration.ofSeconds(10)).until(() -> oyente.oyoConTexto("publicado", "\"numero\":1"));
    }

    @Test
    @DisplayName("Otro proceso no se oye")
    void soloSuProceso() {
        Oyente oyente = new Oyente();
        enVivo.conectar(empresaId, ana, procesoId, oyente);
        Long otro = procesoService.crear(empresaId, ana, "Other", "Another", "Misc").id();
        Long otraPool = poolService.listarPorProceso(empresaId, otro).getFirst().id();
        Long otraLane = laneService.crear(empresaId, ana, otraPool, "Elsewhere",
                rolProcesoService.crear(empresaId, ana, "Elsewhere", null).id()).id();
        actividadService.crear(empresaId, beto, otraLane, "Over there", null, TipoActividad.USUARIO, 0, 0);
        // Algo del proceso propio, despues: cuando llegue, lo de antes ya habria llegado
        actividadService.crear(empresaId, ana, lane, "Here", null, TipoActividad.USUARIO, 0, 0);

        await().atMost(Duration.ofSeconds(10)).until(() -> oyente.oyoConTexto("guardado", "Ana"));
        assertThat(oyente.oyoConTexto("guardado", "Beto")).isFalse();
    }

    @Test
    @DisplayName("HTTP: abre un flujo de eventos sin buffer, y sin sesion responde 401")
    void http() throws Exception {
        String token = SesionEnCookies.acceso(mockMvc.perform(SesionEnCookies.login()
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(correoAna, CLAVE))))
                .andExpect(status().isOk()));

        mockMvc.perform(get("/api/v1/procesos/{id}/en-vivo", procesoId).with(SesionEnCookies.conSesion(token)))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("X-Accel-Buffering", "no"));
        mockMvc.perform(get("/api/v1/procesos/{id}/en-vivo", procesoId)).andExpect(status().isUnauthorized());
    }
}
