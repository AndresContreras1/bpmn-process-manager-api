package com.facimus.procesos.modelado;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.facimus.procesos.common.correo.Correo;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.gestion.model.Clasificacion;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.DatosDeFicha;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.MembresiaRolService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RecertificacionService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.DatosDeArco;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.PoolService;
import com.facimus.procesos.security.ApiPrincipalRequestPostProcessor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 64: publicar con aprobacion de punta a punta, por HTTP y con un reloj fijo. La politica de la tienda, la solicitud
 * que congela el borrador, las decisiones de sus aprobadores y la publicacion por el camino de siempre; los avisos y la
 * bitacora; lo de otra tienda que no existe, la base que no deja ver sus filas, y la recertificacion de un proceso.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RevisionYAprobacionIntegracionTest {

    private static final String CLAVE = "un-faro-sobre-la-bahia-quieta";
    private static final Duration ESPERA = Duration.ofSeconds(10);
    private static final Instant AHORA = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final LocalDate HOY = LocalDate.ofInstant(AHORA, ZoneOffset.UTC);
    private static int tiendas;

    @TestConfiguration
    static class RelojFijo {

        @Bean
        @Primary
        Clock relojFijo() {
            return Clock.fixed(AHORA, ZoneOffset.UTC);
        }
    }

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
    private MembresiaRolService membresiaRolService;
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
    private RecertificacionService recertificacionService;
    @Autowired
    private ColaDeTrabajos cola;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper json;

    private ApiPrincipal ana;
    private ApiPrincipal beto;
    private ApiPrincipal carla;
    private ApiPrincipal elio;
    private ApiPrincipal dora;
    private ApiPrincipal ajeno;
    private Long empresaId;
    private Long proceso;
    private ActividadResponse tarea;

    @BeforeEach
    void registrar() {
        vaciarLaCola();
        int numero = ++tiendas;
        String correoAna = "ana" + numero + "@aprobacion.test";
        empresaId = empresaService.registrar("Aprobaciones " + numero, "9651" + numero + "000-1", correoAna, "Ana",
                correoAna, CLAVE).id();
        ana = persona(correoAna, RolAcceso.ADMINISTRADOR);
        beto = persona(colaborador("Beto", "beto" + numero, RolAcceso.EDITOR), RolAcceso.EDITOR);
        carla = persona(colaborador("Carla", "carla" + numero, RolAcceso.EDITOR), RolAcceso.EDITOR);
        elio = persona(colaborador("Elio", "elio" + numero, RolAcceso.EDITOR), RolAcceso.EDITOR);
        dora = persona(colaborador("Dora", "dora" + numero, RolAcceso.SOLO_LECTURA), RolAcceso.SOLO_LECTURA);
        String otro = "otra" + numero + "@aprobacion.test";
        empresaService.registrar("Otra " + numero, "9652" + numero + "000-1", otro, "Otra", otro, CLAVE);
        ajeno = persona(otro, RolAcceso.ADMINISTRADOR);

        proceso = procesoService.crear(empresaId, ana.usuarioId(), "Despacho", "Del pago a la entrega",
                "Fulfillment").id();
        Long tienda = poolService.listarPorProceso(empresaId, proceso).getFirst().id();
        Long ventas = laneService.crear(empresaId, ana.usuarioId(), tienda, "Ventas",
                rolProcesoService.crear(empresaId, ana.usuarioId(), "Ventas", null).id()).id();
        Long inicio = eventoService.crear(empresaId, ana.usuarioId(), ventas, "Inicio", TipoEvento.INICIO, 0, 0).id();
        tarea = actividadService.crear(empresaId, ana.usuarioId(), ventas, "Revisar", null, TipoActividad.USUARIO,
                180, 0);
        Long fin = eventoService.crear(empresaId, ana.usuarioId(), ventas, "Fin", TipoEvento.FIN, 360, 0).id();
        arcoService.crear(empresaId, ana.usuarioId(), DatosDeArco.entre(inicio, tarea.id()));
        arcoService.crear(empresaId, ana.usuarioId(), DatosDeArco.entre(tarea.id(), fin));
    }

    private String colaborador(String nombre, String usuario, RolAcceso rol) {
        return usuarioService.crearColaborador(empresaId, null, nombre, usuario + "@aprobacion.test", CLAVE, rol)
                .email();
    }

    private ApiPrincipal persona(String correoDe, RolAcceso rol) {
        var usuario = usuarioRepository.findByEmail(correoDe).orElseThrow();
        return new ApiPrincipal(usuario.getId(), usuario.getEmpresa().getId(), rol, correoDe,
                ApiPrincipalRequestPostProcessor.SESION, false);
    }

    private ResultActions politica(ApiPrincipal quien, String cuerpo) throws Exception {
        return mvc.perform(put("/api/v1/empresas/actual/politica-de-publicacion")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).with(principal(quien)));
    }

    /** Ana pone la aprobacion: esas aprobaciones, de esas personas. */
    private void conAprobacion(int necesarias, ApiPrincipal... aprobadores) throws Exception {
        String ids = String.join(",", Arrays.stream(aprobadores).map(una -> String.valueOf(una.usuarioId()))
                .toList());
        Long version = jdbc.queryForList("select version from politicas_publicacion where empresa_id = ?", Long.class,
                empresaId).stream().findFirst().orElse(null);
        politica(ana, "{\"modo\":\"CON_APROBACION\",\"aprobacionesNecesarias\":" + necesarias + ",\"aprobadores\":["
                + ids + "],\"version\":" + version + "}").andExpect(status().isOk());
    }

    private void directa() throws Exception {
        Long version = jdbc.queryForObject("select version from politicas_publicacion where empresa_id = ?",
                Long.class, empresaId);
        politica(ana, "{\"modo\":\"DIRECTA\",\"version\":" + version + "}").andExpect(status().isOk());
    }

    private ResultActions pedir(ApiPrincipal quien, String cuerpo) throws Exception {
        return mvc.perform(post("/api/v1/procesos/{id}/solicitudes", proceso).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo).with(principal(quien)));
    }

    private ResultActions decidir(ApiPrincipal quien, long solicitud, String accion, String cuerpo) throws Exception {
        return mvc.perform(post("/api/v1/solicitudes/{id}/" + accion, solicitud).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo).with(principal(quien)));
    }

    private ResultActions publicarDirecto(ApiPrincipal quien) throws Exception {
        Long version = procesoService.obtener(empresaId, proceso, false).version();
        return mvc.perform(patch("/api/v1/procesos/{id}", proceso).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"PUBLICADO\",\"version\":" + version + "}").with(principal(quien)));
    }

    private void renombrar(String nombre) {
        ActividadResponse actual = actividadService.obtener(empresaId, tarea.id());
        actividadService.editar(empresaId, ana.usuarioId(), tarea.id(), nombre, null, TipoActividad.USUARIO, null,
                actual.posicionX(), actual.posicionY(), actual.version());
    }

    private long id(ResultActions resultado) throws Exception {
        return leer(resultado).get("id").asLong();
    }

    private JsonNode leer(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private int notificaciones(ApiPrincipal quien, String tipo) {
        return jdbc.queryForObject("select count(*) from notificaciones where usuario_id = ? and tipo = ?",
                Integer.class, quien.usuarioId(), tipo);
    }

    private int enBitacora(String accion) {
        return jdbc.queryForObject("select count(*) from bitacora where empresa_id = ? and accion = ?", Integer.class,
                empresaId, accion);
    }

    private void vaciarLaCola() {
        while (cola.procesarUno()) {
            // la cola, hasta vaciarse
        }
    }

    @Test
    @DisplayName("la politica la lee cualquier rol y la cambia el administrador, con su version y en la bitacora")
    void politica() throws Exception {
        mvc.perform(get("/api/v1/empresas/actual/politica-de-publicacion").with(principal(dora)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.modo").value("DIRECTA"))
                .andExpect(jsonPath("$.aprobacionesNecesarias").value(1))
                .andExpect(jsonPath("$.version").doesNotExist());
        String lista = "{\"modo\":\"CON_APROBACION\",\"aprobacionesNecesarias\":2,\"aprobadores\":["
                + carla.usuarioId() + "," + elio.usuarioId() + "]}";
        politica(beto, lista).andExpect(status().isForbidden());

        politica(ana, lista).andExpect(status().isOk()).andExpect(jsonPath("$.modo").value("CON_APROBACION"))
                .andExpect(jsonPath("$.aprobadores[0].nombre").value("Carla"))
                .andExpect(jsonPath("$.aprobadores[1].activo").value(true))
                .andExpect(jsonPath("$.version").value(0));
        // Quien la guardo despues gana: una version vieja es un 409
        politica(ana, "{\"modo\":\"DIRECTA\",\"version\":7}").andExpect(status().isConflict());

        politica(ana, "{\"modo\":\"CON_APROBACION\",\"aprobacionesNecesarias\":0,\"aprobadores\":[1],\"version\":0}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.aprobacionesNecesarias").exists());
        politica(ana, "{\"modo\":\"CON_APROBACION\",\"aprobacionesNecesarias\":3,\"aprobadores\":["
                + carla.usuarioId() + "," + elio.usuarioId() + "],\"version\":0}").andExpect(status().isBadRequest());
        politica(ana, "{\"modo\":\"CON_APROBACION\",\"aprobadores\":[" + ajeno.usuarioId() + "],\"version\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['aprobadores[0]']").value("No es una persona activa de la organización."));
        politica(ana, "{\"aprobadores\":[]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.modo").exists());

        await().atMost(ESPERA).until(() -> enBitacora("POLITICA_PUBLICACION_CAMBIADA") == 1);
        // La politica de otra tienda no es esta
        mvc.perform(get("/api/v1/empresas/actual/politica-de-publicacion").with(principal(ajeno)))
                .andExpect(jsonPath("$.modo").value("DIRECTA"));
    }

    @Test
    @DisplayName("D32: con la aprobacion encendida publicar directo es un 409 tambien para el administrador; apagada, "
            + "publica, y pedir una revision es el 409")
    void lasDosPoliticas() throws Exception {
        conAprobacion(1, carla);
        publicarDirecto(ana).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("hay que pedir una revisión")));
        assertThat(jdbc.queryForObject("select count(*) from versiones_proceso where proceso_id = ?", Integer.class,
                proceso)).isZero();

        directa();
        pedir(beto, "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("publica directo")));
        publicarDirecto(beto).andExpect(status().isOk()).andExpect(jsonPath("$.versionPublicada").value(1));
    }

    @Test
    @DisplayName("la solicitud congela el borrador, la aprueban otras personas y la ultima aprobacion publica lo "
            + "congelado")
    void flujoCompleto() throws Exception {
        conAprobacion(2, carla, elio);

        long solicitud = id(pedir(beto, "{\"markdown\":\"**Primera** version <script>x</script>\"}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/solicitudes/")))
                .andExpect(jsonPath("$.estado").value("ABIERTA"))
                .andExpect(jsonPath("$.html").value(containsString("<strong>Primera</strong>")))
                .andExpect(jsonPath("$.html").value(not(containsString("<script>"))))
                .andExpect(jsonPath("$.numero").value(1)).andExpect(jsonPath("$.versionBase").doesNotExist())
                .andExpect(jsonPath("$.aprobadores[0].nombre").value("Carla"))
                .andExpect(jsonPath("$.aprobadores[1].nombre").value("Elio"))
                .andExpect(jsonPath("$.diferencia.de").value("ninguna"))
                .andExpect(jsonPath("$.diferencia.agregados[?(@.nombre == 'Revisar')]").exists()));
        assertThat(jdbc.queryForObject("select r.motivo from solicitudes_publicacion s join revisiones_borrador r "
                + "on r.id = s.revision_id where s.id = ?", String.class, solicitud)).isNotBlank();
        pedir(beto, "{}").andExpect(status().isConflict());

        // Lo que se edite despues no es lo que se revisa
        renombrar("Revisar y empacar");

        mvc.perform(get("/api/v1/solicitudes?pendientesDeMi=true").with(principal(carla)))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(solicitud))
                .andExpect(jsonPath("$.content[0].diferencia").doesNotExist());
        mvc.perform(get("/api/v1/solicitudes?pendientesDeMi=true").with(principal(beto)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/procesos/{id}/solicitudes?estado=ABIERTA", proceso).with(principal(dora)))
                .andExpect(jsonPath("$.totalElements").value(1));

        decidir(beto, solicitud, "aprobar", "{}").andExpect(status().isForbidden());
        decidir(dora, solicitud, "aprobar", "{}").andExpect(status().isForbidden());
        decidir(ana, solicitud, "aprobar", "{}").andExpect(status().isForbidden());
        decidir(carla, solicitud, "aprobar", "{\"markdown\":\"Bien\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ABIERTA")).andExpect(jsonPath("$.aprobaciones").value(1));
        decidir(carla, solicitud, "aprobar", "{}").andExpect(status().isConflict());
        mvc.perform(get("/api/v1/solicitudes?pendientesDeMi=true").with(principal(carla)))
                .andExpect(jsonPath("$.totalElements").value(0));

        decidir(elio, solicitud, "aprobar", "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PUBLICADA")).andExpect(jsonPath("$.versionPublicada").value(1))
                .andExpect(jsonPath("$.cerradaPor.nombre").value("Elio"))
                .andExpect(jsonPath("$.decisiones.length()").value(2));

        assertThat(jdbc.queryForObject("select definicion from versiones_proceso where proceso_id = ? and numero = 1",
                String.class, proceso)).contains("\"Revisar\"").doesNotContain("Revisar y empacar");
        mvc.perform(get("/api/v1/procesos/{id}", proceso).with(principal(beto)))
                .andExpect(jsonPath("$.proceso.versionPublicada").value(1))
                .andExpect(jsonPath("$.proceso.borradorPendiente").value(true));

        await().atMost(ESPERA).untilAsserted(() -> {
            assertThat(notificaciones(carla, "APROBACION_PEDIDA")).isOne();
            assertThat(notificaciones(elio, "APROBACION_PEDIDA")).isOne();
            assertThat(notificaciones(beto, "SOLICITUD_APROBADA")).isOne();
            assertThat(notificaciones(beto, "SOLICITUD_PUBLICADA")).isOne();
            assertThat(enBitacora("PUBLICACION_SOLICITADA")).isOne();
            assertThat(enBitacora("SOLICITUD_APROBADA")).isEqualTo(2);
            assertThat(enBitacora("PROCESO_PUBLICADO")).isOne();
        });
        assertThat(jdbc.queryForObject("select referencia_id from notificaciones where usuario_id = ? and tipo = "
                + "'SOLICITUD_PUBLICADA'", Long.class, beto.usuarioId())).isEqualTo(solicitud);
    }

    @Test
    @DisplayName("pedir cambios y rechazar cierran la solicitud, dicen por que y le avisan a quien la pidio")
    void cambiosYRechazo() throws Exception {
        conAprobacion(2, carla, elio);
        long primera = id(pedir(beto, "{}").andExpect(status().isCreated()));

        decidir(carla, primera, "pedir-cambios", "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.markdown").exists());
        decidir(carla, primera, "pedir-cambios", "{\"markdown\":\"Falta el *plazo* <img src=x onerror=alert(1)>\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CAMBIOS_PEDIDOS"))
                .andExpect(jsonPath("$.decisiones[0].html").value(containsString("<em>plazo</em>")))
                .andExpect(jsonPath("$.decisiones[0].html").value(not(containsString("<img"))));
        decidir(elio, primera, "aprobar", "{}").andExpect(status().isConflict());

        long segunda = id(pedir(beto, "{}").andExpect(status().isCreated()));
        decidir(elio, segunda, "rechazar", "{\"markdown\":\"No aplica\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"));

        await().atMost(ESPERA).untilAsserted(() -> {
            assertThat(notificaciones(beto, "CAMBIOS_PEDIDOS")).isOne();
            assertThat(notificaciones(beto, "SOLICITUD_RECHAZADA")).isOne();
            assertThat(enBitacora("SOLICITUD_CAMBIOS_PEDIDOS")).isOne();
            assertThat(enBitacora("SOLICITUD_RECHAZADA")).isOne();
        });
        assertThat(jdbc.queryForObject("select count(*) from versiones_proceso where proceso_id = ?", Integer.class,
                proceso)).isZero();
    }

    @Test
    @DisplayName("quien la pidio la retira; otra persona solo si administra el proceso")
    void retirar() throws Exception {
        conAprobacion(1, carla);
        long solicitud = id(pedir(beto, "{}"));

        mvc.perform(post("/api/v1/solicitudes/{id}/retirar", solicitud).with(principal(carla)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/solicitudes/{id}/retirar", solicitud).with(principal(beto)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RETIRADA"))
                .andExpect(jsonPath("$.cerradaPor.nombre").value("Beto"));
        mvc.perform(post("/api/v1/solicitudes/{id}/retirar", solicitud).with(principal(beto)))
                .andExpect(status().isConflict());

        long otra = id(pedir(beto, "{}").andExpect(status().isCreated()));
        mvc.perform(post("/api/v1/solicitudes/{id}/retirar", otra).with(principal(ana))).andExpect(status().isOk());
        await().atMost(ESPERA).until(() -> enBitacora("SOLICITUD_RETIRADA") == 2);
    }

    @Test
    @DisplayName("si la vigente cambio desde que se pidio, la aprobacion no publica: queda aprobada sin publicar")
    void vigenteQueCambio() throws Exception {
        conAprobacion(1, carla);
        long solicitud = id(pedir(beto, "{}"));

        // Se apaga la politica, se publica directo y se vuelve a encender: lo aprobado ya no es esa diferencia
        directa();
        renombrar("Revisar dos veces");
        publicarDirecto(ana).andExpect(status().isOk());
        conAprobacion(1, carla);

        decidir(carla, solicitud, "aprobar", "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA_SIN_PUBLICAR"))
                .andExpect(jsonPath("$.motivo").value(containsString("La versión vigente cambió")))
                .andExpect(jsonPath("$.versionPublicada").doesNotExist());
        assertThat(jdbc.queryForObject("select count(*) from versiones_proceso where proceso_id = ?", Integer.class,
                proceso)).isOne();
        await().atMost(ESPERA).untilAsserted(() -> {
            assertThat(notificaciones(beto, "SOLICITUD_SIN_PUBLICAR")).isOne();
            assertThat(enBitacora("SOLICITUD_SIN_PUBLICAR")).isOne();
        });
    }

    @Test
    @DisplayName("con un rol aprueban sus personas, y quien cambio el proceso por ultima vez no aprueba (D31)")
    void conRolYSeparacionDeFunciones() throws Exception {
        Long rol = rolProcesoService.crear(empresaId, ana.usuarioId(), "Aprobadores", null).id();
        membresiaRolService.reemplazar(empresaId, ana.usuarioId(), carla.usuarioId(), List.of(rol));
        membresiaRolService.reemplazar(empresaId, ana.usuarioId(), elio.usuarioId(), List.of(rol));
        politica(ana, "{\"modo\":\"CON_APROBACION\",\"aprobacionesNecesarias\":2,\"rolProcesoId\":" + rol + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.rolProceso").value("Aprobadores"));

        // Carla edita el proceso: la separacion de funciones la saca de los aprobadores y no quedan dos
        Long version = procesoService.obtener(empresaId, proceso, false).version();
        mvc.perform(put("/api/v1/procesos/{id}", proceso).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Despacho\",\"descripcion\":\"Del pago a la entrega, con cambios\","
                        + "\"categoria\":\"Fulfillment\",\"version\":" + version + "}")
                .with(principal(carla))).andExpect(status().isOk());
        pedir(beto, "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("solo 1 personas pueden aprobar")));

        politica(ana, "{\"modo\":\"CON_APROBACION\",\"aprobacionesNecesarias\":1,\"rolProcesoId\":" + rol
                + ",\"version\":0}").andExpect(status().isOk());
        pedir(beto, "{}").andExpect(status().isCreated()).andExpect(jsonPath("$.aprobadores.length()").value(1))
                .andExpect(jsonPath("$.aprobadores[0].nombre").value("Elio"));
    }

    @Test
    @DisplayName("lo de otra tienda no existe: su solicitud y su proceso responden 404, y su bandeja no los ve")
    void otraTienda() throws Exception {
        conAprobacion(1, carla);
        long solicitud = id(pedir(beto, "{}"));

        mvc.perform(get("/api/v1/solicitudes/{id}", solicitud).with(principal(ajeno))).andExpect(status().isNotFound());
        decidir(ajeno, solicitud, "aprobar", "{}").andExpect(status().isNotFound());
        decidir(ajeno, solicitud, "rechazar", "{\"markdown\":\"x\"}").andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/solicitudes/{id}/retirar", solicitud).with(principal(ajeno)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/procesos/{id}/solicitudes", proceso).contentType(MediaType.APPLICATION_JSON)
                .content("{}").with(principal(ajeno))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/procesos/{id}/solicitudes", proceso).with(principal(ajeno)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/solicitudes").with(principal(ajeno))).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/solicitudes").with(principal(dora))).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/solicitudes/{id}", solicitud).with(principal(dora))).andExpect(status().isOk())
                .andExpect(jsonPath("$.proceso").value("Despacho"));
    }

    @Test
    @DisplayName("la base no deja ver la politica, las solicitudes ni las decisiones de otra tienda")
    void aislamiento() throws Exception {
        conAprobacion(1, carla);
        long solicitud = id(pedir(beto, "{}"));
        decidir(carla, solicitud, "aprobar", "{}").andExpect(status().isOk());

        ContextoDeTienda.deLaTienda(ajeno.empresaId(), () -> {
            for (String tabla : List.of("politicas_publicacion", "solicitudes_publicacion", "aprobaciones")) {
                assertThat(jdbc.queryForObject("select count(*) from " + tabla + " where empresa_id = ?",
                        Integer.class, empresaId)).as(tabla).isZero();
            }
        });
        ContextoDeTienda.deLaTienda(empresaId, () -> assertThat(jdbc.queryForObject(
                "select count(*) from aprobaciones where empresa_id = ?", Integer.class, empresaId)).isOne());
    }

    @Test
    @DisplayName("anonimizar a quien pidio retira sus solicitudes, y a un aprobador lo saca de la lista")
    void anonimizar() throws Exception {
        conAprobacion(1, carla, elio);
        long solicitud = id(pedir(beto, "{}"));

        usuarioService.anonimizar(empresaId, ana.usuarioId(), beto.usuarioId());
        usuarioService.anonimizar(empresaId, ana.usuarioId(), elio.usuarioId());

        mvc.perform(get("/api/v1/solicitudes/{id}", solicitud).with(principal(carla)))
                .andExpect(jsonPath("$.estado").value("RETIRADA"))
                .andExpect(jsonPath("$.solicitante.nombre").value("Persona anonimizada " + beto.usuarioId()));
        mvc.perform(get("/api/v1/empresas/actual/politica-de-publicacion").with(principal(ana)))
                .andExpect(jsonPath("$.aprobadores.length()").value(1))
                .andExpect(jsonPath("$.aprobadores[0].nombre").value("Carla"));
    }

    @Test
    @DisplayName("recertificacion: el dueno recibe un aviso al llegar la fecha y otro al vencer, una vez cada uno, y "
            + "confirmar mueve la fecha")
    void recertificacion() throws Exception {
        procesoService.editarFicha(empresaId, proceso, ana.usuarioId(), new DatosDeFicha(null, beto.usuarioId(), HOY,
                Clasificacion.INTERNA, List.of(), procesoService.obtener(empresaId, proceso, false).version(), 6));

        // El reloj diario cruza tiendas y deja un trabajo por tienda; el trabajo de esta avisa a su duena
        ContextoDeTienda.comoSistema(() -> recertificacionService.encolarAvisos(HOY));
        assertThat(jdbc.queryForObject("select count(*) from trabajos where empresa_id = ? and tipo = "
                + "'recertificacion.avisos'", Integer.class, empresaId)).isOne();
        vaciarLaCola();
        await().atMost(ESPERA).until(() -> notificaciones(beto, "RECERTIFICACION_PENDIENTE") == 1);
        // El mismo dia otra vez no repite nada; al dia siguiente se avisa una vez que vencio, y nunca mas
        assertThat(recertificacionService.avisar(empresaId, HOY)).isZero();
        assertThat(recertificacionService.avisar(empresaId, HOY.plusDays(1))).isOne();
        assertThat(recertificacionService.avisar(empresaId, HOY.plusDays(1))).isZero();
        assertThat(recertificacionService.avisar(empresaId, HOY.plusDays(6))).isZero();
        await().atMost(ESPERA).until(() -> notificaciones(beto, "RECERTIFICACION_VENCIDA") == 1);
        assertThat(notificaciones(beto, "RECERTIFICACION_PENDIENTE")).isOne();

        // Vencida se ve en el proceso
        jdbc.update("update procesos set proxima_revision = ? where id = ?", HOY.minusDays(1), proceso);
        mvc.perform(get("/api/v1/procesos/{id}", proceso).with(principal(dora)))
                .andExpect(jsonPath("$.proceso.revisionVencida").value(true))
                .andExpect(jsonPath("$.proceso.mesesEntreRevisiones").value(6));

        String nota = "{\"nota\":\"Revisado con el transportista\"}";
        mvc.perform(post("/api/v1/procesos/{id}/recertificacion", proceso).contentType(MediaType.APPLICATION_JSON)
                .content(nota).with(principal(carla))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/procesos/{id}/recertificacion", proceso).contentType(MediaType.APPLICATION_JSON)
                .content(nota).with(principal(ajeno))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/procesos/{id}/recertificacion", proceso).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nota\":\"" + "x".repeat(2001) + "\"}").with(principal(beto)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/procesos/{id}/recertificacion", proceso).contentType(MediaType.APPLICATION_JSON)
                .content(nota).with(principal(beto))).andExpect(status().isOk())
                .andExpect(jsonPath("$.proximaRevision").value(HOY.plusMonths(6).toString()))
                .andExpect(jsonPath("$.revisionVencida").value(false));
        // Quien administra tambien la confirma, sin cuerpo
        mvc.perform(post("/api/v1/procesos/{id}/recertificacion", proceso).with(principal(ana)))
                .andExpect(status().isOk());
        await().atMost(ESPERA).until(() -> enBitacora("PROCESO_RECERTIFICADO") == 2);
        assertThat(jdbc.queryForObject("select detalle from bitacora where empresa_id = ? and accion = "
                + "'PROCESO_RECERTIFICADO' order by secuencia limit 1", String.class, empresaId))
                .contains("Revisado con el transportista", "vencida");
    }
}
