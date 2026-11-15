package com.facimus.procesos.modelado;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
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
 * PR 62: la documentacion y el manual de punta a punta, por HTTP: escribir en Markdown y leerla saneada, la ficha,
 * el dibujo de la version que manda la web, y el manual que arma la cola, en HTML y en PDF.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentacionIntegracionTest {

    private static final String CLAVE = "un-faro-sobre-la-bahia-quieta";
    private static final Path ALMACEN = carpeta();
    private static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"200\" height=\"60\">"
            + "<script>alert(1)</script><rect width=\"200\" height=\"60\" onclick=\"alert(2)\"/>"
            + "<text x=\"10\" y=\"30\">Despacho</text></svg>";
    private static int tiendas;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("almacen.directorio", ALMACEN::toString);
    }

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
    private EventoService eventoService;
    @Autowired
    private ActividadService actividadService;
    @Autowired
    private ArcoService arcoService;
    @Autowired
    private ColaDeTrabajos cola;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper json;

    private ApiPrincipal ana;
    private ApiPrincipal beto;
    private ApiPrincipal carla;
    private ApiPrincipal ajeno;
    private Long proceso;
    private String tarea;

    private static Path carpeta() {
        try {
            return Files.createTempDirectory("almacen-manuales-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @BeforeEach
    void registrar() {
        int numero = ++tiendas;
        String correo = "ana" + numero + "@manual.test";
        Long empresaId = empresaService.registrar("Manual " + numero, "9631" + numero + "000-1", correo, "Ana",
                correo, CLAVE).id();
        ana = persona(correo, RolAcceso.ADMINISTRADOR);
        beto = persona(usuarioService.crearColaborador(empresaId, null, "Beto", "beto" + numero + "@manual.test",
                CLAVE, RolAcceso.EDITOR).email(), RolAcceso.EDITOR);
        carla = persona(usuarioService.crearColaborador(empresaId, null, "Carla", "carla" + numero + "@manual.test",
                CLAVE, RolAcceso.SOLO_LECTURA).email(), RolAcceso.SOLO_LECTURA);
        String otro = "otra" + numero + "@manual.test";
        empresaService.registrar("Otra " + numero, "9632" + numero + "000-1", otro, "Otra", otro, CLAVE);
        ajeno = persona(otro, RolAcceso.ADMINISTRADOR);

        Long admin = ana.usuarioId();
        proceso = procesoService.crear(empresaId, admin, "Despacho", "Del pago a la entrega", "Fulfillment").id();
        Long tienda = poolService.listarPorProceso(empresaId, proceso).getFirst().id();
        Long rol = rolProcesoService.crear(empresaId, admin, "Bodega", null).id();
        Long lane = laneService.crear(empresaId, admin, tienda, "Bodega", rol).id();
        Long inicio = eventoService.crear(empresaId, admin, lane, "Pedido pagado", TipoEvento.INICIO, 20, 80).id();
        Long empacar = actividadService.crear(empresaId, admin, lane, "Empacar", "Con cinta", TipoActividad.USUARIO,
                160, 80).id();
        Long fin = eventoService.crear(empresaId, admin, lane, "Pedido listo", TipoEvento.FIN, 320, 80).id();
        arcoService.crear(empresaId, admin, DatosDeArco.entre(inicio, empacar));
        arcoService.crear(empresaId, admin, DatosDeArco.entre(empacar, fin));
        tarea = jdbc.queryForObject("select bpmn_id from nodos_flujo where id = ?", String.class, empacar);
    }

    private void publicar() {
        procesoService.cambiarEstado(ana.empresaId(), proceso, ana.usuarioId(), EstadoProceso.PUBLICADO,
                procesoService.obtener(ana.empresaId(), proceso, false).version());
    }

    private ApiPrincipal persona(String correo, RolAcceso rol) {
        var usuario = usuarioRepository.findByEmail(correo).orElseThrow();
        return new ApiPrincipal(usuario.getId(), usuario.getEmpresa().getId(), rol, correo,
                ApiPrincipalRequestPostProcessor.SESION, false);
    }

    private ResultActions escribir(ApiPrincipal quien, String elemento, String markdown, Long version)
            throws Exception {
        Map<String, Object> pedido = new HashMap<>();
        pedido.put("elemento", elemento);
        pedido.put("markdown", markdown);
        pedido.put("version", version);
        String cuerpo = json.writeValueAsString(pedido);
        return mvc.perform(put("/api/v1/procesos/{id}/documentacion", proceso).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo).with(principal(quien)));
    }

    private JsonNode leer(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private void armarTodo() {
        while (cola.procesarUno()) {
            // la cola, hasta vaciarse
        }
    }

    @Test
    @DisplayName("la documentacion se escribe en Markdown y se lee como HTML saneado, del proceso y de un paso")
    void documentacion() throws Exception {
        escribir(beto, null, "## Despacho\n\nTodo el proceso <script>x</script>", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.html").value(containsString("<h2>Despacho</h2>")))
                .andExpect(jsonPath("$.html").value(not(containsString("<script>"))))
                .andExpect(jsonPath("$.version").value(0));
        escribir(beto, tarea, "Usar **doble** cinta", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.bpmnId").value(tarea));

        mvc.perform(get("/api/v1/procesos/{id}/documentacion", proceso).with(principal(carla)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        escribir(beto, tarea, "Otra cosa", 7L).andExpect(status().isConflict());
        escribir(beto, "Activity_no_existe", "x", null).andExpect(status().isNotFound());
        escribir(carla, null, "x", 0L).andExpect(status().isForbidden());
        escribir(ajeno, null, "x", null).andExpect(status().isNotFound());
        escribir(beto, tarea, " ", 0L).andExpect(status().isOk()).andExpect(jsonPath("$.markdown").value(""));
        mvc.perform(get("/api/v1/procesos/{id}/documentacion", proceso).with(principal(beto)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("la vista previa devuelve el HTML que se guardaria, sin guardar nada")
    void vistaPrevia() throws Exception {
        mvc.perform(post("/api/v1/documentacion/vista-previa").contentType(MediaType.APPLICATION_JSON)
                .content("{\"markdown\":\"[ver](javascript:alert(1)) **si**\"}").with(principal(beto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.html").value("<p>ver <strong>si</strong></p>"));
        mvc.perform(get("/api/v1/procesos/{id}/documentacion", proceso).with(principal(beto)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("la ficha empieza vacia, se escribe entera y se edita con su version")
    void ficha() throws Exception {
        mvc.perform(get("/api/v1/procesos/{id}/caracterizacion", proceso).with(principal(carla)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").isEmpty());
        mvc.perform(put("/api/v1/procesos/{id}/caracterizacion", proceso).contentType(MediaType.APPLICATION_JSON)
                .content("{\"objetivo\":\"Entregar a tiempo\",\"riesgos\":\"Retrasos\"}").with(principal(beto)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.objetivo").value("Entregar a tiempo"))
                .andExpect(jsonPath("$.version").value(0));
        mvc.perform(put("/api/v1/procesos/{id}/caracterizacion", proceso).contentType(MediaType.APPLICATION_JSON)
                .content("{\"objetivo\":\"Otra\"}").with(principal(beto))).andExpect(status().isConflict());
        mvc.perform(put("/api/v1/procesos/{id}/caracterizacion", proceso).contentType(MediaType.APPLICATION_JSON)
                .content("{\"objetivo\":\"" + "x".repeat(4001) + "\",\"version\":0}").with(principal(beto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("el dibujo de la version se guarda saneado una sola vez, y solo quien puede publicar")
    void dibujo() throws Exception {
        publicar();

        mvc.perform(put("/api/v1/procesos/{id}/versiones/1/dibujo", proceso).contentType("image/svg+xml")
                .content(SVG).with(principal(carla))).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/procesos/{id}/versiones/1/dibujo", proceso).contentType("image/svg+xml")
                .content(SVG).with(principal(beto))).andExpect(status().isNoContent());
        mvc.perform(put("/api/v1/procesos/{id}/versiones/1/dibujo", proceso).contentType("image/svg+xml")
                .content(SVG).with(principal(beto))).andExpect(status().isConflict());
        mvc.perform(put("/api/v1/procesos/{id}/versiones/1/dibujo", proceso).contentType("image/svg+xml")
                .content("<html/>").with(principal(ana))).andExpect(status().isConflict());

        String guardado = jdbc.queryForObject("select svg from versiones_proceso where proceso_id = ? and numero = 1",
                String.class, proceso);
        assertThat(guardado).contains("<rect", "Despacho").doesNotContain("script", "onclick");
    }

    @Test
    @DisplayName("el manual se arma en la cola con la ficha, el dibujo y la documentacion, y se baja en PDF y HTML")
    void manual() throws Exception {
        escribir(beto, null, "Todo el proceso", null);
        escribir(beto, tarea, "Usar **doble** cinta", null);
        mvc.perform(put("/api/v1/procesos/{id}/caracterizacion", proceso).contentType(MediaType.APPLICATION_JSON)
                .content("{\"objetivo\":\"Entregar a tiempo\"}").with(principal(beto)));
        publicar();
        mvc.perform(put("/api/v1/procesos/{id}/versiones/1/dibujo", proceso).contentType("image/svg+xml")
                .content(SVG).with(principal(beto)));

        long manual = leer(mvc.perform(post("/api/v1/procesos/{id}/manuales", proceso).with(principal(beto)))
                .andExpect(status().isAccepted()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE")).andExpect(jsonPath("$.version").value(1)))
                .get("id").asLong();
        mvc.perform(get("/api/v1/manuales/{id}/enlace", manual).with(principal(carla))).andExpect(status().isConflict());

        armarTodo();

        mvc.perform(get("/api/v1/manuales/{id}", manual).with(principal(carla)))
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"));
        String pdf = leer(mvc.perform(get("/api/v1/manuales/{id}/enlace", manual).with(principal(carla))))
                .get("url").asString();
        byte[] archivo = mvc.perform(get(pdf)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        try (PDDocument documento = Loader.loadPDF(archivo)) {
            // La negrita va en otra fuente, y el extractor la pone al final de su linea: se buscan las palabras
            assertThat(new PDFTextStripper().getText(documento)).contains("Despacho", "Entregar a tiempo",
                    "Todo el proceso", "Empacar", "Usar", "doble", "Lo hace: Bodega");
        }
        String html = leer(mvc.perform(get("/api/v1/manuales/{id}/enlace", manual).param("formato", "html")
                .with(principal(carla)))).get("url").asString();
        String pagina = mvc.perform(get(html)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(pagina).contains("<rect", "<strong>doble</strong>").doesNotContain("<script", "onclick");
    }

    @Test
    @DisplayName("sin version vigente no hay manual; uno de otra tienda no existe; quien solo lee no lo pide")
    void manualSinVersion() throws Exception {
        mvc.perform(post("/api/v1/procesos/{id}/manuales", proceso).with(principal(beto)))
                .andExpect(status().isConflict());
        publicar();
        mvc.perform(post("/api/v1/procesos/{id}/manuales", proceso).with(principal(carla)))
                .andExpect(status().isForbidden());
        long manual = leer(mvc.perform(post("/api/v1/procesos/{id}/manuales", proceso).with(principal(beto))))
                .get("id").asLong();

        mvc.perform(get("/api/v1/manuales/{id}", manual).with(principal(ajeno))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/procesos/{id}/manuales", proceso).with(principal(ajeno)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/procesos/{id}/manuales", proceso).with(principal(carla)))
                .andExpect(jsonPath("$.length()").value(1));
    }
}
