package com.facimus.procesos.ejecucion;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.ejecucion.service.AsignacionDeTareaService;
import com.facimus.procesos.gestion.dto.response.ConfiguracionTiendaResponse;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.LimitesDeSesion;
import com.facimus.procesos.gestion.service.MembresiaRolService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.UsuarioService;
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
 * PR 70 de punta a punta, por HTTP y con PostgreSQL: un reembolso cuyas tareas llegan de cada manera (al rol, a la
 * persona de un campo, al iniciador y a una persona fija), lo que se hace con ellas mientras esperan y quien puede cada
 * cosa, dos personas que reclaman la misma a la vez, las bandejas y la carga, la politica de completar por otro, y que
 * cada tienda ve lo suyo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AsignacionIntegracionTest {

    private static final String CLAVE = "un-faro-sobre-la-bahia-quieta";
    private static final String TAREAS = "/api/v1/tareas/";
    private static int tiendas;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transacciones;
    @Autowired
    private EmpresaService empresaService;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private MembresiaRolService membresiaRolService;
    @Autowired
    private ConfiguracionTiendaService configuracionTiendaService;
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
    private AsignacionDeTareaService asignacionDeTareaService;

    private Long empresaId;
    private Long otraId;
    /** Administradora, de ningun rol. */
    private ApiPrincipal ana;
    /** Editor, de Support. */
    private ApiPrincipal beto;
    /** Editora, de Support. */
    private ApiPrincipal dora;
    /** Editora, de Billing. */
    private ApiPrincipal eva;
    /** Solo lee. */
    private ApiPrincipal carla;
    private ApiPrincipal ajeno;

    private Long proceso;
    private Long inicio;
    private Long revisar;
    private Long aprobar;
    private Long pagar;
    private Long avisar;
    private Long servicio;
    private Long rolSupport;

    @BeforeEach
    void dosTiendas() {
        int numero = ++tiendas;
        String correo = "ana" + numero + "@asignacion.test";
        empresaId = empresaService.registrar("Asignacion " + numero, "9781" + numero + "000-1", correo, "Ana", correo,
                CLAVE).id();
        ana = persona(correo, RolAcceso.ADMINISTRADOR);
        beto = colaborador("Beto", numero, RolAcceso.EDITOR);
        dora = colaborador("Dora", numero, RolAcceso.EDITOR);
        eva = colaborador("Eva", numero, RolAcceso.EDITOR);
        carla = colaborador("Carla", numero, RolAcceso.SOLO_LECTURA);
        String otro = "otra" + numero + "@asignacion.test";
        otraId = empresaService.registrar("Otra " + numero, "9782" + numero + "000-1", otro, "Otra", otro, CLAVE)
                .id();
        ajeno = persona(otro, RolAcceso.ADMINISTRADOR);
        reembolso(empresaId, ana.usuarioId());
        Long rolBilling = rolProcesoService.crear(empresaId, ana.usuarioId(), "Billing " + numero, null).id();
        membresiaRolService.reemplazar(empresaId, ana.usuarioId(), beto.usuarioId(), List.of(rolSupport));
        membresiaRolService.reemplazar(empresaId, ana.usuarioId(), dora.usuarioId(), List.of(rolSupport));
        membresiaRolService.reemplazar(empresaId, ana.usuarioId(), eva.usuarioId(), List.of(rolBilling));
    }

    // ---------- A quien le llega cada tarea ----------

    @Test
    @DisplayName("Cada tipo de asignacion da la tarea a quien dice al crearse, y la linea de tiempo lo cuenta")
    void cadaTipoDeAsignacion() throws Exception {
        asignarTodo();
        publicar();

        Long caso = abrir(beto, dora.usuarioId());
        Long deRevisar = tareaQueEspera(caso);
        assertThat(quienLaTiene(deRevisar)).isNull();
        completar(beto, deRevisar).andExpect(status().isOk()).andExpect(jsonPath("$.asignadoA").value(
                beto.usuarioId()));

        Long deAprobar = tareaQueEspera(caso);
        assertThat(quienLaTiene(deAprobar)).isEqualTo(dora.usuarioId());
        completar(beto, deAprobar).andExpect(status().isConflict());
        completar(dora, deAprobar).andExpect(status().isOk());

        Long dePagar = tareaQueEspera(caso);
        assertThat(quienLaTiene(dePagar)).isEqualTo(beto.usuarioId());
        completar(beto, dePagar).andExpect(status().isOk());

        Long deAvisar = tareaQueEspera(caso);
        assertThat(quienLaTiene(deAvisar)).isEqualTo(eva.usuarioId());
        completar(eva, deAvisar).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select estado from casos where id = ?", String.class, caso))
                .isEqualTo("TERMINADO");
        assertThat(linea(caso)).filteredOn(linea -> linea.startsWith("TAREA_ASIGNADA")).containsExactly(
                "TAREA_ASIGNADA \"Approve refund\" le llega al usuario " + dora.usuarioId()
                        + ", por la variable \"aprobador\".",
                "TAREA_ASIGNADA \"Pay refund\" le llega al usuario " + beto.usuarioId() + ", para quien abrio el caso.",
                "TAREA_ASIGNADA \"Notify customer\" le llega al usuario " + eva.usuarioId()
                        + ", asignada a una persona fija.");
    }

    @Test
    @DisplayName("La asignacion queda congelada en la version: cambiarla en el borrador no cambia los casos que corren")
    void congeladaEnLaVersion() throws Exception {
        asignar(avisar, "{\"tipo\": \"PERSONA\", \"usuarioId\": " + eva.usuarioId() + ", \"version\": %d}");
        publicar();
        asignar(avisar, "{\"tipo\": \"ROL\", \"version\": %d}");

        Long caso = abrir(beto, dora.usuarioId());
        for (ApiPrincipal quien : List.of(beto, beto, beto)) {
            completar(quien, tareaQueEspera(caso)).andExpect(status().isOk());
        }

        assertThat(quienLaTiene(tareaQueEspera(caso))).isEqualTo(eva.usuarioId());
    }

    @Test
    @DisplayName("Si la persona fija ya no puede tener tareas, la tarea espera en el rol y la linea de tiempo dice "
            + "por que")
    void personaQueYaNoPuede() throws Exception {
        asignar(revisar, "{\"tipo\": \"PERSONA\", \"usuarioId\": " + eva.usuarioId() + ", \"version\": %d}");
        publicar();
        usuarioService.desactivar(empresaId, ana.usuarioId(), eva.usuarioId());

        Long caso = abrir(beto, dora.usuarioId());

        assertThat(quienLaTiene(tareaQueEspera(caso))).isNull();
        assertThat(linea(caso)).contains("TAREA_CREADA \"Review refund\" queda en la bandeja a la espera de que "
                + "alguien la complete. Iba para el usuario " + eva.usuarioId() + ", que no puede tener tareas: esta "
                + "desactivado, solo lee o no es de la organizacion.");
    }

    @Test
    @DisplayName("Guardar la asignacion: quien modela si, quien solo lee no, y lo que no se puede cumplir no se guarda")
    void guardarLaAsignacion() throws Exception {
        String persona = "{\"tipo\": \"PERSONA\", \"usuarioId\": %d, \"version\": 0}";
        mvc.perform(put(asignacion(aprobar)).with(como(carla)).contentType(MediaType.APPLICATION_JSON)
                .content(persona.formatted(eva.usuarioId()))).andExpect(status().isForbidden());
        mvc.perform(put(asignacion(aprobar)).with(como(beto)).contentType(MediaType.APPLICATION_JSON)
                        .content(persona.formatted(carla.usuarioId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.usuarioId").value(containsString("solo lee")));
        mvc.perform(put(asignacion(aprobar)).with(como(beto)).contentType(MediaType.APPLICATION_JSON)
                        .content(persona.formatted(ajeno.usuarioId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.usuarioId").value("No es una persona de la organización."));
        mvc.perform(put(asignacion(aprobar)).with(como(beto)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\": \"CAMPO\", \"campo\": \"1aprobador\", \"version\": 0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.campo").exists());
        mvc.perform(put(asignacion(aprobar)).with(como(beto)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.tipo").exists());
        mvc.perform(put(asignacion(servicio)).with(como(beto)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\": \"INICIADOR\", \"version\": 0}")).andExpect(status().isConflict());

        mvc.perform(put(asignacion(aprobar)).with(como(beto)).contentType(MediaType.APPLICATION_JSON)
                        .content(persona.formatted(eva.usuarioId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asignacion.tipo").value("PERSONA"))
                .andExpect(jsonPath("$.asignacion.usuarioId").value(eva.usuarioId()))
                .andExpect(jsonPath("$.version").value(1));
        // Con la version vieja, alguien guardo antes; el diagrama la trae para cualquier rol
        mvc.perform(put(asignacion(aprobar)).with(como(beto)).contentType(MediaType.APPLICATION_JSON)
                .content(persona.formatted(dora.usuarioId()))).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/procesos/" + proceso + "/diagrama").with(como(carla))).andExpect(status().isOk())
                .andExpect(jsonPath("$.actividades[?(@.id == " + aprobar + ")].asignacion.tipo").value(
                        contains("PERSONA")));
    }

    @Test
    @DisplayName("La asignacion viaja en el BPMN del proceso y vuelve al importarlo")
    void enElBpmn() throws Exception {
        asignarTodo();
        String xml = mvc.perform(get("/api/v1/procesos/" + proceso + "/modelo").param("para", "dibujar")
                        .with(como(ana)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(xml).contains("<bpm:asignacion tipo=\"CAMPO\" campo=\"aprobador\"/>",
                "<bpm:asignacion tipo=\"INICIADOR\"/>",
                "<bpm:asignacion tipo=\"PERSONA\" usuario=\"" + eva.usuarioId() + "\"/>");
    }

    // ---------- Quien puede que ----------

    @Test
    @DisplayName("Reclamar: quien no es de su rol no, uno del rol si, y el segundo choca; solo quien la tiene la "
            + "completa")
    void reclamarYCompletar() throws Exception {
        publicar();
        Long tarea = tareaQueEspera(abrir(beto, dora.usuarioId()));

        accion(eva, tarea, "reclamar").andExpect(status().isForbidden());
        accion(carla, tarea, "reclamar").andExpect(status().isForbidden());
        accion(beto, tarea, "reclamar").andExpect(status().isOk())
                .andExpect(jsonPath("$.asignadoA").value(beto.usuarioId()));
        accion(dora, tarea, "reclamar").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La tarea ya la tiene alguien."));
        completar(dora, tarea).andExpect(status().isConflict());
        completar(ana, tarea).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("solo ella la completa")));

        completar(beto, tarea).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("COMPLETADA"));
        accion(beto, tarea, "soltar").andExpect(status().isConflict());
        await().atMost(Duration.ofSeconds(10)).until(() -> enLaBitacora("TAREA_RECLAMADA") == 1);
    }

    @Test
    @DisplayName("Un administrador toma cualquier tarea que no tenga nadie, aunque no sea de su rol")
    void administradorReclama() throws Exception {
        publicar();
        Long tarea = tareaQueEspera(abrir(beto, dora.usuarioId()));

        accion(ana, tarea, "reclamar").andExpect(status().isOk());
        completar(ana, tarea).andExpect(status().isOk());
    }

    @Test
    @DisplayName("La politica: apagada, un administrador no completa la tarea de otro; encendida, si, y queda en la "
            + "bitacora")
    void completarPorOtro() throws Exception {
        publicar();
        Long tarea = tareaQueEspera(abrir(beto, dora.usuarioId()));
        accion(beto, tarea, "reclamar").andExpect(status().isOk());
        completar(ana, tarea).andExpect(status().isConflict());

        ConfiguracionTiendaResponse antes = configuracionTiendaService.obtener(empresaId);
        configuracionTiendaService.editar(empresaId, ana.usuarioId(), antes.politicaEstructura(), null, null,
                LimitesDeSesion.SIN_CAMBIOS, null, null, true, antes.version());
        mvc.perform(get("/api/v1/empresas/actual/configuracion").with(como(ana))).andExpect(status().isOk())
                .andExpect(jsonPath("$.administradorCompletaPorOtro").value(true));
        completar(dora, tarea).andExpect(status().isConflict());

        completar(ana, tarea).andExpect(status().isOk()).andExpect(jsonPath("$.asignadoA").value(beto.usuarioId()));
        assertThat(linea(casoDe(tarea))).contains("TAREA_COMPLETADA \"Review refund\" se completo: la completo un "
                + "administrador por el usuario " + beto.usuarioId() + ", que la tenia.");
        await().atMost(Duration.ofSeconds(10)).until(() -> enLaBitacora("TAREA_COMPLETADA_POR_OTRO") == 1);
    }

    @Test
    @DisplayName("Soltar, delegar, devolver y reasignar: cada uno por quien puede, con su motivo en la linea de tiempo")
    void soltarDelegarYReasignar() throws Exception {
        publicar();
        Long tarea = tareaQueEspera(abrir(beto, dora.usuarioId()));
        accion(beto, tarea, "reclamar").andExpect(status().isOk());

        traspaso(dora, tarea, "delegar", eva.usuarioId(), "Por ella").andExpect(status().isForbidden());
        traspaso(beto, tarea, "delegar", carla.usuarioId(), "Lee").andExpect(status().isConflict());
        traspaso(beto, tarea, "delegar", ajeno.usuarioId(), "De otra tienda").andExpect(status().isNotFound());
        mvc.perform(post(TAREAS + tarea + "/delegar").with(como(beto)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuarioId\": " + eva.usuarioId() + "}")).andExpect(status().isBadRequest());
        traspaso(beto, tarea, "delegar", eva.usuarioId(), "Vacaciones").andExpect(status().isOk())
                .andExpect(jsonPath("$.asignadoA").value(eva.usuarioId()))
                .andExpect(jsonPath("$.delegadaPor").value(beto.usuarioId()));
        traspaso(eva, tarea, "delegar", dora.usuarioId(), "Otra vez").andExpect(status().isConflict());
        bandeja(eva, "MIAS").andExpect(jsonPath("$.content[*].id").value(contains(tarea.intValue())));

        accion(eva, tarea, "soltar").andExpect(status().isOk())
                .andExpect(jsonPath("$.asignadoA").value(beto.usuarioId()))
                .andExpect(jsonPath("$.delegadaPor").isEmpty());
        traspaso(beto, tarea, "delegar", dora.usuarioId(), "Turno").andExpect(status().isOk());
        traspaso(eva, tarea, "reasignar", eva.usuarioId(), "Me la quedo").andExpect(status().isForbidden());
        traspaso(ana, tarea, "reasignar", eva.usuarioId(), "Reparto").andExpect(status().isOk())
                .andExpect(jsonPath("$.asignadoA").value(eva.usuarioId()))
                .andExpect(jsonPath("$.delegadaPor").isEmpty());
        mvc.perform(post(TAREAS + tarea + "/soltar").with(como(eva)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"No me toca\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.asignadoA").isEmpty());

        assertThat(jdbc.queryForList("select estado from delegaciones where actividad_caso_id = ? order by id",
                String.class, tarea)).containsExactly("DEVUELTA", "ANULADA");
        assertThat(linea(casoDe(tarea))).filteredOn(linea -> linea.startsWith("TAREA_"))
                .extracting(linea -> linea.substring(0, linea.indexOf(' ')))
                .containsSubsequence("TAREA_RECLAMADA", "TAREA_DELEGADA", "TAREA_LIBERADA", "TAREA_DELEGADA",
                        "TAREA_REASIGNADA", "TAREA_LIBERADA");
        assertThat(linea(casoDe(tarea))).contains("TAREA_REASIGNADA \"Review refund\" pasa del usuario "
                + dora.usuarioId() + " al usuario " + eva.usuarioId() + ": Reparto",
                "TAREA_LIBERADA \"Review refund\" deja de tenerla el usuario " + eva.usuarioId()
                        + " y vuelve a la bandeja de su rol: No me toca");
        await().atMost(Duration.ofSeconds(10)).until(() -> enLaBitacora("TAREA_DELEGADA") == 2
                && enLaBitacora("TAREA_REASIGNADA") == 1 && enLaBitacora("TAREA_LIBERADA") == 2);
    }

    @Test
    @DisplayName("Las tareas de otra tienda no existen para esta (IDOR), para ninguna accion")
    void otraTienda() throws Exception {
        Long suya = tareaDeOtraTienda();

        for (String accion : List.of("reclamar", "soltar")) {
            accion(ana, suya, accion).andExpect(status().isNotFound());
        }
        for (String accion : List.of("reasignar", "delegar")) {
            traspaso(ana, suya, accion, beto.usuarioId(), "x").andExpect(status().isNotFound());
        }
        completar(ana, suya).andExpect(status().isNotFound());
        mvc.perform(get(TAREAS + suya).with(como(ana))).andExpect(status().isNotFound());
        mvc.perform(put(asignacion(jdbc.queryForObject("select nodo_id from actividades_caso where id = ?", Long.class,
                suya))).with(como(ana)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\": \"INICIADOR\", \"version\": 0}")).andExpect(status().isNotFound());
        // Y a la inversa: alguien de la otra tienda no le pasa la suya a una persona de esta
        accion(ajeno, suya, "reclamar").andExpect(status().isOk());
        traspaso(ajeno, suya, "reasignar", beto.usuarioId(), "x").andExpect(status().isNotFound());
    }

    // ---------- Dos a la vez ----------

    @Test
    @DisplayName("Carrera: la segunda reclamacion espera a que la primera termine y no toca ninguna fila")
    void carrera_laSegundaEspera() throws Exception {
        publicar();
        Long tarea = tareaQueEspera(abrir(beto, dora.usuarioId()));
        TransactionTemplate transaccion = new TransactionTemplate(transacciones);
        CountDownLatch tomada = new CountDownLatch(1);
        CountDownLatch terminar = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<?> primera = hilos.submit(() -> transaccion.executeWithoutResult(estado -> {
                asignacionDeTareaService.reclamar(empresaId, beto.usuarioId(), tarea);
                tomada.countDown();
                esperar(terminar);
            }));
            assertThat(tomada.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> segunda = hilos.submit(() -> asignacionDeTareaService.reclamar(empresaId, dora.usuarioId(),
                    tarea));
            assertThatThrownBy(() -> segunda.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

            terminar.countDown();
            primera.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> segunda.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(ReglaNegocioException.class)
                    .hasRootCauseMessage("La tarea ya la tiene alguien.");
        } finally {
            terminar.countDown();
            hilos.shutdownNow();
        }
        assertThat(quienLaTiene(tarea)).isEqualTo(beto.usuarioId());
    }

    @Test
    @DisplayName("Carrera: dos personas reclaman la misma tarea a la vez, cinco veces; cada vez gana exactamente una")
    void carrera_ganaUna() throws Exception {
        publicar();
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            for (int vuelta = 0; vuelta < 5; vuelta++) {
                Long tarea = tareaQueEspera(abrir(beto, dora.usuarioId()));
                CyclicBarrier salida = new CyclicBarrier(2);
                List<Future<Boolean>> intentos = new ArrayList<>();
                for (ApiPrincipal quien : List.of(beto, dora)) {
                    intentos.add(hilos.submit(() -> {
                        salida.await(10, TimeUnit.SECONDS);
                        try {
                            asignacionDeTareaService.reclamar(empresaId, quien.usuarioId(), tarea);
                            return true;
                        } catch (ReglaNegocioException perdio) {
                            assertThat(perdio).hasMessage("La tarea ya la tiene alguien.");
                            return false;
                        }
                    }));
                }
                List<Boolean> ganadores = new ArrayList<>();
                for (Future<Boolean> intento : intentos) {
                    ganadores.add(intento.get(20, TimeUnit.SECONDS));
                }

                assertThat(ganadores).containsExactlyInAnyOrder(true, false);
                assertThat(quienLaTiene(tarea)).isIn(beto.usuarioId(), dora.usuarioId());
                assertThat(jdbc.queryForObject("select count(*) from eventos_caso where caso_id = ? and tipo = "
                        + "'TAREA_RECLAMADA'", Integer.class, casoDe(tarea))).isOne();
            }
        } finally {
            hilos.shutdownNow();
        }
    }

    // ---------- Las bandejas ----------

    @Test
    @DisplayName("Las bandejas: mias, de mis roles, sin asignar y todas, de la mas vieja a la mas nueva; y la carga")
    void bandejasYCarga() throws Exception {
        publicar();
        Long primera = tareaQueEspera(abrir(beto, dora.usuarioId()));
        Long segunda = tareaQueEspera(abrir(beto, dora.usuarioId()));
        Long tercera = tareaQueEspera(abrir(beto, dora.usuarioId()));
        Long cuarta = tareaQueEspera(abrir(beto, dora.usuarioId()));
        accion(beto, primera, "reclamar").andExpect(status().isOk());
        accion(beto, tercera, "reclamar").andExpect(status().isOk());
        accion(dora, segunda, "reclamar").andExpect(status().isOk());

        bandeja(beto, "MIAS").andExpect(jsonPath("$.content[*].id").value(contains(primera.intValue(),
                tercera.intValue())));
        bandeja(dora, "MIAS").andExpect(jsonPath("$.content[*].id").value(contains(segunda.intValue())));
        bandeja(eva, "SIN_ASIGNAR").andExpect(jsonPath("$.content[*].id").value(contains(cuarta.intValue())));
        bandeja(beto, "DE_MIS_ROLES").andExpect(jsonPath("$.content[*].id").value(contains(primera.intValue(),
                segunda.intValue(), tercera.intValue(), cuarta.intValue())));
        bandeja(eva, "DE_MIS_ROLES").andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/tareas").param("mias", "true").with(como(beto)))
                .andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get("/api/v1/tareas").param("bandeja", "SIN_ASIGNAR").param("rolProcesoId",
                rolSupport.toString()).param("procesoId", proceso.toString()).with(como(carla)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id").value(contains(cuarta.intValue())));
        bandeja(carla, "TODAS").andExpect(jsonPath("$.content[*].id").value(contains(primera.intValue(),
                segunda.intValue(), tercera.intValue(), cuarta.intValue())));
        bandeja(ajeno, "TODAS").andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/v1/tareas/carga").with(como(carla))).andExpect(status().isOk())
                .andExpect(jsonPath("$.sinAsignar").value(1))
                .andExpect(jsonPath("$.personas[*].usuarioId").value(contains(beto.usuarioId().intValue(),
                        dora.usuarioId().intValue())))
                .andExpect(jsonPath("$.personas[0].nombre").value("Beto"))
                .andExpect(jsonPath("$.personas[0].tareas").value(2))
                .andExpect(jsonPath("$.personas[1].tareas").value(1));
        mvc.perform(get("/api/v1/tareas/carga").with(como(ajeno))).andExpect(jsonPath("$.sinAsignar").value(0))
                .andExpect(jsonPath("$.personas.length()").value(0));
    }

    // ---------- Cada tienda, lo suyo ----------

    @Test
    @DisplayName("PostgreSQL esconde las delegaciones de otra tienda")
    void seguridadPorFilas() throws Exception {
        publicar();
        Long tarea = tareaQueEspera(abrir(beto, dora.usuarioId()));
        accion(beto, tarea, "reclamar").andExpect(status().isOk());
        traspaso(beto, tarea, "delegar", dora.usuarioId(), "Turno").andExpect(status().isOk());
        Long suya = tareaDeOtraTienda();
        accion(ajeno, suya, "reclamar").andExpect(status().isOk());
        Long otroDeAlla = usuarioService.crearColaborador(otraId, null, "Zoe", "zoe" + tiendas + "@asignacion.test",
                CLAVE, RolAcceso.EDITOR).id();
        traspaso(ajeno, suya, "delegar", otroDeAlla, "Alla").andExpect(status().isOk());

        assertThat(ContextoDeTienda.deLaTienda(empresaId, () -> jdbc.queryForList(
                "select distinct empresa_id from delegaciones", Long.class))).containsExactly(empresaId);
        assertThat(ContextoDeTienda.deLaTienda(empresaId, () -> jdbc.update(
                "update delegaciones set motivo = 'x' where actividad_caso_id = ?", suya))).isZero();
        assertThat(ContextoDeTienda.deLaTienda(otraId, () -> jdbc.queryForList(
                "select actividad_caso_id from delegaciones", Long.class))).containsExactly(suya);
    }

    // ---------- Lo que se arma para cada prueba ----------

    /**
     * Un inicio con un campo PERSONA, revisar (en la bandeja del rol), aprobar, pagar, avisar y un fin, cada uno en la
     * lane de Support. Una actividad de servicio suelta, para lo que no se asigna.
     */
    private void reembolso(Long tienda, Long autor) {
        proceso = procesoService.crear(tienda, autor, "Refunds " + ++tiendas, "Lo que hace", "Ops").id();
        Long pool = poolService.listarPorProceso(tienda, proceso).getFirst().id();
        rolSupport = rolProcesoService.crear(tienda, autor, "Support " + tiendas, null).id();
        Long lane = laneService.crear(tienda, autor, pool, "Support", rolSupport).id();
        inicio = eventoService.crear(tienda, autor, lane, "Refund requested", TipoEvento.INICIO, 0, 0).id();
        revisar = actividadService.crear(tienda, autor, lane, "Review refund", null, TipoActividad.USUARIO, 100, 0)
                .id();
        aprobar = actividadService.crear(tienda, autor, lane, "Approve refund", null, TipoActividad.USUARIO, 200, 0)
                .id();
        pagar = actividadService.crear(tienda, autor, lane, "Pay refund", null, TipoActividad.USUARIO, 300, 0).id();
        avisar = actividadService.crear(tienda, autor, lane, "Notify customer", null, TipoActividad.USUARIO, 400, 0)
                .id();
        servicio = actividadService.crear(tienda, autor, lane, "Log refund", null, TipoActividad.SERVICIO, 400, 100)
                .id();
        Long fin = eventoService.crear(tienda, autor, lane, "Refund done", TipoEvento.FIN, 500, 0).id();
        arcoService.crear(tienda, autor, DatosDeArco.entre(inicio, revisar));
        arcoService.crear(tienda, autor, DatosDeArco.entre(revisar, aprobar));
        arcoService.crear(tienda, autor, DatosDeArco.entre(aprobar, pagar));
        arcoService.crear(tienda, autor, DatosDeArco.entre(pagar, avisar));
        arcoService.crear(tienda, autor, DatosDeArco.entre(avisar, servicio));
        arcoService.crear(tienda, autor, DatosDeArco.entre(servicio, fin));
    }

    /** El formulario del inicio con su campo PERSONA, y las tres asignaciones que no son la del rol. */
    private void asignarTodo() throws Exception {
        mvc.perform(put("/api/v1/eventos/" + inicio + "/formulario").with(como(ana))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"version": 0, "campos": [
                          {"nombre": "aprobador", "etiqueta": "Approver", "tipo": "PERSONA", "requerido": true}]}"""))
                .andExpect(status().isOk());
        asignar(aprobar, "{\"tipo\": \"CAMPO\", \"campo\": \"aprobador\", \"version\": %d}");
        asignar(pagar, "{\"tipo\": \"INICIADOR\", \"version\": %d}");
        asignar(avisar, "{\"tipo\": \"PERSONA\", \"usuarioId\": " + eva.usuarioId() + ", \"version\": %d}");
    }

    private void asignar(Long actividad, String cuerpo) throws Exception {
        Long version = jdbc.queryForObject("select version from nodos_flujo where id = ?", Long.class, actividad);
        mvc.perform(put(asignacion(actividad)).with(como(beto)).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo.formatted(version))).andExpect(status().isOk());
    }

    private static String asignacion(Long actividad) {
        return "/api/v1/actividades/" + actividad + "/asignacion";
    }

    private void publicar() {
        procesoService.cambiarEstado(empresaId, proceso, ana.usuarioId(), EstadoProceso.PUBLICADO,
                procesoService.obtener(empresaId, proceso, false).version());
    }

    /**
     * Un caso que abre una persona. Sin formulario de inicio va en PRUEBA, con datos libres; con el, en produccion y
     * con el aprobador que pide.
     */
    private Long abrir(ApiPrincipal quien, Long aprobador) throws Exception {
        boolean conFormulario = jdbc.queryForObject("select formulario is not null from nodos_flujo where id = ?",
                Boolean.class, inicio);
        String cuerpo = conFormulario
                ? "{\"referencia\": \"R-1\", \"modo\": \"PRODUCCION\", \"variables\": {\"aprobador\": " + aprobador
                        + "}}"
                : "{\"referencia\": \"R-1\", \"modo\": \"PRUEBA\", \"variables\": {}}";
        return id(mvc.perform(post("/api/v1/procesos/" + proceso + "/casos").with(como(quien))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isCreated()));
    }

    /** El mismo reembolso en la otra tienda, publicado y con un caso abierto; devuelve su primera tarea. */
    private Long tareaDeOtraTienda() throws Exception {
        Long[] mios = {proceso, inicio, revisar, aprobar, pagar, avisar, servicio, rolSupport};
        reembolso(otraId, ajeno.usuarioId());
        procesoService.cambiarEstado(otraId, proceso, ajeno.usuarioId(), EstadoProceso.PUBLICADO,
                procesoService.obtener(otraId, proceso, false).version());
        Long caso = id(mvc.perform(post("/api/v1/procesos/" + proceso + "/casos").with(como(ajeno))
                .contentType(MediaType.APPLICATION_JSON).content("{\"referencia\": \"R-9\", \"modo\": \"PRUEBA\"}"))
                .andExpect(status().isCreated()));
        Long suya = tareaQueEspera(caso);
        proceso = mios[0];
        inicio = mios[1];
        revisar = mios[2];
        aprobar = mios[3];
        pagar = mios[4];
        avisar = mios[5];
        servicio = mios[6];
        rolSupport = mios[7];
        return suya;
    }

    private ResultActions completar(ApiPrincipal quien, Long tarea) throws Exception {
        return mvc.perform(post(TAREAS + tarea + "/completar").with(como(quien))
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
    }

    private ResultActions accion(ApiPrincipal quien, Long tarea, String accion) throws Exception {
        return mvc.perform(post(TAREAS + tarea + "/" + accion).with(como(quien)));
    }

    private ResultActions traspaso(ApiPrincipal quien, Long tarea, String accion, Long aQuien, String motivo)
            throws Exception {
        return mvc.perform(post(TAREAS + tarea + "/" + accion).with(como(quien))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuarioId\": " + aQuien + ", \"motivo\": \"" + motivo + "\"}"));
    }

    private ResultActions bandeja(ApiPrincipal quien, String cual) throws Exception {
        return mvc.perform(get("/api/v1/tareas").param("bandeja", cual).with(como(quien)))
                .andExpect(status().isOk());
    }

    private Long tareaQueEspera(Long caso) {
        List<Long> tareas = jdbc.queryForList("select id from actividades_caso where caso_id = ? and estado = "
                + "'EN_ESPERA' and tipo_nodo = 'ACTIVIDAD' order by id", Long.class, caso);
        assertThat(tareas).hasSize(1);
        return tareas.getFirst();
    }

    private Long quienLaTiene(Long tarea) {
        return jdbc.queryForObject("select asignado_a from actividades_caso where id = ?", Long.class, tarea);
    }

    private Long casoDe(Long tarea) {
        return jdbc.queryForObject("select caso_id from actividades_caso where id = ?", Long.class, tarea);
    }

    /** La linea de tiempo del caso, una linea por evento: el tipo y su detalle. */
    private List<String> linea(Long caso) {
        return jdbc.queryForList("select tipo || ' ' || detalle from eventos_caso where caso_id = ? order by id",
                String.class, caso);
    }

    private int enLaBitacora(String accion) {
        return jdbc.queryForObject("select count(*) from bitacora where empresa_id = ? and accion = ?", Integer.class,
                empresaId, accion);
    }

    private ApiPrincipal colaborador(String nombre, int numero, RolAcceso rol) {
        return persona(usuarioService.crearColaborador(empresaId, null, nombre, nombre.toLowerCase() + numero
                + "@asignacion.test", CLAVE, rol).email(), rol);
    }

    private Long id(ResultActions respuesta) throws Exception {
        JsonNode cuerpo = json.readTree(respuesta.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        return cuerpo.get("id").asLong();
    }

    private ApiPrincipal persona(String correo, RolAcceso rol) {
        var usuario = usuarioRepository.findByEmail(correo).orElseThrow();
        return new ApiPrincipal(usuario.getId(), usuario.getEmpresa().getId(), rol, correo,
                ApiPrincipalRequestPostProcessor.SESION, false);
    }

    private static RequestPostProcessor como(ApiPrincipal quien) {
        return principal(quien);
    }

    private static void esperar(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("La otra transaccion no llego a tiempo.");
            }
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrumpido);
        }
    }
}
