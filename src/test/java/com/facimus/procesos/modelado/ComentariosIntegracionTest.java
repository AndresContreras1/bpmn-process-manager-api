package com.facimus.procesos.modelado;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.facimus.procesos.common.correo.Correo;
import com.facimus.procesos.common.correo.CorreoSaliente;
import com.facimus.procesos.common.model.Accion;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.gestion.model.SujetoConcesion;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.Actor;
import com.facimus.procesos.gestion.service.ConcesionService;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.NotificacionService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.Recurso;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.PoolService;
import com.facimus.procesos.security.ApiPrincipalRequestPostProcessor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 63: los comentarios y las notificaciones de punta a punta, por HTTP. Quien solo lee comenta; mencionar es solo a
 * quien puede ver el proceso; resolver, reabrir y borrar quedan en la bitacora. Cada mencion llega a la tabla, al flujo
 * en vivo de su destinatario (con la escucha de avisos encendida, como en produccion) y al correo segun lo que eligio.
 * Lo de otra tienda no existe, y la base no deja ver las filas de otra.
 */
@SpringBootTest(properties = "avisos.escuchar=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ComentariosIntegracionTest {

    private static final String CLAVE = "un-faro-sobre-la-bahia-quieta";
    private static final Duration ESPERA = Duration.ofSeconds(10);
    private static int tiendas;

    @MockitoBean
    private Correo correo;

    @Autowired
    private MockMvc mvc;
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
    private ConcesionService concesionService;
    @Autowired
    private NotificacionService notificacionService;
    @Autowired
    private ColaDeTrabajos cola;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper json;

    private ApiPrincipal ana;
    private ApiPrincipal beto;
    private ApiPrincipal carla;
    private ApiPrincipal dora;
    private ApiPrincipal ajeno;
    private Long proceso;
    private String tarea;

    /** Un emisor que guarda lo que le mandan, en vez de escribirlo en una respuesta. */
    private static final class Oyente extends SseEmitter {

        private final List<String> recibido = new CopyOnWriteArrayList<>();

        @Override
        public void send(SseEventBuilder evento) throws IOException {
            StringBuilder texto = new StringBuilder();
            evento.build().forEach(parte -> texto.append(parte.getData()));
            recibido.add(texto.toString());
        }

        boolean oyo(String tipo, String contenido) {
            return recibido.stream().anyMatch(uno -> uno.contains("event:" + tipo) && uno.contains(contenido));
        }
    }

    @BeforeEach
    void registrar() {
        while (cola.procesarUno()) {
            // Lo que otra prueba dejo en la cola sale ahora, antes de mirar los correos de esta
        }
        clearInvocations(correo);
        int numero = ++tiendas;
        String correoAna = "ana" + numero + "@comentarios.test";
        Long empresaId = empresaService.registrar("Comentarios " + numero, "9641" + numero + "000-1", correoAna,
                "Ana", correoAna, CLAVE).id();
        ana = persona(correoAna, RolAcceso.ADMINISTRADOR);
        beto = persona(colaborador(empresaId, "Beto", "beto" + numero, RolAcceso.EDITOR), RolAcceso.EDITOR);
        carla = persona(colaborador(empresaId, "Carla", "carla" + numero, RolAcceso.SOLO_LECTURA),
                RolAcceso.SOLO_LECTURA);
        dora = persona(colaborador(empresaId, "Dora", "dora" + numero, RolAcceso.EDITOR), RolAcceso.EDITOR);
        String otro = "otra" + numero + "@comentarios.test";
        empresaService.registrar("Otra " + numero, "9642" + numero + "000-1", otro, "Otra", otro, CLAVE);
        ajeno = persona(otro, RolAcceso.ADMINISTRADOR);

        proceso = procesoService.crear(empresaId, ana.usuarioId(), "Despacho", "Del pago a la entrega",
                "Fulfillment").id();
        Long tienda = poolService.listarPorProceso(empresaId, proceso).getFirst().id();
        Long rol = rolProcesoService.crear(empresaId, ana.usuarioId(), "Bodega", null).id();
        Long lane = laneService.crear(empresaId, ana.usuarioId(), tienda, "Bodega", rol).id();
        Long empacar = actividadService.crear(empresaId, ana.usuarioId(), lane, "Empacar", null,
                TipoActividad.USUARIO, 160, 80).id();
        tarea = jdbc.queryForObject("select bpmn_id from nodos_flujo where id = ?", String.class, empacar);
        // Ver el proceso queda para Beto y Carla (y para quien administra): Dora no lo ve
        Actor admin = new Actor(ana.usuarioId(), empresaId, RolAcceso.ADMINISTRADOR);
        concesionService.conceder(admin, Recurso.proceso(proceso), Accion.VER, SujetoConcesion.PERSONA,
                beto.usuarioId(), null);
        concesionService.conceder(admin, Recurso.proceso(proceso), Accion.VER, SujetoConcesion.PERSONA,
                carla.usuarioId(), null);
    }

    private String colaborador(Long empresaId, String nombre, String usuario, RolAcceso rol) {
        return usuarioService.crearColaborador(empresaId, null, nombre, usuario + "@comentarios.test", CLAVE, rol)
                .email();
    }

    private ApiPrincipal persona(String correoDe, RolAcceso rol) {
        var usuario = usuarioRepository.findByEmail(correoDe).orElseThrow();
        return new ApiPrincipal(usuario.getId(), usuario.getEmpresa().getId(), rol, correoDe,
                ApiPrincipalRequestPostProcessor.SESION, false);
    }

    private ResultActions comentar(ApiPrincipal quien, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(post("/api/v1/procesos/{id}/comentarios", proceso).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)).with(principal(quien)));
    }

    private ResultActions responder(ApiPrincipal quien, long comentario, Map<String, Object> cuerpo)
            throws Exception {
        return mvc.perform(post("/api/v1/comentarios/{id}/respuestas", comentario)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo))
                .with(principal(quien)));
    }

    private ResultActions accion(ApiPrincipal quien, long comentario, String cual) throws Exception {
        return mvc.perform(post("/api/v1/comentarios/{id}/" + cual, comentario).with(principal(quien)));
    }

    private ResultActions hilos(ApiPrincipal quien, String filtros) throws Exception {
        return mvc.perform(get("/api/v1/procesos/{id}/comentarios" + filtros, proceso).with(principal(quien)));
    }

    private long id(ResultActions resultado) throws Exception {
        return leer(resultado).get("id").asLong();
    }

    private JsonNode leer(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private static Map<String, Object> texto(String markdown, Object... menciones) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("markdown", markdown);
        cuerpo.put("menciones", List.of(menciones));
        return cuerpo;
    }

    private int notificacionesDe(ApiPrincipal quien) {
        return jdbc.queryForObject("select count(*) from notificaciones where usuario_id = ?", Integer.class,
                quien.usuarioId());
    }

    private int enBitacora(ApiPrincipal quien, String accion) {
        return jdbc.queryForObject("select count(*) from bitacora where empresa_id = ? and accion = ?", Integer.class,
                quien.empresaId(), accion);
    }

    private List<CorreoSaliente> correosA(ApiPrincipal quien) {
        ArgumentCaptor<CorreoSaliente> enviados = ArgumentCaptor.forClass(CorreoSaliente.class);
        verify(correo, atLeastOnce()).enviar(enviados.capture());
        return enviados.getAllValues().stream().filter(uno -> uno.para().equals(quien.email())).toList();
    }

    private void vaciarLaCola() {
        while (cola.procesarUno()) {
            // la cola, hasta vaciarse
        }
    }

    @Test
    @DisplayName("quien solo lee abre un hilo sobre un elemento, con su HTML saneado, y se filtra por elemento y estado")
    void hilos() throws Exception {
        Map<String, Object> cuerpo = texto("**Ojo** con la cinta <script>x</script>");
        cuerpo.put("bpmnId", tarea);
        long hilo = id(comentar(carla, cuerpo).andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/comentarios/")))
                .andExpect(jsonPath("$.html").value(containsString("<strong>Ojo</strong>")))
                .andExpect(jsonPath("$.html").value(not(containsString("<script>"))))
                .andExpect(jsonPath("$.autor.nombre").value("Carla"))
                .andExpect(jsonPath("$.estado").value("ABIERTO")).andExpect(jsonPath("$.bpmnId").value(tarea)));
        comentar(beto, texto("Del proceso entero")).andExpect(status().isCreated());
        responder(beto, hilo, texto("Doble cinta")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.padreId").value(hilo)).andExpect(jsonPath("$.estado").doesNotExist());

        hilos(carla, "").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].comentario.markdown").value("Del proceso entero"))
                .andExpect(jsonPath("$.content[1].respuestas[0].markdown").value("Doble cinta"));
        hilos(carla, "?bpmnId=" + tarea).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].comentario.id").value(hilo));
        hilos(carla, "?estado=RESUELTO").andExpect(jsonPath("$.totalElements").value(0));
        hilos(carla, "?tamano=51").andExpect(status().isBadRequest());
        hilos(dora, "").andExpect(status().isNotFound());
        hilos(ajeno, "").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("el ancla tiene que existir, el texto tiene tope, y una mencion de quien no ve el proceso es un 400")
    void validaciones() throws Exception {
        Map<String, Object> elementoInexistente = texto("x");
        elementoInexistente.put("bpmnId", "Activity_no_existe");
        comentar(beto, elementoInexistente).andExpect(status().isNotFound());
        Map<String, Object> versionInexistente = texto("x");
        versionInexistente.put("versionDelProceso", 3);
        comentar(beto, versionInexistente).andExpect(status().isNotFound());
        comentar(beto, texto("x".repeat(10_001))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.markdown").exists());
        comentar(beto, texto(" ")).andExpect(status().isBadRequest());
        Object[] muchas = new Object[21];
        java.util.Arrays.fill(muchas, carla.usuarioId());
        comentar(beto, texto("x", muchas)).andExpect(status().isBadRequest());

        // Quien no ve el proceso, alguien de otra tienda y alguien que no existe responden igual
        comentar(beto, texto("x", carla.usuarioId(), dora.usuarioId(), ajeno.usuarioId(), Long.MAX_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['menciones[0]']").doesNotExist())
                .andExpect(jsonPath("$.errors['menciones[1]']").value(
                        "No es una persona de la organización que pueda ver este proceso."))
                .andExpect(jsonPath("$.errors['menciones[2]']").value(
                        "No es una persona de la organización que pueda ver este proceso."))
                .andExpect(jsonPath("$.errors['menciones[3]']").value(
                        "No es una persona de la organización que pueda ver este proceso."));
        assertThat(jdbc.queryForObject("select count(*) from comentarios where proceso_id = ?", Integer.class,
                proceso)).isZero();
        comentar(ajeno, texto("x")).andExpect(status().isNotFound());
        comentar(dora, texto("x")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("un hilo se resuelve y se reabre desde su primer comentario, y los dos quedan en la bitacora")
    void resolverYReabrir() throws Exception {
        long hilo = id(comentar(carla, texto("¿Quién revisa la dirección?")));
        long respuesta = id(responder(beto, hilo, texto("Yo")));

        accion(beto, respuesta, "resolver").andExpect(status().isConflict());
        accion(carla, hilo, "resolver").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RESUELTO"))
                .andExpect(jsonPath("$.resueltoPor.nombre").value("Carla"));
        accion(beto, hilo, "resolver").andExpect(status().isConflict());
        hilos(beto, "?estado=RESUELTO").andExpect(jsonPath("$.totalElements").value(1));
        accion(ajeno, hilo, "reabrir").andExpect(status().isNotFound());
        accion(dora, hilo, "reabrir").andExpect(status().isNotFound());
        accion(beto, hilo, "reabrir").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ABIERTO"))
                .andExpect(jsonPath("$.resueltoPor").doesNotExist());
        accion(beto, hilo, "reabrir").andExpect(status().isConflict());

        await().atMost(ESPERA).untilAsserted(() -> {
            assertThat(enBitacora(ana, "COMENTARIO_RESUELTO")).isOne();
            assertThat(enBitacora(ana, "COMENTARIO_REABIERTO")).isOne();
        });
    }

    @Test
    @DisplayName("solo el autor edita, con su version; borra el autor o un administrador, y el hilo sigue si tiene "
            + "respuestas")
    void editarYBorrar() throws Exception {
        long hilo = id(comentar(carla, texto("Primera idea")));
        long respuesta = id(responder(beto, hilo, texto("Segunda")));

        mvc.perform(put("/api/v1/comentarios/{id}", hilo).contentType(MediaType.APPLICATION_JSON)
                .content("{\"markdown\":\"Otra\",\"version\":0}").with(principal(beto)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/comentarios/{id}", hilo).contentType(MediaType.APPLICATION_JSON)
                .content("{\"markdown\":\"Otra\",\"version\":5}").with(principal(carla)))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/comentarios/{id}", hilo).contentType(MediaType.APPLICATION_JSON)
                .content("{\"markdown\":\"Mejor _idea_\",\"version\":0}").with(principal(carla)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.html").value("<p>Mejor <em>idea</em></p>"))
                .andExpect(jsonPath("$.editadoEn").isNotEmpty());
        mvc.perform(put("/api/v1/comentarios/{id}", hilo).contentType(MediaType.APPLICATION_JSON)
                .content("{\"markdown\":\"x\",\"version\":1}").with(principal(ajeno)))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/v1/comentarios/{id}", hilo).with(principal(beto))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/comentarios/{id}", hilo).with(principal(ajeno)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/comentarios/{id}", hilo).with(principal(ana))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/comentarios/{id}", hilo).with(principal(ana))).andExpect(status().isNotFound());
        hilos(beto, "").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].comentario.eliminado").value(true))
                .andExpect(jsonPath("$.content[0].comentario.markdown").value(""))
                .andExpect(jsonPath("$.content[0].respuestas[0].markdown").value("Segunda"));
        // Un hilo sin texto sigue: se le responde, y sin respuestas que queden, desaparece
        mvc.perform(delete("/api/v1/comentarios/{id}", respuesta).with(principal(beto)))
                .andExpect(status().isNoContent());
        hilos(beto, "").andExpect(jsonPath("$.totalElements").value(0));

        await().atMost(ESPERA).untilAsserted(() -> assertThat(enBitacora(ana, "COMENTARIO_ELIMINADO")).isEqualTo(2));
    }

    @Test
    @DisplayName("una mencion llega a la tabla, al flujo en vivo y al correo de su destinatario, y solo el la ve")
    void notificaciones() throws Exception {
        Oyente flujo = new Oyente();
        notificacionService.conectar(beto.empresaId(), beto.usuarioId(), flujo);
        // El primero lo manda la propia peticion; los demas llegan por la escucha de avisos, como JSON
        assertThat(flujo.oyo("conteo", "noLeidas=0")).isTrue();

        long hilo = id(comentar(carla, texto("Mira esto, *Beto* <img src=x onerror=alert(1)>", beto.usuarioId())));

        await().atMost(ESPERA).until(() -> flujo.oyo("notificacion", "\"MENCION\""));
        assertThat(flujo.oyo("conteo", "\"noLeidas\":1")).isTrue();
        JsonNode pagina = leer(mvc.perform(get("/api/v1/notificaciones").with(principal(beto)))
                .andExpect(status().isOk()));
        assertThat(pagina.get("totalElements").asInt()).isOne();
        JsonNode mencion = pagina.get("content").get(0);
        assertThat(mencion.get("tipo").asString()).isEqualTo("MENCION");
        assertThat(mencion.get("proceso").asString()).isEqualTo("Despacho");
        assertThat(mencion.get("autor").asString()).isEqualTo("Carla");
        assertThat(mencion.get("referenciaId").asLong()).isEqualTo(hilo);
        long notificacion = mencion.get("id").asLong();

        // Solo la ve su destinatario: otra persona de la tienda y otra tienda reciben 404
        mvc.perform(get("/api/v1/notificaciones").with(principal(carla))).andExpect(jsonPath("$.totalElements")
                .value(0));
        mvc.perform(post("/api/v1/notificaciones/{id}/leida", notificacion).with(principal(carla)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/notificaciones/{id}/leida", notificacion).with(principal(ajeno)))
                .andExpect(status().isNotFound());

        // Por correo, en seguida: sin el texto del comentario
        vaciarLaCola();
        CorreoSaliente salido = correosA(beto).getFirst();
        assertThat(salido.asunto()).isEqualTo("Carla te mencionó en Despacho");
        assertThat(salido.html()).contains("/procesos/" + proceso + "?comentario=" + hilo)
                .doesNotContain("Mira esto", "onerror");

        mvc.perform(get("/api/v1/notificaciones/conteo").with(principal(beto))).andExpect(jsonPath("$.noLeidas")
                .value(1));
        mvc.perform(post("/api/v1/notificaciones/{id}/leida", notificacion).with(principal(beto)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.leidaEn").isNotEmpty());
        mvc.perform(get("/api/v1/notificaciones?noLeidas=true").with(principal(beto)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(post("/api/v1/notificaciones/leidas").with(principal(beto))).andExpect(status().isOk())
                .andExpect(jsonPath("$.noLeidas").value(0));
    }

    @Test
    @DisplayName("responder avisa a quien escribio en el hilo, resolverlo espera al resumen, y cada uno elige su correo")
    void respuestasYResumen() throws Exception {
        mvc.perform(get("/api/v1/notificaciones/preferencias").with(principal(beto))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("MENCION")).andExpect(jsonPath("$[0].porDefecto").value(true))
                .andExpect(jsonPath("$[2].envio").value("RESUMEN_DIARIO"));
        mvc.perform(put("/api/v1/notificaciones/preferencias").contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferencias\":[{\"tipo\":\"RESPUESTA\",\"envio\":\"NUNCA\"}]}").with(principal(beto)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[1].envio").value("NUNCA"))
                .andExpect(jsonPath("$[1].porDefecto").value(false));
        mvc.perform(put("/api/v1/notificaciones/preferencias").contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferencias\":[]}").with(principal(beto))).andExpect(status().isBadRequest());

        long hilo = id(comentar(beto, texto("¿Empacamos los sábados?")));
        responder(carla, hilo, texto("Sí")).andExpect(status().isCreated());
        await().atMost(ESPERA).until(() -> notificacionesDe(beto) == 1);
        accion(carla, hilo, "resolver").andExpect(status().isOk());
        await().atMost(ESPERA).until(() -> notificacionesDe(beto) == 2);
        assertThat(jdbc.queryForList("select tipo || ':' || envio from notificaciones where usuario_id = ? "
                + "order by id", String.class, beto.usuarioId()))
                .containsExactly("RESPUESTA:NUNCA", "HILO_RESUELTO:RESUMEN_DIARIO");

        vaciarLaCola();
        assertThat(jdbc.queryForObject("select count(*) from trabajos where empresa_id = ? and tipo = "
                + "'notificaciones.correo'", Integer.class, beto.empresaId())).isZero();
        ContextoDeTienda.comoSistema(notificacionService::encolarResumenes);
        vaciarLaCola();
        List<CorreoSaliente> resumen = correosA(beto);
        assertThat(resumen).singleElement().satisfies(uno -> {
            assertThat(uno.asunto()).isEqualTo("Tus notificaciones del día: 1 sin leer");
            assertThat(uno.texto()).contains("Carla resolvió un hilo en Despacho");
        });
    }

    @Test
    @DisplayName("HTTP: el flujo de notificaciones abre sin buffer, y sin sesion responde 401")
    void flujo() throws Exception {
        mvc.perform(get("/api/v1/notificaciones/flujo").with(principal(carla))).andExpect(request().asyncStarted())
                .andExpect(header().string("X-Accel-Buffering", "no"));
        mvc.perform(get("/api/v1/notificaciones/flujo")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("la base no deja ver los comentarios ni las notificaciones de otra tienda")
    void aislamiento() throws Exception {
        comentar(carla, texto("Hola", beto.usuarioId())).andExpect(status().isCreated());
        await().atMost(ESPERA).until(() -> notificacionesDe(beto) == 1);

        ContextoDeTienda.deLaTienda(ajeno.empresaId(), () -> {
            assertThat(jdbc.queryForObject("select count(*) from comentarios where empresa_id = ?", Integer.class,
                    beto.empresaId())).isZero();
            assertThat(jdbc.queryForObject("select count(*) from notificaciones where empresa_id = ?", Integer.class,
                    beto.empresaId())).isZero();
        });
        ContextoDeTienda.deLaTienda(beto.empresaId(), () -> assertThat(jdbc.queryForObject(
                "select count(*) from comentarios where empresa_id = ?", Integer.class, beto.empresaId())).isOne());
    }

    @Test
    @DisplayName("anonimizar a alguien deja sus comentarios con su seudonimo, tambien donde lo mencionaban, y borra "
            + "sus notificaciones")
    void anonimizar() throws Exception {
        long hilo = id(comentar(carla, texto("Pregunta a @Beto", beto.usuarioId())));
        responder(beto, hilo, texto("Aquí estoy")).andExpect(status().isCreated());
        await().atMost(ESPERA).until(() -> notificacionesDe(beto) == 1);

        usuarioService.anonimizar(ana.empresaId(), ana.usuarioId(), beto.usuarioId());

        String seudonimo = "Persona anonimizada " + beto.usuarioId();
        hilos(carla, "").andExpect(jsonPath("$.content[0].comentario.markdown").value("Pregunta a @" + seudonimo))
                .andExpect(jsonPath("$.content[0].comentario.menciones[0].nombre").value(seudonimo))
                .andExpect(jsonPath("$.content[0].respuestas[0].autor.nombre").value(seudonimo));
        assertThat(notificacionesDe(beto)).isZero();
    }
}
