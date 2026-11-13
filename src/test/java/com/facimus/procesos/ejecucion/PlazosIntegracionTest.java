package com.facimus.procesos.ejecucion;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.ejecucion.service.PlazoService;
import com.facimus.procesos.ejecucion.service.TemporizadorService;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.MembresiaRolService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.UsuarioService;
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

import tools.jackson.databind.json.JsonMapper;

/**
 * PR 71 de punta a punta, por HTTP y con PostgreSQL: el plazo de una tarea y del caso, el borde que escala, el repaso
 * que avisa una sola vez por el centro de notificaciones, la bandeja por vencimiento, el cumplimiento del tablero, y
 * quien puede, que ve cada tienda y que esconde la base. El reloj de pared lo mueve la prueba, y el repaso y los
 * temporizadores se llaman a mano, como lo haria su pasada.
 *
 * <p>El reloj empieza el viernes 10 de julio de 2026 a las tres de la tarde en Bogota, despues de lo que las pruebas
 * del motor miran y antes de hoy: lo que el reloj de verdad dejo armado no vence aqui. Lo que dejo otra prueba de esta
 * clase si puede moverse en una pasada, asi que nada cuenta las pasadas: cada prueba mira su tienda.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlazosIntegracionTest {

    private static final String CLAVE = "un-faro-sobre-la-bahia-quieta";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final Instant VIERNES = LocalDateTime.parse("2026-07-10T15:00:00").atZone(BOGOTA).toInstant();
    private static final Duration ESPERA = Duration.ofSeconds(10);
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

    /** Los nodos del proceso de reembolsos de una tienda. */
    private record Reembolso(Long proceso, Long rol, Long inicio, Long revisar, Long borde, Long servicio, Long fin) {
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
    private MembresiaRolService membresiaRolService;
    @Autowired
    private ProcesoService procesoService;
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
    private TemporizadorService temporizadorService;
    @Autowired
    private PlazoService plazoService;

    private Long empresaId;
    private Long otraId;
    /** Administradora y duena del proceso. */
    private ApiPrincipal ana;
    /** Editor, de Support. */
    private ApiPrincipal beto;
    /** Editora, de Support: la supervisora del borde. */
    private ApiPrincipal dora;
    /** Solo lee. */
    private ApiPrincipal carla;
    private ApiPrincipal ajeno;
    private Reembolso reembolso;

    @BeforeEach
    void dosTiendas() {
        reloj.irA(VIERNES);
        int numero = ++tiendas;
        String correo = "ana" + numero + "@plazos.test";
        empresaId = empresaService.registrar("Plazos " + numero, "9881" + numero + "000-1", correo, "Ana", correo,
                CLAVE).id();
        ana = persona(correo, RolAcceso.ADMINISTRADOR);
        beto = colaborador(empresaId, "Beto", numero, RolAcceso.EDITOR);
        dora = colaborador(empresaId, "Dora", numero, RolAcceso.EDITOR);
        carla = colaborador(empresaId, "Carla", numero, RolAcceso.SOLO_LECTURA);
        String otro = "otra" + numero + "@plazos.test";
        otraId = empresaService.registrar("Otra " + numero, "9882" + numero + "000-1", otro, "Otra", otro, CLAVE)
                .id();
        ajeno = persona(otro, RolAcceso.ADMINISTRADOR);
        reembolso = reembolso(empresaId, ana.usuarioId());
        membresiaRolService.reemplazar(empresaId, ana.usuarioId(), beto.usuarioId(), List.of(reembolso.rol()));
        membresiaRolService.reemplazar(empresaId, ana.usuarioId(), dora.usuarioId(), List.of(reembolso.rol()));
        jdbc.update("update procesos set dueno_id = ? where id = ?", ana.usuarioId(), reembolso.proceso());
    }

    // ---------- Guardarlos en el borrador ----------

    @Test
    @DisplayName("El plazo de una tarea lo guarda quien modela; quien solo lee no, otra tienda no lo encuentra, y lo "
            + "mal armado no se guarda")
    void plazoDeLaTarea_porHttp() throws Exception {
        Long revisar = reembolso.revisar();
        plazo("/actividades/", revisar, carla, "{\"cantidad\": 8, \"unidad\": \"HORAS\", \"version\": %d}")
                .andExpect(status().isForbidden());
        plazo("/actividades/", revisar, ajeno, "{\"cantidad\": 8, \"unidad\": \"HORAS\", \"version\": %d}")
                .andExpect(status().isNotFound());
        plazo("/actividades/", revisar, beto, "{\"cantidad\": 0, \"unidad\": \"HORAS\", \"version\": %d}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.cantidad").exists());
        plazo("/actividades/", revisar, beto, "{\"cantidad\": 367, \"unidad\": \"DIAS_HABILES\", \"version\": %d}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.cantidad").exists());
        plazo("/actividades/", revisar, beto, "{\"cantidad\": 8, \"version\": %d}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.unidad").exists());
        plazo("/actividades/", revisar, beto, "{\"cantidad\": 8, \"unidad\": \"SEMANAS\", \"version\": %d}")
                .andExpect(status().isBadRequest());
        plazo("/actividades/", revisar, beto, "{\"cantidad\": 8, \"unidad\": \"HORAS\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.version").exists());
        plazo("/actividades/", reembolso.servicio(), beto, "{\"cantidad\": 8, \"unidad\": \"HORAS\", \"version\": %d}")
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/actividades/" + revisar + "/plazo").with(como(beto))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\": 8, \"unidad\": \"HORAS\", \"version\": 99}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select plazo_unidad from nodos_flujo where id = ?", String.class, revisar))
                .isNull();

        plazo("/actividades/", revisar, beto, "{\"cantidad\": 8, \"unidad\": \"HORAS\", \"version\": %d}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.plazo.cantidad").value(8))
                .andExpect(jsonPath("$.plazo.unidad").value("HORAS"));
        mvc.perform(get("/api/v1/procesos/" + reembolso.proceso() + "/diagrama").with(como(carla)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actividades[?(@.id == " + revisar + ")].plazo.cantidad").value(8));
        plazo("/actividades/", revisar, ana, "{\"version\": %d}").andExpect(status().isOk())
                .andExpect(jsonPath("$.plazo").doesNotExist());
    }

    @Test
    @DisplayName("El plazo del caso va en un inicio, y lo que escala, en un borde que no interrumpe")
    void plazoDelCasoYEscalamiento_porHttp() throws Exception {
        plazo("/eventos/", reembolso.inicio(), ana, "{\"cantidad\": 2, \"unidad\": \"DIAS_HABILES\", \"version\": %d}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.plazo.unidad").value("DIAS_HABILES"));
        plazo("/eventos/", reembolso.fin(), ana, "{\"cantidad\": 2, \"unidad\": \"DIAS_HABILES\", \"version\": %d}")
                .andExpect(status().isConflict());
        plazo("/eventos/", reembolso.inicio(), ajeno, "{\"cantidad\": 2, \"unidad\": \"HORAS\", \"version\": %d}")
                .andExpect(status().isNotFound());

        Long borde = reembolso.borde();
        escalamiento(borde, carla, "{\"accion\": \"AVISAR\", \"version\": %d}").andExpect(status().isForbidden());
        escalamiento(borde, ajeno, "{\"accion\": \"AVISAR\", \"version\": %d}").andExpect(status().isNotFound());
        escalamiento(borde, beto, "{\"accion\": \"AVISAR\", \"usuarioId\": " + ajeno.usuarioId()
                + ", \"version\": %d}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.usuarioId").exists());
        escalamiento(borde, beto, "{\"accion\": \"AVISAR\", \"usuarioId\": " + carla.usuarioId()
                + ", \"version\": %d}").andExpect(status().isBadRequest());
        escalamiento(reembolso.inicio(), beto, "{\"accion\": \"AVISAR\", \"version\": %d}")
                .andExpect(status().isConflict());
        escalamiento(borde, beto, "{\"accion\": \"REASIGNAR\", \"usuarioId\": " + dora.usuarioId()
                + ", \"version\": %d}").andExpect(status().isOk())
                .andExpect(jsonPath("$.escalamiento.accion").value("REASIGNAR"))
                .andExpect(jsonPath("$.escalamiento.usuarioId").value(dora.usuarioId()));

        Long interrumpe = eventoService.crear(empresaId, ana.usuarioId(), lane(reembolso), "Too late",
                TipoEvento.TEMPORIZADOR_BORDE, 0, 0, new DatosDeTemporizador(TipoTemporizador.DURACION, "PT9H",
                        reembolso.revisar(), true)).id();
        escalamiento(interrumpe, beto, "{\"accion\": \"AVISAR\", \"version\": %d}").andExpect(status().isConflict());
    }

    // ---------- Un caso de produccion, de punta a punta ----------

    @Test
    @DisplayName("Por vencer a las seis horas, escalado a la supervisora, vencido a las ocho: cada aviso una vez, y el "
            + "tablero lo cuenta tarde")
    void deUnaPuntaALaOtra() throws Exception {
        conTodo();
        publicar();

        Long caso = abrir(beto);
        mvc.perform(get("/api/v1/casos/" + caso).with(como(carla))).andExpect(status().isOk())
                // Dos dias habiles desde el viernes a las tres: el martes 14 a las tres, sin contar el fin de semana
                .andExpect(jsonPath("$.caso.venceEn").value("2026-07-14T20:00:00"))
                .andExpect(jsonPath("$.caso.estadoPlazo").value("A_TIEMPO"));
        Long tarea = tareaQueEspera(caso);
        mvc.perform(get("/api/v1/tareas").with(como(carla))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(tarea))
                .andExpect(jsonPath("$.content[0].venceEn").value("2026-07-11T04:00:00"))
                .andExpect(jsonPath("$.content[0].estadoPlazo").value("A_TIEMPO"));
        mvc.perform(post("/api/v1/tareas/" + tarea + "/reclamar").with(como(beto))).andExpect(status().isOk());

        reloj.avanzar(Duration.ofHours(6));
        temporizadorService.dispararVencidos();
        plazoService.revisarVencidos();
        assertThat(jdbc.queryForObject("select asignado_a from actividades_caso where id = ?", Long.class, tarea))
                .isEqualTo(dora.usuarioId());
        assertThat(estadoDelPlazo(tarea)).isEqualTo("POR_VENCER");

        reloj.avanzar(Duration.ofHours(2));
        // Dos pasadas, como dos instancias: la segunda no la encuentra por mover, y no avisa otra vez
        plazoService.revisarVencidos();
        plazoService.revisarVencidos();
        assertThat(estadoDelPlazo(tarea)).isEqualTo("VENCIDA");

        await().atMost(ESPERA).untilAsserted(() -> assertThat(notificaciones()).containsOnly(
                Map.entry("TAREA_ESCALADA " + beto.usuarioId(), 1L),
                Map.entry("TAREA_ESCALADA " + dora.usuarioId(), 1L),
                Map.entry("PLAZO_POR_VENCER " + dora.usuarioId(), 1L),
                Map.entry("PLAZO_POR_VENCER " + ana.usuarioId(), 1L),
                Map.entry("PLAZO_VENCIDO " + dora.usuarioId(), 1L),
                Map.entry("PLAZO_VENCIDO " + ana.usuarioId(), 1L)));
        mvc.perform(get("/api/v1/notificaciones").with(como(dora))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tipo").value("PLAZO_VENCIDO"))
                .andExpect(jsonPath("$.content[0].referenciaId").value(caso))
                .andExpect(jsonPath("$.content[0].autor").doesNotExist());
        assertThat(linea(caso)).contains("TAREA_ESCALADA \"Six hours\" escala \"Review refund\": pasa del usuario "
                + beto.usuarioId() + " al usuario " + dora.usuarioId() + ", que la supervisa.",
                "PLAZO_POR_VENCER \"Review refund\" esta por vencer: entro en el ultimo cuarto de su plazo.",
                "PLAZO_VENCIDO \"Review refund\" paso su plazo.");
        await().atMost(ESPERA).until(() -> enLaBitacora("TAREA_ESCALADA") == 1);

        mvc.perform(post("/api/v1/tareas/" + tarea + "/completar").with(como(dora))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPlazo").value("VENCIDA"));
        assertThat(jdbc.queryForMap("select estado, estado_plazo from casos where id = ?", caso))
                .containsEntry("estado", "TERMINADO").containsEntry("estado_plazo", "A_TIEMPO");

        mvc.perform(get("/api/v1/procesos/" + reembolso.proceso() + "/tablero").with(como(carla)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cumplimiento.tareasATiempo").value(0))
                .andExpect(jsonPath("$.cumplimiento.tareasTarde").value(1))
                .andExpect(jsonPath("$.cumplimiento.casos.aTiempo").value(1))
                .andExpect(jsonPath("$.cumplimiento.casos.tarde").value(0))
                .andExpect(jsonPath("$.cumplimiento.porActividad[0].nodoNombre").value("Review refund"))
                .andExpect(jsonPath("$.cumplimiento.porActividad[0].tarde").value(1))
                .andExpect(jsonPath("$.cumplimiento.porProceso[0].procesoId").value(reembolso.proceso()))
                .andExpect(jsonPath("$.cumplimiento.porPersona[0].usuarioId").value(dora.usuarioId()))
                .andExpect(jsonPath("$.cumplimiento.porPersona[0].tarde").value(1));
        mvc.perform(get("/api/v1/empresas/actual/tablero").with(como(carla))).andExpect(status().isOk())
                .andExpect(jsonPath("$.cumplimiento.tareasTarde").value(1));
        mvc.perform(get("/api/v1/empresas/actual/tablero").with(como(ajeno))).andExpect(status().isOk())
                .andExpect(jsonPath("$.cumplimiento.tareasTarde").value(0))
                .andExpect(jsonPath("$.cumplimiento.porPersona.length()").value(0));
        // El tablero cuenta solo lo de la tienda de quien pregunta: el proceso de otra no le trae nada
        mvc.perform(get("/api/v1/procesos/" + reembolso.proceso() + "/tablero").with(como(ajeno)))
                .andExpect(jsonPath("$.cumplimiento.tareasTarde").value(0))
                .andExpect(jsonPath("$.cumplimiento.casos.aTiempo").value(0))
                .andExpect(jsonPath("$.cumplimiento.porActividad.length()").value(0));
    }

    @Test
    @DisplayName("Una tarea completada a tiempo cuenta a tiempo, y la bandeja va de la que vence antes a la que despues")
    void aTiempoYBandeja() throws Exception {
        conTodo();
        publicar();

        Long primero = abrir(beto);
        reloj.avanzar(Duration.ofHours(1));
        Long segundo = abrir(beto);
        Long dePrueba = id(mvc.perform(post("/api/v1/procesos/" + reembolso.proceso() + "/casos").with(como(beto))
                .contentType(MediaType.APPLICATION_JSON).content("{\"modo\": \"PRUEBA\"}"))
                .andExpect(status().isCreated()));

        mvc.perform(get("/api/v1/tareas").with(como(beto))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].casoId").value(primero))
                .andExpect(jsonPath("$.content[1].casoId").value(segundo))
                .andExpect(jsonPath("$.content[2].casoId").value(dePrueba))
                .andExpect(jsonPath("$.content[2].venceTick").isNumber())
                .andExpect(jsonPath("$.content[2].venceEn").doesNotExist());

        mvc.perform(post("/api/v1/tareas/" + tareaQueEspera(segundo) + "/completar").with(como(beto))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPlazo").value("A_TIEMPO"));
        mvc.perform(get("/api/v1/procesos/" + reembolso.proceso() + "/tablero").with(como(carla)))
                .andExpect(jsonPath("$.cumplimiento.tareasATiempo").value(1))
                .andExpect(jsonPath("$.cumplimiento.tareasTarde").value(0))
                .andExpect(jsonPath("$.cumplimiento.porPersona[0].usuarioId").value(beto.usuarioId()))
                .andExpect(jsonPath("$.cumplimiento.porPersona[0].aTiempo").value(1));
    }

    @Test
    @DisplayName("PostgreSQL esconde los plazos y los avisos de otra tienda")
    void seguridadPorFilas() throws Exception {
        conTodo();
        publicar();
        Reembolso suyo = reembolso(otraId, ajeno.usuarioId());
        mvc.perform(put("/api/v1/actividades/" + suyo.revisar() + "/plazo").with(como(ajeno))
                .contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\": 1, \"unidad\": \"HORAS\", "
                        + "\"version\": " + version(suyo.revisar()) + "}")).andExpect(status().isOk());
        jdbc.update("update procesos set dueno_id = ? where id = ?", ajeno.usuarioId(), suyo.proceso());
        procesoService.cambiarEstado(otraId, suyo.proceso(), ajeno.usuarioId(), EstadoProceso.PUBLICADO,
                procesoService.obtener(otraId, suyo.proceso(), false).version());
        Long caso = abrir(beto);
        Long suCaso = id(mvc.perform(post("/api/v1/procesos/" + suyo.proceso() + "/casos").with(como(ajeno))
                .contentType(MediaType.APPLICATION_JSON).content("{\"modo\": \"PRODUCCION\"}"))
                .andExpect(status().isCreated()));
        reloj.avanzar(Duration.ofHours(9));
        plazoService.revisarVencidos();
        await().atMost(ESPERA).until(() -> jdbc.queryForObject("select count(*) from notificaciones where tipo = "
                + "'PLAZO_VENCIDO' and empresa_id in (?, ?)", Integer.class, empresaId, otraId) >= 3);

        for (String consulta : List.of("select distinct empresa_id from actividades_caso where vence_en is not null",
                "select distinct empresa_id from casos where estado_plazo is not null",
                "select distinct empresa_id from notificaciones where tipo like 'PLAZO%'")) {
            assertThat(ContextoDeTienda.deLaTienda(empresaId, () -> jdbc.queryForList(consulta, Long.class)))
                    .as(consulta).containsExactly(empresaId);
        }
        assertThat(ContextoDeTienda.deLaTienda(empresaId, () -> jdbc.update(
                "update actividades_caso set estado_plazo = 'A_TIEMPO' where caso_id = ?", suCaso))).isZero();
        assertThat(ContextoDeTienda.deLaTienda(otraId, () -> jdbc.queryForList(
                "select estado_plazo from casos where id = ?", String.class, caso))).isEmpty();
        mvc.perform(get("/api/v1/casos/" + suCaso).with(como(beto))).andExpect(status().isNotFound());
    }

    // ---------- Lo que se arma para cada prueba ----------

    /** Inicio, una tarea con un borde de seis horas que no interrumpe, un servicio y dos fines. */
    private Reembolso reembolso(Long tienda, Long autor) {
        Long proceso = procesoService.crear(tienda, autor, "Refunds " + ++tiendas, "Lo que hace", "Ops").id();
        Long pool = poolService.listarPorProceso(tienda, proceso).getFirst().id();
        Long rol = rolProcesoService.crear(tienda, autor, "Support " + tiendas, null).id();
        Long lane = laneService.crear(tienda, autor, pool, "Support", rol).id();
        Long inicio = eventoService.crear(tienda, autor, lane, "Refund requested", TipoEvento.INICIO, 0, 0).id();
        Long revisar = actividadService.crear(tienda, autor, lane, "Review refund", null, TipoActividad.USUARIO, 100,
                0).id();
        Long borde = eventoService.crear(tienda, autor, lane, "Six hours", TipoEvento.TEMPORIZADOR_BORDE, 0, 0,
                new DatosDeTemporizador(TipoTemporizador.DURACION, "PT6H", revisar, false)).id();
        Long servicio = actividadService.crear(tienda, autor, lane, "Log refund", null, TipoActividad.SERVICIO, 200, 0)
                .id();
        Long fin = eventoService.crear(tienda, autor, lane, "Refund done", TipoEvento.FIN, 300, 0).id();
        Long escalado = eventoService.crear(tienda, autor, lane, "Escalated", TipoEvento.FIN, 300, 100).id();
        arcoService.crear(tienda, autor, DatosDeArco.entre(inicio, revisar));
        arcoService.crear(tienda, autor, DatosDeArco.entre(revisar, servicio));
        arcoService.crear(tienda, autor, DatosDeArco.entre(servicio, fin));
        arcoService.crear(tienda, autor, DatosDeArco.entre(borde, escalado));
        return new Reembolso(proceso, rol, inicio, revisar, borde, servicio, fin);
    }

    private Long lane(Reembolso de) {
        return jdbc.queryForObject("select lane_id from nodos_flujo where id = ?", Long.class, de.revisar());
    }

    /** Ocho horas para la tarea, dos dias habiles para el caso, y el borde que se la pasa a Dora. */
    private void conTodo() throws Exception {
        plazo("/actividades/", reembolso.revisar(), beto, "{\"cantidad\": 8, \"unidad\": \"HORAS\", \"version\": %d}")
                .andExpect(status().isOk());
        plazo("/eventos/", reembolso.inicio(), beto,
                "{\"cantidad\": 2, \"unidad\": \"DIAS_HABILES\", \"version\": %d}").andExpect(status().isOk());
        escalamiento(reembolso.borde(), beto, "{\"accion\": \"REASIGNAR\", \"usuarioId\": " + dora.usuarioId()
                + ", \"version\": %d}").andExpect(status().isOk());
    }

    private ResultActions plazo(String ruta, Long nodo, ApiPrincipal quien, String cuerpo) throws Exception {
        return mvc.perform(put("/api/v1" + ruta + nodo + "/plazo").with(como(quien))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo.formatted(version(nodo))));
    }

    private ResultActions escalamiento(Long evento, ApiPrincipal quien, String cuerpo) throws Exception {
        return mvc.perform(put("/api/v1/eventos/" + evento + "/escalamiento").with(como(quien))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo.formatted(version(evento))));
    }

    private Long version(Long nodo) {
        return jdbc.queryForObject("select version from nodos_flujo where id = ?", Long.class, nodo);
    }

    private void publicar() {
        procesoService.cambiarEstado(empresaId, reembolso.proceso(), ana.usuarioId(), EstadoProceso.PUBLICADO,
                procesoService.obtener(empresaId, reembolso.proceso(), false).version());
    }

    private Long abrir(ApiPrincipal quien) throws Exception {
        return id(mvc.perform(post("/api/v1/procesos/" + reembolso.proceso() + "/casos").with(como(quien))
                .contentType(MediaType.APPLICATION_JSON).content("{\"modo\": \"PRODUCCION\"}"))
                .andExpect(status().isCreated()));
    }

    private Long tareaQueEspera(Long caso) {
        List<Long> tareas = jdbc.queryForList("select id from actividades_caso where caso_id = ? and estado = "
                + "'EN_ESPERA' and tipo_nodo = 'ACTIVIDAD' order by id", Long.class, caso);
        assertThat(tareas).hasSize(1);
        return tareas.getFirst();
    }

    private String estadoDelPlazo(Long tarea) {
        return jdbc.queryForObject("select estado_plazo from actividades_caso where id = ?", String.class, tarea);
    }

    /** Cuantas notificaciones de cada tipo tiene cada persona de esta tienda, como "TIPO usuario". */
    private Map<String, Long> notificaciones() {
        return jdbc.query("select tipo, usuario_id, count(*) as cuantas from notificaciones where empresa_id = ? "
                + "group by tipo, usuario_id", resultado -> {
                    Map<String, Long> cuentas = new HashMap<>();
                    while (resultado.next()) {
                        cuentas.put(resultado.getString("tipo") + " " + resultado.getLong("usuario_id"),
                                resultado.getLong("cuantas"));
                    }
                    return cuentas;
                }, empresaId);
    }

    private List<String> linea(Long caso) {
        return jdbc.queryForList("select tipo || ' ' || detalle from eventos_caso where caso_id = ? order by id",
                String.class, caso);
    }

    private int enLaBitacora(String accion) {
        return jdbc.queryForObject("select count(*) from bitacora where empresa_id = ? and accion = ?", Integer.class,
                empresaId, accion);
    }

    private ApiPrincipal colaborador(Long tienda, String nombre, int numero, RolAcceso rol) {
        return persona(usuarioService.crearColaborador(tienda, null, nombre, nombre.toLowerCase() + numero
                + "@plazos.test", CLAVE, rol).email(), rol);
    }

    private Long id(ResultActions respuesta) throws Exception {
        return json.readTree(respuesta.andReturn().getResponse().getContentAsString()).get("id").asLong();
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
