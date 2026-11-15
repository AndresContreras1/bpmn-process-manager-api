package com.facimus.procesos.ejecucion;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

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

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.DatosDeArco;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.modelado.service.GatewayService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.PoolService;
import com.facimus.procesos.security.ApiPrincipalRequestPostProcessor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 69 de punta a punta, por HTTP y con PostgreSQL: un reembolso que se abre con su formulario de inicio y se aprueba
 * con el de su tarea. Quien guarda los formularios y quien los lee, lo que responde un formulario mal armado o mal
 * contestado, que el formulario queda congelado en la version, que viaja en el BPMN y vuelve, y que cada tienda ve los
 * suyos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FormulariosIntegracionTest {

    private static final String CLAVE = "un-faro-sobre-la-bahia-quieta";
    private static int tiendas;

    /** El formulario con que se pide un reembolso: el pedido, con su forma, y el monto en pesos. */
    private static final String DE_INICIO = """
            {"version": %d, "campos": [
              {"nombre": "pedido", "etiqueta": "Order", "tipo": "TEXTO", "requerido": true, "patron": "ORD-\\\\d+"},
              {"nombre": "monto", "etiqueta": "Amount", "tipo": "MONEDA", "moneda": "COP", "requerido": true,
               "minimo": 1}]}""";

    /** El de la aprobacion: si se aprueba, cuanto (propone el monto pedido) y, si no, por que. */
    private static final String DE_LA_TAREA = """
            {"version": %d, "campos": [
              {"nombre": "aprobado", "etiqueta": "Approved?", "tipo": "SI_NO", "requerido": true},
              {"nombre": "montoAprobado", "etiqueta": "Approved amount", "tipo": "MONEDA", "moneda": "COP",
               "porDefecto": "monto", "requeridoSi": "aprobado == true", "maximo": 5000000},
              {"nombre": "motivo", "etiqueta": "Why not", "tipo": "TEXTO_LARGO", "visibleSi": "aprobado == false",
               "requerido": true}]}""";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private JdbcTemplate jdbc;
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
    private EventoService eventoService;
    @Autowired
    private ActividadService actividadService;
    @Autowired
    private GatewayService gatewayService;
    @Autowired
    private ArcoService arcoService;

    private Long empresaId;
    private Long otraId;
    private ApiPrincipal ana;
    private ApiPrincipal beto;
    private ApiPrincipal carla;
    private ApiPrincipal ajeno;

    /** Los nodos del reembolso armado en cada prueba. */
    private Long proceso;
    private Long inicio;
    private Long aprobar;
    private Long pagar;

    @BeforeEach
    void dosTiendas() {
        int numero = ++tiendas;
        String correo = "ana" + numero + "@formularios.test";
        empresaId = empresaService.registrar("Formularios " + numero, "9691" + numero + "000-1", correo, "Ana", correo,
                CLAVE).id();
        ana = persona(correo, RolAcceso.ADMINISTRADOR);
        beto = persona(usuarioService.crearColaborador(empresaId, null, "Beto", "beto" + numero
                + "@formularios.test", CLAVE, RolAcceso.EDITOR).email(), RolAcceso.EDITOR);
        carla = persona(usuarioService.crearColaborador(empresaId, null, "Carla", "carla" + numero
                + "@formularios.test", CLAVE, RolAcceso.SOLO_LECTURA).email(), RolAcceso.SOLO_LECTURA);
        String otro = "otra" + numero + "@formularios.test";
        otraId = empresaService.registrar("Otra " + numero, "9692" + numero + "000-1", otro, "Otra", otro, CLAVE)
                .id();
        ajeno = persona(otro, RolAcceso.ADMINISTRADOR);
        reembolso(empresaId, ana.usuarioId());
    }

    // ---------- Guardar los formularios ----------

    @Test
    @DisplayName("Quien modela guarda los formularios y vuelven con sus condiciones compiladas; quien solo lee, no")
    void guardar() throws Exception {
        mvc.perform(put("/api/v1/actividades/" + aprobar + "/formulario").with(como(carla))
                        .contentType(MediaType.APPLICATION_JSON).content(DE_LA_TAREA.formatted(0)))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/v1/actividades/" + aprobar + "/formulario").with(como(beto))
                        .contentType(MediaType.APPLICATION_JSON).content(DE_LA_TAREA.formatted(0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formulario.campos.length()").value(3))
                .andExpect(jsonPath("$.formulario.campos[1].requeridoSiArbol.tipo").value("COMPARACION"))
                .andExpect(jsonPath("$.formulario.campos[1].requeridoSiArbol.ruta").value("aprobado"))
                .andExpect(jsonPath("$.formulario.campos[1].requeridoSiArbol.valor").value(true))
                .andExpect(jsonPath("$.formulario.campos[2].visibleSiArbol.operador").value("=="))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(put("/api/v1/eventos/" + inicio + "/formulario").with(como(ana))
                        .contentType(MediaType.APPLICATION_JSON).content(DE_INICIO.formatted(0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.formulario.campos[0].patron").value("ORD-\\d+"));

        // Con la version vieja, alguien guardo antes
        mvc.perform(put("/api/v1/actividades/" + aprobar + "/formulario").with(como(beto))
                        .contentType(MediaType.APPLICATION_JSON).content(DE_LA_TAREA.formatted(0)))
                .andExpect(status().isConflict());
        // Lo lee cualquier rol, en el diagrama del borrador
        mvc.perform(get("/api/v1/procesos/" + proceso + "/diagrama").with(como(carla))).andExpect(status().isOk())
                .andExpect(jsonPath("$.actividades[?(@.id == " + aprobar + ")].formulario.campos.length()")
                        .value(3));
    }

    @Test
    @DisplayName("Un formulario mal armado es un 400 con cada propiedad; en una tarea que no es de usuario, un 409")
    void malArmado() throws Exception {
        mvc.perform(put("/api/v1/actividades/" + aprobar + "/formulario").with(como(beto))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"version": 0, "campos": [
                                  {"nombre": "a", "etiqueta": "A", "tipo": "TEXTO", "patron": "(a)\\\\1"},
                                  {"nombre": "b", "etiqueta": "B", "tipo": "NUMERO", "minimo": 9, "maximo": 1,
                                   "visibleSi": "c == 1"},
                                  {"nombre": "c", "etiqueta": "C", "tipo": "LISTA"},
                                  {"nombre": "d", "etiqueta": "", "tipo": "COLOR"}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['campos.tipo']").exists());
        mvc.perform(put("/api/v1/actividades/" + aprobar + "/formulario").with(como(beto))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"version": 0, "campos": [
                                  {"nombre": "a", "etiqueta": "A", "tipo": "TEXTO", "patron": "(a)\\\\1"},
                                  {"nombre": "b", "etiqueta": "B", "tipo": "NUMERO", "minimo": 9, "maximo": 1,
                                   "visibleSi": "c == 1"},
                                  {"nombre": "c", "etiqueta": "C", "tipo": "LISTA"},
                                  {"nombre": "a", "etiqueta": "", "tipo": "TEXTO"}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validación fallida"))
                .andExpect(jsonPath("$.errors['campos[3].etiqueta']").exists())
                .andExpect(jsonPath("$.errors.length()").value(1));
        mvc.perform(put("/api/v1/actividades/" + aprobar + "/formulario").with(como(beto))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"version": 0, "campos": [
                                  {"nombre": "a", "etiqueta": "A", "tipo": "TEXTO", "patron": "(a)\\\\1"},
                                  {"nombre": "b", "etiqueta": "B", "tipo": "NUMERO", "minimo": 9, "maximo": 1,
                                   "visibleSi": "c == 1"},
                                  {"nombre": "c", "etiqueta": "C", "tipo": "LISTA"},
                                  {"nombre": "a", "etiqueta": "Again", "tipo": "TEXTO"}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['campos[0].patron']").value(containsString("RE2")))
                .andExpect(jsonPath("$.errors['campos[1].minimo']").exists())
                .andExpect(jsonPath("$.errors['campos[1].visibleSi']").exists())
                .andExpect(jsonPath("$.errors['campos[2].opciones']").exists())
                .andExpect(jsonPath("$.errors['campos[3].nombre']").exists());
        mvc.perform(put("/api/v1/actividades/" + pagar + "/formulario").with(como(beto))
                        .contentType(MediaType.APPLICATION_JSON).content(DE_LA_TAREA.formatted(0)))
                .andExpect(status().isOk());

        Long servicio = actividadService.crear(empresaId, ana.usuarioId(), lane(proceso), "Notify the bank", null,
                TipoActividad.SERVICIO, 300, 0).id();
        mvc.perform(put("/api/v1/actividades/" + servicio + "/formulario").with(como(beto))
                        .contentType(MediaType.APPLICATION_JSON).content(DE_LA_TAREA.formatted(0)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("El formulario de otra tienda no existe: ni se guarda, ni se lee su inicio")
    void idor() throws Exception {
        conFormularios();
        publicar(empresaId, ana.usuarioId(), proceso);

        mvc.perform(put("/api/v1/actividades/" + aprobar + "/formulario").with(como(ajeno))
                        .contentType(MediaType.APPLICATION_JSON).content(DE_LA_TAREA.formatted(1)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/eventos/" + inicio + "/formulario").with(como(ajeno))
                        .contentType(MediaType.APPLICATION_JSON).content(DE_INICIO.formatted(1)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/procesos/" + proceso + "/formulario-de-inicio").with(como(ajeno)))
                .andExpect(status().isNotFound());
        Long tarea = tareaQueEspera(abrir());
        mvc.perform(get("/api/v1/tareas/" + tarea + "/formulario").with(como(ajeno)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/tareas/" + tarea + "/completar").with(como(ajeno))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"datos\": {\"aprobado\": true}}"))
                .andExpect(status().isNotFound());
    }

    // ---------- Abrir y completar con formulario ----------

    @Test
    @DisplayName("Un caso se abre con el formulario de inicio: 400 por campo si no lo cumple, con tipo si lo cumple")
    void abrirConElFormularioDeInicio() throws Exception {
        conFormularios();
        publicar(empresaId, ana.usuarioId(), proceso);

        mvc.perform(get("/api/v1/procesos/" + proceso + "/formulario-de-inicio").with(como(carla)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.evento").value("Refund requested"))
                .andExpect(jsonPath("$.formulario.campos[*].nombre").value(contains("pedido",
                        "monto")));
        mvc.perform(post("/api/v1/procesos/" + proceso + "/casos").with(como(beto))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modo\": \"PRODUCCION\", \"variables\": {\"pedido\": \"X-1\", \"monto\": 10.555, "
                                + "\"cupon\": \"GRATIS\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Las respuestas no cumplen el formulario."))
                .andExpect(jsonPath("$.errors['variables.pedido']").value("No tiene la forma que se pide."))
                .andExpect(jsonPath("$.errors['variables.monto']").value("Admite hasta 2 decimales."))
                .andExpect(jsonPath("$.errors['variables.cupon']").value("No es un campo de este formulario."));
        assertThat(jdbc.queryForObject("select count(*) from casos where proceso_id = ?", Integer.class, proceso))
                .isZero();

        Long caso = abrir();
        mvc.perform(get("/api/v1/casos/" + caso).with(como(carla))).andExpect(status().isOk())
                .andExpect(jsonPath("$.variables.pedido").value("ORD-1001"))
                .andExpect(jsonPath("$.variables.monto").value(150000.5));
    }

    @Test
    @DisplayName("La tarea propone lo del caso, rechaza lo que no cumple, y guarda con tipo lo que un gateway lee")
    void completarConElFormularioDeLaTarea() throws Exception {
        conFormularios();
        publicar(empresaId, ana.usuarioId(), proceso);
        Long caso = abrir();
        Long tarea = tareaQueEspera(caso);

        mvc.perform(get("/api/v1/tareas/" + tarea + "/formulario").with(como(carla))).andExpect(status().isOk())
                .andExpect(jsonPath("$.nodo").value("Approve refund"))
                .andExpect(jsonPath("$.casoId").value(caso))
                .andExpect(jsonPath("$.valores.montoAprobado").value(150000.5))
                .andExpect(jsonPath("$.variables.pedido").value("ORD-1001"))
                .andExpect(jsonPath("$.variables.caso.referencia").value("ORD-1001"))
                .andExpect(jsonPath("$.formulario.campos[2].visibleSiArbol.valor").value(false));
        mvc.perform(post("/api/v1/tareas/" + tarea + "/completar").with(como(carla))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"datos\": {\"aprobado\": true}}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/tareas/" + tarea + "/completar").with(como(beto))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datos\": {\"aprobado\": \"si\", \"montoAprobado\": 6000000}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['datos.aprobado']").value("Es true o false."))
                .andExpect(jsonPath("$.errors['datos.montoAprobado']").value("Debe ser como mucho 5000000."));
        mvc.perform(post("/api/v1/tareas/" + tarea + "/completar").with(como(beto))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"datos\": {\"aprobado\": true}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['datos.montoAprobado']").value("Es obligatorio."));
        mvc.perform(get("/api/v1/tareas/" + tarea).with(como(carla)))
                .andExpect(jsonPath("$.estado").value("EN_ESPERA"));

        mvc.perform(post("/api/v1/tareas/" + tarea + "/completar").with(como(beto))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datos\": {\"aprobado\": true, \"montoAprobado\": 150000, \"motivo\": \"x\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("COMPLETADA"));

        mvc.perform(get("/api/v1/casos/" + caso).with(como(carla)))
                .andExpect(jsonPath("$.variables.tarea.approveRefund.aprobado").value(true))
                .andExpect(jsonPath("$.variables.tarea.approveRefund.montoAprobado").value(150000))
                .andExpect(jsonPath("$.variables.tarea.approveRefund.motivo").doesNotExist());
        // El gateway leyo el si o no con su tipo: el caso fue a pagar
        Long pago = tareaQueEspera(caso);
        assertThat(jdbc.queryForObject("select nodo_nombre from actividades_caso where id = ?", String.class, pago))
                .isEqualTo("Pay refund");
    }

    @Test
    @DisplayName("El formulario queda congelado en la version: cambiar el borrador no cambia el del caso abierto")
    void congeladoEnLaVersion() throws Exception {
        conFormularios();
        publicar(empresaId, ana.usuarioId(), proceso);
        Long tarea = tareaQueEspera(abrir());

        mvc.perform(put("/api/v1/actividades/" + aprobar + "/formulario").with(como(beto))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\": 1, \"campos\": []}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.formulario").doesNotExist());

        mvc.perform(get("/api/v1/tareas/" + tarea + "/formulario").with(como(carla)))
                .andExpect(jsonPath("$.formulario.campos.length()").value(3));
        mvc.perform(post("/api/v1/tareas/" + tarea + "/completar").with(como(beto))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"datos\": {\"libre\": 1}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['datos.libre']").value("No es un campo de este formulario."));
    }

    @Test
    @DisplayName("Sin formulario, los datos libres son del modo PRUEBA; en PRODUCCION, un 400 por clave")
    void sinFormulario() throws Exception {
        publicar(empresaId, ana.usuarioId(), proceso);

        mvc.perform(get("/api/v1/procesos/" + proceso + "/formulario-de-inicio").with(como(carla)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.formulario").doesNotExist());
        mvc.perform(post("/api/v1/procesos/" + proceso + "/casos").with(como(beto))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modo\": \"PRODUCCION\", \"variables\": {\"monto\": 5}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['variables.monto']").exists());
        Long produccion = id(mvc.perform(post("/api/v1/procesos/" + proceso + "/casos").with(como(beto))
                .contentType(MediaType.APPLICATION_JSON).content("{\"modo\": \"PRODUCCION\"}")));
        Long enProduccion = tareaQueEspera(produccion);
        mvc.perform(post("/api/v1/tareas/" + enProduccion + "/completar").with(como(beto))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"datos\": {\"aprobado\": true}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['datos.aprobado']").exists());

        Long prueba = id(mvc.perform(post("/api/v1/procesos/" + proceso + "/casos").with(como(beto))
                .contentType(MediaType.APPLICATION_JSON).content("{\"variables\": {\"monto\": 5}}")));
        mvc.perform(post("/api/v1/tareas/" + tareaQueEspera(prueba) + "/completar").with(como(beto))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"datos\": {\"aprobado\": true}}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/casos/" + prueba).with(como(carla)))
                .andExpect(jsonPath("$.variables.monto").value(5))
                .andExpect(jsonPath("$.variables.tarea.approveRefund.aprobado").value(true));
    }

    // ---------- El formulario en el BPMN ----------

    @Test
    @DisplayName("El formulario sale en el BPMN como bpm:formulario y vuelve entero al importarlo")
    void idaYVueltaPorElBpmn() throws Exception {
        conFormularios();
        String xml = mvc.perform(get("/api/v1/procesos/" + proceso + "/modelo").with(como(ana)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(xml).contains("<bpm:formulario>", "nombre=\"montoAprobado\"", "requeridoSi=\"aprobado == true\"");

        JsonNode importacion = cuerpo(mvc.perform(post("/api/v1/procesos/importar").param("nombre", "Refunds copy")
                        .with(como(ana)).contentType(MediaType.APPLICATION_XML).content(xml))
                .andExpect(status().isCreated()));
        long copia = importacion.get("procesoId").asLong();

        JsonNode original = cuerpo(mvc.perform(get("/api/v1/procesos/" + proceso + "/diagrama").with(como(ana))));
        JsonNode importado = cuerpo(mvc.perform(get("/api/v1/procesos/" + copia + "/diagrama").with(como(ana))));
        assertThat(formularioDe(importado, "actividades", "Approve refund"))
                .isEqualTo(formularioDe(original, "actividades", "Approve refund"));
        assertThat(formularioDe(importado, "eventos", "Refund requested"))
                .isEqualTo(formularioDe(original, "eventos", "Refund requested"));
        assertThat(importado.get("actividades").findValues("formulario").stream().filter(nodo -> !nodo.isNull()))
                .hasSize(1);
    }

    @Test
    @DisplayName("Un formulario roto que llega por el BPMN se guarda, pero el diagnostico lo marca E-22 y no se publica")
    void rotoPorElBpmn() throws Exception {
        conFormularios();
        String xml = mvc.perform(get("/api/v1/procesos/" + proceso + "/modelo").with(como(ana)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String roto = xml.replace("patron=\"ORD-\\d+\"", "patron=\"(a)\\1\"");
        assertThat(roto).isNotEqualTo(xml);

        mvc.perform(put("/api/v1/procesos/" + proceso + "/modelo").with(como(ana))
                .contentType(MediaType.APPLICATION_XML).content(roto)).andExpect(status().isOk());

        mvc.perform(get("/api/v1/procesos/" + proceso + "/diagnostico").with(como(carla)))
                .andExpect(jsonPath("$.hallazgos[?(@.codigo == 'E-22')].elementoId").value(
                        contains(inicio.intValue())));
        Long autora = ana.usuarioId();
        assertThatThrownBy(() -> publicar(empresaId, autora, proceso))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("1 error");
    }

    @Test
    @DisplayName("Una condicion de 100 KB que llega por el BPMN no se compila: el diagrama se lee y el diagnostico la marca")
    void condicionEnormePorElBpmn() throws Exception {
        conFormularios();
        String xml = mvc.perform(get("/api/v1/procesos/" + proceso + "/modelo").with(como(ana)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String enorme = "not ".repeat(25_000) + "aprobado == true";
        String roto = xml.replace("requeridoSi=\"aprobado == true\"", "requeridoSi=\"" + enorme + "\"");
        assertThat(roto).isNotEqualTo(xml);

        mvc.perform(put("/api/v1/procesos/" + proceso + "/modelo").with(como(ana))
                .contentType(MediaType.APPLICATION_XML).content(roto)).andExpect(status().isOk());

        JsonNode campo = formularioDe(cuerpo(mvc.perform(get("/api/v1/procesos/" + proceso + "/diagrama")
                .with(como(carla))).andExpect(status().isOk())), "actividades", "Approve refund").get("campos").get(1);
        assertThat(campo.get("requeridoSi").asString()).hasSize(enorme.length());
        assertThat(campo.path("requeridoSiArbol").isNull() || campo.path("requeridoSiArbol").isMissingNode()).isTrue();
        mvc.perform(get("/api/v1/procesos/" + proceso + "/diagnostico").with(como(carla)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hallazgos[?(@.codigo == 'E-22')].elementoId").value(contains(aprobar.intValue())))
                .andExpect(jsonPath("$.hallazgos[?(@.codigo == 'E-22')].problema").value(contains(
                        containsString("1000 caracteres"))));
    }

    @Test
    @DisplayName("Guardar desde el modelador lo que se exporto no cambia nada, aunque el formulario diga false o 1E+2")
    void guardarOtraVezNoCambia() throws Exception {
        String formulario = """
                {"version": 0, "campos": [
                  {"nombre": "aprobado", "etiqueta": "Approved?", "tipo": "SI_NO", "requerido": false, "ayuda": ""},
                  {"nombre": "piezas", "etiqueta": "Pieces", "tipo": "NUMERO", "minimo": 1E+2, "maximo": 1000.00},
                  {"nombre": "canal", "etiqueta": "Channel", "tipo": "LISTA",
                   "opciones": [{"valor": "WEB", "etiqueta": ""}]},
                  {"nombre": "nota", "etiqueta": "Note", "tipo": "TEXTO", "opciones": []}]}""";
        mvc.perform(put("/api/v1/actividades/" + aprobar + "/formulario").with(como(ana))
                        .contentType(MediaType.APPLICATION_JSON).content(formulario))
                .andExpect(status().isOk()).andExpect(jsonPath("$.formulario.campos[1].minimo").value(100));
        String xml = mvc.perform(get("/api/v1/procesos/" + proceso + "/modelo").with(como(ana)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        int version = cuerpo(mvc.perform(get("/api/v1/actividades/" + aprobar).with(como(ana)))).get("version")
                .asInt();
        // El modelador lo vuelve a escribir a su manera: un archivo identico ni se lee, este si
        String comoLoGuardaElModelador = xml.replace("<bpm:formulario>", "<bpm:formulario >");
        assertThat(comoLoGuardaElModelador).isNotEqualTo(xml);

        mvc.perform(put("/api/v1/procesos/" + proceso + "/modelo").with(como(ana))
                        .contentType(MediaType.APPLICATION_XML).content(comoLoGuardaElModelador))
                .andExpect(status().isOk()).andExpect(jsonPath("$.creados").value(0))
                .andExpect(jsonPath("$.editados").value(0)).andExpect(jsonPath("$.dadosDeBaja").value(0));
        mvc.perform(get("/api/v1/actividades/" + aprobar).with(como(ana)))
                .andExpect(jsonPath("$.version").value(version));
    }

    // ---------- Cada tienda, lo suyo ----------

    @Test
    @DisplayName("PostgreSQL esconde los formularios de otra tienda")
    void seguridadPorFilas() throws Exception {
        conFormularios();
        Long suyo = reembolsoDe(otraId, ajeno.usuarioId());
        mvc.perform(put("/api/v1/actividades/" + suyo + "/formulario").with(como(ajeno))
                        .contentType(MediaType.APPLICATION_JSON).content(DE_LA_TAREA.formatted(0)))
                .andExpect(status().isOk());

        assertThat(ContextoDeTienda.deLaTienda(empresaId, () -> jdbc.queryForList(
                "select distinct empresa_id from nodos_flujo where formulario is not null", Long.class)))
                .containsExactly(empresaId);
        assertThat(ContextoDeTienda.deLaTienda(empresaId, () -> jdbc.update(
                "update nodos_flujo set formulario = null where id = ?", suyo))).isZero();
        assertThat(ContextoDeTienda.deLaTienda(otraId, () -> jdbc.queryForList(
                "select id from nodos_flujo where formulario is not null", Long.class))).containsExactly(suyo);
    }

    // ---------- Lo que se arma para cada prueba ----------

    /** Un inicio, la tarea de aprobar, un gateway que lee la respuesta, la de pagar y dos fines. */
    private void reembolso(Long tienda, Long autor) {
        proceso = procesoService.crear(tienda, autor, "Refunds " + ++tiendas, "Lo que hace", "Ops").id();
        Long lane = lane(proceso);
        inicio = eventoService.crear(tienda, autor, lane, "Refund requested", TipoEvento.INICIO, 0, 0).id();
        aprobar = actividadService.crear(tienda, autor, lane, "Approve refund", null, TipoActividad.USUARIO, 100, 0)
                .id();
        Long decidir = gatewayService.crear(tienda, autor, lane, "Approved?", TipoGateway.EXCLUSIVO, 200, 0).id();
        pagar = actividadService.crear(tienda, autor, lane, "Pay refund", null, TipoActividad.USUARIO, 300, 0).id();
        Long pagado = eventoService.crear(tienda, autor, lane, "Refund paid", TipoEvento.FIN, 400, 0).id();
        Long rechazado = eventoService.crear(tienda, autor, lane, "Refund refused", TipoEvento.FIN, 300, 100).id();
        arcoService.crear(tienda, autor, DatosDeArco.entre(inicio, aprobar));
        arcoService.crear(tienda, autor, DatosDeArco.entre(aprobar, decidir));
        arcoService.crear(tienda, autor, new DatosDeArco(decidir, pagar, "Yes",
                "tarea.approveRefund.aprobado == true", false, 0));
        arcoService.crear(tienda, autor, new DatosDeArco(decidir, rechazado, "No", null, true, 1));
        arcoService.crear(tienda, autor, DatosDeArco.entre(pagar, pagado));
    }

    /** El mismo reembolso en otra tienda; devuelve su tarea de aprobar. */
    private Long reembolsoDe(Long tienda, Long autor) {
        Long[] mios = {proceso, inicio, aprobar, pagar};
        reembolso(tienda, autor);
        Long suyo = aprobar;
        proceso = mios[0];
        inicio = mios[1];
        aprobar = mios[2];
        pagar = mios[3];
        return suyo;
    }

    private void conFormularios() throws Exception {
        mvc.perform(put("/api/v1/eventos/" + inicio + "/formulario").with(como(ana))
                .contentType(MediaType.APPLICATION_JSON).content(DE_INICIO.formatted(0))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/actividades/" + aprobar + "/formulario").with(como(ana))
                .contentType(MediaType.APPLICATION_JSON).content(DE_LA_TAREA.formatted(0))).andExpect(status().isOk());
    }

    /** Un caso de produccion con un pedido que cumple el formulario de inicio. */
    private Long abrir() throws Exception {
        String variables = "{\"pedido\": \"ORD-1001\", \"monto\": 150000.50}";
        return id(mvc.perform(post("/api/v1/procesos/" + proceso + "/casos").with(como(beto))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"referencia\": \"ORD-1001\", \"modo\": \"PRODUCCION\", \"variables\": " + variables
                                + "}"))
                .andExpect(status().isCreated()));
    }

    private Long tareaQueEspera(Long caso) {
        List<Long> tareas = jdbc.queryForList("select id from actividades_caso where caso_id = ? and estado = "
                + "'EN_ESPERA' and tipo_nodo = 'ACTIVIDAD' order by id", Long.class, caso);
        assertThat(tareas).hasSize(1);
        return tareas.getFirst();
    }

    private Long lane(Long enProceso) {
        Long tienda = jdbc.queryForObject("select empresa_id from procesos where id = ?", Long.class, enProceso);
        Long autor = tienda.equals(empresaId) ? ana.usuarioId() : ajeno.usuarioId();
        List<Long> lanes = jdbc.queryForList("select l.id from lanes l join pools p on p.id = l.pool_id "
                + "where p.proceso_id = ? order by l.id", Long.class, enProceso);
        if (!lanes.isEmpty()) {
            return lanes.getFirst();
        }
        Long pool = poolService.listarPorProceso(tienda, enProceso).getFirst().id();
        return laneService.crear(tienda, autor, pool, "Support",
                rolProcesoService.crear(tienda, autor, "Support " + tiendas, null).id()).id();
    }

    private void publicar(Long tienda, Long autor, Long enProceso) {
        procesoService.cambiarEstado(tienda, enProceso, autor, EstadoProceso.PUBLICADO,
                procesoService.obtener(tienda, enProceso, false).version());
    }

    private static JsonNode formularioDe(JsonNode diagrama, String lista, String nombre) {
        for (JsonNode nodo : diagrama.get(lista)) {
            if (nombre.equals(nodo.get("nombre").asString())) {
                return nodo.get("formulario");
            }
        }
        throw new AssertionError("No hay ningun nodo llamado " + nombre);
    }

    private Long id(ResultActions respuesta) throws Exception {
        return cuerpo(respuesta).get("id").asLong();
    }

    private JsonNode cuerpo(ResultActions respuesta) throws Exception {
        return json.readTree(respuesta.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
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
