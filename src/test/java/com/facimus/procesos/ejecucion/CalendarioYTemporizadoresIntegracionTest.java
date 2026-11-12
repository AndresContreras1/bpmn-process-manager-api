package com.facimus.procesos.ejecucion;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.ejecucion.model.ModoCaso;
import com.facimus.procesos.ejecucion.service.CasoService;
import com.facimus.procesos.ejecucion.service.TemporizadorService;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.gestion.service.VersionService;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoTemporizador;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.DatosDeArco;
import com.facimus.procesos.modelado.service.DatosDeTemporizador;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.PoolService;
import com.facimus.procesos.security.ApiPrincipalRequestPostProcessor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 68 de punta a punta, por HTTP y con PostgreSQL: el calendario de la organizacion (quien lo lee, quien lo cambia,
 * lo que cada tienda ve) y los temporizadores de un caso de produccion, con un reloj de pared que la prueba mueve a
 * mano. Nada corre por detras: el planificador se llama a mano, como lo haria su pasada.
 *
 * <p>El reloj empieza el viernes 9 de enero de 2026 a las tres de la tarde en Bogota, lejos de hoy: lo que otras
 * pruebas dejaron armado con el reloj de verdad no vence aqui.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CalendarioYTemporizadoresIntegracionTest {

    private static final String CLAVE = "un-faro-sobre-la-bahia-quieta";
    private static final String CALENDARIO = "/api/v1/organizacion/calendario";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Instant VIERNES = LocalDateTime.parse("2026-01-09T15:00:00").atZone(BOGOTA).toInstant();
    private static int tiendas;

    /** El reloj de pared de la aplicacion entera, en manos de la prueba. */
    static final class RelojMovil extends Clock {

        private volatile Instant ahora = VIERNES;

        void irA(Instant momento) {
            ahora = momento;
        }

        void avanzar(Duration tiempo) {
            ahora = ahora.plus(tiempo);
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }
    }

    @TestConfiguration
    static class RelojDePrueba {

        @Bean
        @Primary
        RelojMovil relojMovil() {
            return new RelojMovil();
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RelojMovil reloj;
    @Autowired
    private EmpresaService empresaService;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ProcesoService procesoService;
    @Autowired
    private VersionService versionService;
    @Autowired
    private RolProcesoService rolProcesoService;
    @Autowired
    private PoolService poolService;
    @Autowired
    private LaneService laneService;
    @Autowired
    private EventoService eventoService;
    @Autowired
    private ActividadService actividadService;
    @Autowired
    private ArcoService arcoService;
    @Autowired
    private CasoService casoService;
    @Autowired
    private TemporizadorService temporizadorService;

    private Long empresaId;
    private Long otraId;
    private ApiPrincipal ana;
    private ApiPrincipal beto;
    private ApiPrincipal carla;
    private ApiPrincipal ajeno;

    @BeforeEach
    void dosTiendas() {
        reloj.irA(VIERNES);
        int numero = ++tiendas;
        String correo = "ana" + numero + "@calendario.test";
        empresaId = empresaService.registrar("Calendario " + numero, "9681" + numero + "000-1", correo, "Ana", correo,
                CLAVE).id();
        ana = persona(correo, RolAcceso.ADMINISTRADOR);
        beto = persona(usuarioService.crearColaborador(empresaId, null, "Beto", "beto" + numero + "@calendario.test",
                CLAVE, RolAcceso.EDITOR).email(), RolAcceso.EDITOR);
        carla = persona(usuarioService.crearColaborador(empresaId, null, "Carla", "carla" + numero
                + "@calendario.test", CLAVE, RolAcceso.SOLO_LECTURA).email(), RolAcceso.SOLO_LECTURA);
        String otro = "otra" + numero + "@calendario.test";
        otraId = empresaService.registrar("Otra " + numero, "9682" + numero + "000-1", otro, "Otra", otro, CLAVE)
                .id();
        ajeno = persona(otro, RolAcceso.ADMINISTRADOR);
    }

    // ---------- El calendario ----------

    @Test
    @DisplayName("El calendario de fabrica es el del pais del idioma; lo lee cualquier rol y lo cambia el administrador")
    void calendario_lecturaYEdicion() throws Exception {
        mvc.perform(get(CALENDARIO).with(como(carla))).andExpect(status().isOk())
                .andExpect(jsonPath("$.pais").value("CO"))
                .andExpect(jsonPath("$.zonaHoraria").value("America/Bogota"))
                .andExpect(jsonPath("$.horaInicio").value(startsWith("08:00")))
                .andExpect(jsonPath("$.diasHabiles.length()").value(5))
                .andExpect(jsonPath("$.version").doesNotExist());
        String paris = """
                {"pais": "FR", "zonaHoraria": "Europe/Paris", "horaInicio": "09:00", "horaFin": "18:00",
                 "diasHabiles": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]}""";

        mvc.perform(put(CALENDARIO).with(como(beto)).contentType(MediaType.APPLICATION_JSON).content(paris))
                .andExpect(status().isForbidden());
        mvc.perform(put(CALENDARIO).with(como(carla)).contentType(MediaType.APPLICATION_JSON).content(paris))
                .andExpect(status().isForbidden());
        mvc.perform(put(CALENDARIO).with(como(ana)).contentType(MediaType.APPLICATION_JSON).content(paris))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pais").value("FR"))
                .andExpect(jsonPath("$.version").value(0));
        mvc.perform(put(CALENDARIO).with(como(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content(paris.replace("\"pais\": \"FR\"", "\"pais\": \"FR\", \"version\": 7")))
                .andExpect(status().isConflict());
        mvc.perform(get(CALENDARIO + "/festivos").param("anio", "2026").with(como(carla)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(11))
                .andExpect(jsonPath("$[0].nombre").value("Jour de l'an"));
        // La otra tienda sigue con el suyo
        mvc.perform(get(CALENDARIO).with(como(ajeno))).andExpect(status().isOk())
                .andExpect(jsonPath("$.pais").value("CO"));
        await().atMost(Duration.ofSeconds(10)).until(() -> enLaBitacora("CALENDARIO_EDITADO") == 1);
    }

    @Test
    @DisplayName("Lo que no es un calendario no se guarda: 400 con el campo")
    void calendario_validacion() throws Exception {
        mvc.perform(put(CALENDARIO).with(como(ana)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"pais": "CO", "zonaHoraria": "America/Atlantida", "horaInicio": "08:00", "horaFin": "17:00",
                         "diasHabiles": ["MONDAY"]}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(put(CALENDARIO).with(como(ana)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"zonaHoraria": "America/Bogota", "horaInicio": "08:00", "horaFin": "17:00",
                         "diasHabiles": []}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.pais").exists())
                .andExpect(jsonPath("$.errors.diasHabiles").exists());
        mvc.perform(get(CALENDARIO + "/festivos").param("anio", "1800").with(como(ana)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Los festivos se agregan, se quitan y se restauran; cada cambio queda en la bitacora")
    void festivos_edicion() throws Exception {
        mvc.perform(put(CALENDARIO + "/festivos/2026-09-04").with(como(beto))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": \"Aniversario\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(CALENDARIO + "/festivos/2026-09-04").with(como(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": \" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.nombre").exists());
        mvc.perform(put(CALENDARIO + "/festivos/2026-09-04").with(como(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": \"Aniversario\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.origen").value("EDITADO"));
        mvc.perform(delete(CALENDARIO + "/festivos/2026-08-17").with(como(beto)))
                .andExpect(status().isForbidden());
        mvc.perform(delete(CALENDARIO + "/festivos/2026-08-17").with(como(ana))).andExpect(status().isNoContent());
        mvc.perform(delete(CALENDARIO + "/festivos/2026-08-17").with(como(ana))).andExpect(status().isConflict());

        mvc.perform(get(CALENDARIO + "/festivos").param("anio", "2026").with(como(carla)))
                .andExpect(jsonPath("$[*].fecha").value(hasItem("2026-09-04")))
                .andExpect(jsonPath("$[*].fecha").value(not(hasItem("2026-08-17"))));
        mvc.perform(get(CALENDARIO + "/ediciones").with(como(carla))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fecha").value("2026-08-17"))
                .andExpect(jsonPath("$[0].festivo").value(false));
        // La otra tienda no ve lo que esta cambio, ni puede deshacerlo
        mvc.perform(get(CALENDARIO + "/ediciones").with(como(ajeno))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete(CALENDARIO + "/ediciones/2026-08-17").with(como(ajeno))).andExpect(status().isNotFound());

        mvc.perform(delete(CALENDARIO + "/ediciones/2026-08-17").with(como(ana))).andExpect(status().isNoContent());
        mvc.perform(delete(CALENDARIO + "/ediciones/2026-08-17").with(como(ana))).andExpect(status().isNotFound());
        mvc.perform(get(CALENDARIO + "/festivos").param("anio", "2026").with(como(carla)))
                .andExpect(jsonPath("$[*].fecha").value(hasItem("2026-08-17")));
        await().atMost(Duration.ofSeconds(10)).until(() -> enLaBitacora("FESTIVO_NOMBRADO") == 1
                && enLaBitacora("FESTIVO_QUITADO") == 1 && enLaBitacora("FESTIVO_RESTAURADO") == 1);
    }

    @Test
    @DisplayName("PostgreSQL esconde las filas de calendario, festivos y temporizadores de otra tienda")
    void seguridadPorFilas() throws Exception {
        String bogota = """
                {"pais": "CO", "zonaHoraria": "America/Bogota", "horaInicio": "07:00", "horaFin": "16:00",
                 "diasHabiles": ["MONDAY"]}""";
        for (ApiPrincipal administrador : List.of(ana, ajeno)) {
            mvc.perform(put(CALENDARIO).with(como(administrador)).contentType(MediaType.APPLICATION_JSON)
                    .content(bogota)).andExpect(status().isOk());
            mvc.perform(put(CALENDARIO + "/festivos/2026-09-04").with(como(administrador))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": \"Cierre\"}"))
                    .andExpect(status().isOk());
        }
        Long caso = casoDeProduccion(esperaDeDosDiasHabiles(empresaId, ana.usuarioId()));
        casoDeProduccion(esperaDeDosDiasHabiles(otraId, ajeno.usuarioId()));

        for (String tabla : List.of("calendarios", "festivos", "temporizadores")) {
            assertThat(ContextoDeTienda.deLaTienda(empresaId, () -> jdbc.queryForList(
                    "select distinct empresa_id from " + tabla, Long.class))).as(tabla).containsExactly(empresaId);
            assertThat(ContextoDeTienda.deLaTienda(empresaId, () -> jdbc.update(
                    "update " + tabla + " set empresa_id = empresa_id where empresa_id = ?", otraId))).isZero();
        }
        assertThat(ContextoDeTienda.deLaTienda(otraId, () -> jdbc.queryForList(
                "select id from temporizadores where caso_id = ?", Long.class, caso))).isEmpty();
    }

    // ---------- Los temporizadores ----------

    @Test
    @DisplayName("Un caso de produccion espera dos dias habiles y sigue cuando el reloj de pared llega")
    void produccion_elTemporizadorSaltaCuandoElRelojLlega() throws Exception {
        Long proceso = esperaDeDosDiasHabiles(empresaId, ana.usuarioId());

        JsonNode caso = cuerpo(mvc.perform(post("/api/v1/procesos/" + proceso + "/casos").with(como(beto))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"modo\": \"PRODUCCION\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.modo").value("PRODUCCION")).andReturn()
                .getResponse().getContentAsString());
        long casoId = caso.get("id").asLong();
        mvc.perform(get("/api/v1/casos/" + casoId + "/temporizadores").with(como(carla)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clase").value("INTERMEDIO"))
                .andExpect(jsonPath("$[0].estado").value("PROGRAMADO"))
                .andExpect(jsonPath("$[0].tipoTemporizador").value("DURACION_HABIL"))
                // Viernes a las tres + 18 horas habiles, sin el lunes 12 (Reyes Magos): miercoles 14 a las tres
                .andExpect(jsonPath("$[0].venceEn").value("2026-01-14T20:00:00"));

        reloj.irA(Instant.parse("2026-01-14T19:59:00Z"));
        temporizadorService.dispararVencidos();
        assertThat(estadoDelCaso(casoId)).isEqualTo("ABIERTO");

        reloj.irA(Instant.parse("2026-01-14T20:00:00Z"));
        temporizadorService.dispararVencidos();

        assertThat(estadoDelCaso(casoId)).isEqualTo("TERMINADO");
        mvc.perform(get("/api/v1/casos/" + casoId + "/temporizadores").with(como(carla)))
                .andExpect(jsonPath("$[0].estado").value("DISPARADO"))
                .andExpect(jsonPath("$[0].disparos").value(1));
        mvc.perform(get("/api/v1/casos/" + casoId + "/eventos").with(como(carla)))
                .andExpect(jsonPath("$[*].tipo").value(hasItem("TEMPORIZADOR_DISPARADO")));
    }

    @Test
    @DisplayName("Uno que vencio con la API caida salta una sola vez al volver")
    void apiCaida_saltaUnaVez() {
        Long caso = casoDeProduccion(esperaDeDosDiasHabiles(empresaId, ana.usuarioId()));

        // Una semana sin que nadie mirara
        reloj.avanzar(Duration.ofDays(7));
        temporizadorService.dispararVencidos();
        temporizadorService.dispararVencidos();

        assertThat(disparosEnLaBitacora(caso)).isOne();
        assertThat(estadoDelCaso(caso)).isEqualTo("TERMINADO");
    }

    @Test
    @DisplayName("Con dos instancias a la vez, el temporizador salta una sola vez")
    void dosInstancias_saltaUnaVez() throws Exception {
        Long caso = casoDeProduccion(esperaDeDosDiasHabiles(empresaId, ana.usuarioId()));
        reloj.avanzar(Duration.ofDays(7));
        CyclicBarrier juntas = new CyclicBarrier(2);
        Callable<Integer> instancia = () -> {
            juntas.await();
            return temporizadorService.dispararVencidos();
        };

        int saltos = 0;
        try (ExecutorService dos = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> pasadas = dos.invokeAll(List.of(instancia, instancia));
            for (Future<Integer> pasada : pasadas) {
                saltos += pasada.get();
            }
        }

        // La que llega segunda no lo encuentra armado: ni lo vuelve a disparar ni lo cancela
        assertThat(saltos).isOne();
        assertThat(disparosEnLaBitacora(caso)).isOne();
        assertThat(jdbc.queryForMap("select estado, disparos from temporizadores where caso_id = ?", caso))
                .containsEntry("estado", "DISPARADO").containsEntry("disparos", 1);
        assertThat(estadoDelCaso(caso)).isEqualTo("TERMINADO");
    }

    @Test
    @DisplayName("Cancelar el caso cancela sus temporizadores, y ya no saltan")
    void cancelarElCaso_cancelaSusTemporizadores() throws Exception {
        Long caso = casoDeProduccion(esperaDeDosDiasHabiles(empresaId, ana.usuarioId()));

        mvc.perform(post("/api/v1/casos/" + caso + "/cancelar").with(como(beto))).andExpect(status().isOk());
        reloj.avanzar(Duration.ofDays(7));
        temporizadorService.dispararVencidos();

        assertThat(jdbc.queryForObject("select estado from temporizadores where caso_id = ?", String.class, caso))
                .isEqualTo("CANCELADO");
        assertThat(disparosEnLaBitacora(caso)).isZero();
    }

    @Test
    @DisplayName("Un inicio temporizado abre casos de produccion solo; retirar su version lo cancela")
    void inicioTemporizado() throws Exception {
        Long proceso = conInicioTemporizado(empresaId, ana.usuarioId());
        mvc.perform(get("/api/v1/procesos/" + proceso + "/temporizadores").with(como(carla)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clase").value("INICIO"))
                .andExpect(jsonPath("$[0].venceEn").value("2026-01-09T21:00:00"));
        mvc.perform(post("/api/v1/procesos/" + proceso + "/casos").with(como(beto))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());

        // Cinco horas y media despues: vencieron cinco vueltas, pero abre un solo caso
        reloj.avanzar(Duration.ofMinutes(330));
        temporizadorService.dispararVencidos();

        assertThat(jdbc.queryForList("select modo from casos where proceso_id = ?", String.class, proceso))
                .containsExactly("PRODUCCION");
        mvc.perform(get("/api/v1/procesos/" + proceso + "/temporizadores").with(como(carla)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].disparos").value(1))
                .andExpect(jsonPath("$[0].venceEn").value("2026-01-10T02:00:00"));

        versionService.retirar(empresaId, proceso, 1, ana.usuarioId());

        assertThat(jdbc.queryForList("select estado from temporizadores where proceso_id = ?", String.class, proceso))
                .containsOnly("CANCELADO");
        mvc.perform(get("/api/v1/procesos/" + proceso + "/temporizadores").with(como(ajeno)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Probar el borrador abre un caso de prueba en una version que nadie ve; leerlo no lo permite")
    void probarElBorrador() throws Exception {
        Long proceso = esperaDeDosDiasHabiles(empresaId, ana.usuarioId());

        mvc.perform(post("/api/v1/procesos/" + proceso + "/pruebas").with(como(carla))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/procesos/" + proceso + "/pruebas").with(como(ajeno))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        JsonNode prueba = cuerpo(mvc.perform(post("/api/v1/procesos/" + proceso + "/pruebas").with(como(beto))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"referencia\": \"TEST-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.modo").value("PRUEBA"))
                .andExpect(jsonPath("$.versionNumero").value(0))
                .andReturn().getResponse().getContentAsString());

        mvc.perform(get("/api/v1/procesos/" + proceso + "/versiones").with(como(carla)))
                .andExpect(jsonPath("$[*].numero").value(not(hasItem(0))));
        mvc.perform(get("/api/v1/procesos/" + proceso + "/versiones/0").with(como(carla)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/casos/" + prueba.get("id").asLong() + "/temporizadores").with(como(carla)))
                .andExpect(jsonPath("$[0].modo").value("PRUEBA"))
                .andExpect(jsonPath("$[0].venceTick").value(18));
        mvc.perform(get("/api/v1/casos/" + prueba.get("id").asLong() + "/temporizadores").with(como(ajeno)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from versiones_proceso where proceso_id = ? and tipo = "
                + "'PRUEBA' and numero = 0 and estado = 'RETIRADA'", Integer.class, proceso)).isOne();
        assertThat(casoService.olvidarPruebasAntesDe(LocalDateTime.now().plusMinutes(1))).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from casos where proceso_id = ?", Integer.class, proceso))
                .isZero();
    }

    // ---------- Lo que se arma para cada prueba ----------

    /** Inicio, una espera de dos dias habiles y un fin, publicado. */
    private Long esperaDeDosDiasHabiles(Long tienda, Long autor) {
        Long proceso = procesoService.crear(tienda, autor, "Espera " + ++tiendas, "Lo que hace", "Ops").id();
        Long lane = lane(tienda, autor, proceso);
        Long inicio = eventoService.crear(tienda, autor, lane, "Order received", TipoEvento.INICIO, 0, 0).id();
        Long espera = eventoService.crear(tienda, autor, lane, "Wait two business days",
                TipoEvento.TEMPORIZADOR_INTERMEDIO, 120, 0,
                new DatosDeTemporizador(TipoTemporizador.DURACION_HABIL, "P2D", null, null)).id();
        Long fin = eventoService.crear(tienda, autor, lane, "Done", TipoEvento.FIN, 240, 0).id();
        arcoService.crear(tienda, autor, DatosDeArco.entre(inicio, espera));
        arcoService.crear(tienda, autor, DatosDeArco.entre(espera, fin));
        publicar(tienda, autor, proceso);
        return proceso;
    }

    /** Un inicio temporizado cada hora y una tarea, publicado. */
    private Long conInicioTemporizado(Long tienda, Long autor) {
        Long proceso = procesoService.crear(tienda, autor, "Cada hora " + ++tiendas, "Lo que hace", "Ops").id();
        Long lane = lane(tienda, autor, proceso);
        Long inicio = eventoService.crear(tienda, autor, lane, "Every hour", TipoEvento.TEMPORIZADOR_INICIO, 0, 0,
                new DatosDeTemporizador(TipoTemporizador.CICLO, "R/PT1H", null, null)).id();
        Long tarea = actividadService.crear(tienda, autor, lane, "Count stock", "Count it.", TipoActividad.USUARIO,
                120, 0).id();
        Long fin = eventoService.crear(tienda, autor, lane, "Counted", TipoEvento.FIN, 240, 0).id();
        arcoService.crear(tienda, autor, DatosDeArco.entre(inicio, tarea));
        arcoService.crear(tienda, autor, DatosDeArco.entre(tarea, fin));
        publicar(tienda, autor, proceso);
        return proceso;
    }

    private Long lane(Long tienda, Long autor, Long proceso) {
        Long pool = poolService.listarPorProceso(tienda, proceso).getFirst().id();
        return laneService.crear(tienda, autor, pool, "Sales",
                rolProcesoService.crear(tienda, autor, "Sales " + tiendas, null).id()).id();
    }

    private void publicar(Long tienda, Long autor, Long proceso) {
        procesoService.cambiarEstado(tienda, proceso, autor, EstadoProceso.PUBLICADO,
                procesoService.obtener(tienda, proceso, false).version());
    }

    private Long casoDeProduccion(Long proceso) {
        Long tienda = jdbc.queryForObject("select empresa_id from procesos where id = ?", Long.class, proceso);
        return casoService.abrir(tienda, null, proceso, null, Map.of(), ModoCaso.PRODUCCION).id();
    }

    private String estadoDelCaso(Long caso) {
        return jdbc.queryForObject("select estado from casos where id = ?", String.class, caso);
    }

    private int disparosEnLaBitacora(Long caso) {
        return jdbc.queryForObject("select count(*) from eventos_caso where caso_id = ? and tipo = "
                + "'TEMPORIZADOR_DISPARADO'", Integer.class, caso);
    }

    private int enLaBitacora(String accion) {
        return jdbc.queryForObject("select count(*) from bitacora where empresa_id = ? and accion = ?", Integer.class,
                empresaId, accion);
    }

    private JsonNode cuerpo(String texto) {
        return json.readTree(texto);
    }

    private ApiPrincipal persona(String correo, RolAcceso rol) {
        var usuario = usuarioRepository.findByEmail(correo).orElseThrow();
        return new ApiPrincipal(usuario.getId(), usuario.getEmpresa().getId(), rol, correo,
                ApiPrincipalRequestPostProcessor.SESION, false);
    }

    private static RequestPostProcessor como(ApiPrincipal quien) {
        return principal(quien);
    }
}
