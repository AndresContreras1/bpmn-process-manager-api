package com.facimus.procesos.modelado;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
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
import java.time.LocalDate;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.gestion.model.Clasificacion;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.CarpetaService;
import com.facimus.procesos.gestion.service.DatosDeFicha;
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
 * F22: lo que el portal de lectura le pide a la API, hecho por quien solo lee. Encuentra lo publicado en su carpeta,
 * abre la ficha con el nombre del dueno y la revision vencida, dibuja la version vigente, lee la documentacion
 * saneada de un paso, baja su documento y el manual por sus enlaces firmados, y no puede cambiar ni pedir nada. Otra
 * tienda no ve nada de eso.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortalDeLecturaIntegracionTest {

    private static final String CLAVE = "un-mapa-de-la-costa-entera";
    private static final Path ALMACEN = carpeta();
    private static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"200\" height=\"60\">"
            + "<text x=\"10\" y=\"30\">Despacho</text></svg>";
    private static final String INSTRUCCIONES = "Envolver dos veces y poner la tarjeta encima.\n";
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
    private CarpetaService carpetaService;
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
    private Long empresaId;
    private Long carpeta;
    private Long proceso;
    private String tarea;

    private static Path carpeta() {
        try {
            return Files.createTempDirectory("almacen-portal-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @BeforeEach
    void registrar() {
        int numero = ++tiendas;
        String correo = "ana" + numero + "@portal.test";
        empresaId = empresaService.registrar("Portal " + numero, "9641" + numero + "000-1", correo, "Ana", correo,
                CLAVE).id();
        ana = persona(correo, RolAcceso.ADMINISTRADOR);
        beto = persona(usuarioService.crearColaborador(empresaId, null, "Beto Bodega", "beto" + numero
                + "@portal.test", CLAVE, RolAcceso.EDITOR).email(), RolAcceso.EDITOR);
        carla = persona(usuarioService.crearColaborador(empresaId, null, "Carla", "carla" + numero + "@portal.test",
                CLAVE, RolAcceso.SOLO_LECTURA).email(), RolAcceso.SOLO_LECTURA);
        String otro = "otra" + numero + "@portal.test";
        empresaService.registrar("Otra " + numero, "9642" + numero + "000-1", otro, "Otra", otro, CLAVE);
        ajeno = persona(otro, RolAcceso.ADMINISTRADOR);

        Long admin = ana.usuarioId();
        carpeta = carpetaService.crear(empresaId, "Bodega", null).id();
        proceso = procesoService.crear(empresaId, admin, "Despacho", "Del pago a la entrega", "Fulfillment").id();
        Long tienda = poolService.listarPorProceso(empresaId, proceso).getFirst().id();
        Long rol = rolProcesoService.crear(empresaId, admin, "Empaque", null).id();
        Long lane = laneService.crear(empresaId, admin, tienda, "Empaque", rol).id();
        Long inicio = eventoService.crear(empresaId, admin, lane, "Pedido pagado", TipoEvento.INICIO, 20, 80).id();
        Long empacar = actividadService.crear(empresaId, admin, lane, "Empacar", "Con cinta", TipoActividad.USUARIO,
                160, 80).id();
        Long fin = eventoService.crear(empresaId, admin, lane, "Pedido listo", TipoEvento.FIN, 320, 80).id();
        arcoService.crear(empresaId, admin, DatosDeArco.entre(inicio, empacar));
        arcoService.crear(empresaId, admin, DatosDeArco.entre(empacar, fin));
        tarea = jdbc.queryForObject("select bpmn_id from nodos_flujo where id = ?", String.class, empacar);
    }

    private ApiPrincipal persona(String correo, RolAcceso rol) {
        var usuario = usuarioRepository.findByEmail(correo).orElseThrow();
        return new ApiPrincipal(usuario.getId(), usuario.getEmpresa().getId(), rol, correo,
                ApiPrincipalRequestPostProcessor.SESION, false);
    }

    /** Archivado en su carpeta, con Beto de dueno y una revision que paso ayer sin que nadie la confirmara. */
    private void archivar() {
        Long version = procesoService.obtener(empresaId, proceso, false).version();
        procesoService.editarFicha(empresaId, proceso, ana.usuarioId(), new DatosDeFicha(carpeta, beto.usuarioId(),
                LocalDate.now().minusDays(1), Clasificacion.CONFIDENCIAL, List.of("envios"), version));
    }

    private void publicar() {
        procesoService.cambiarEstado(empresaId, proceso, ana.usuarioId(), EstadoProceso.PUBLICADO,
                procesoService.obtener(empresaId, proceso, false).version());
    }

    private JsonNode leer(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private void vaciarLaCola() {
        while (cola.procesarUno()) {
            // la cola, hasta vaciarse: el documento queda disponible y el manual armado
        }
    }

    private ResultActions como(ApiPrincipal quien, String ruta, Object... variables) throws Exception {
        return mvc.perform(get(ruta, variables).with(principal(quien)));
    }

    @Test
    @DisplayName("quien solo lee encuentra lo publicado, lee su ficha y su documentacion, y baja el documento y el "
            + "manual")
    void quienSoloLeeRecorreElPortal() throws Exception {
        archivar();
        mvc.perform(put("/api/v1/procesos/{id}/documentacion", proceso).contentType(MediaType.APPLICATION_JSON)
                .content("{\"elemento\":\"" + tarea + "\",\"markdown\":\"Usar **doble** cinta <script>x</script>\"}")
                .with(principal(beto))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/procesos/{id}/caracterizacion", proceso).contentType(MediaType.APPLICATION_JSON)
                .content("{\"objetivo\":\"Entregar a tiempo\"}").with(principal(beto))).andExpect(status().isOk());
        long documento = leer(mvc.perform(post("/api/v1/procesos/{id}/documentos", proceso)
                .param("nombre", "empaque.txt").param("elemento", tarea)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(INSTRUCCIONES.getBytes(StandardCharsets.UTF_8)).with(principal(beto)))
                .andExpect(status().isCreated())).get("id").asLong();
        publicar();
        mvc.perform(put("/api/v1/procesos/{id}/versiones/1/dibujo", proceso).contentType("image/svg+xml")
                .content(SVG).with(principal(beto))).andExpect(status().isNoContent());
        long manual = leer(mvc.perform(post("/api/v1/procesos/{id}/manuales", proceso).with(principal(beto)))
                .andExpect(status().isAccepted())).get("id").asLong();
        vaciarLaCola();

        // La carpeta lista lo publicado; un borrador de la misma carpeta no sale
        Long borrador = procesoService.crear(empresaId, ana.usuarioId(), "Devoluciones", "Sin publicar", "Ventas")
                .id();
        procesoService.editarFicha(empresaId, borrador, ana.usuarioId(), new DatosDeFicha(carpeta, null, null,
                Clasificacion.INTERNA, List.of(), procesoService.obtener(empresaId, borrador, false).version()));
        mvc.perform(get("/api/v1/procesos").param("estado", "PUBLICADO").param("carpetaId", carpeta.toString())
                .with(principal(carla))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(proceso))
                .andExpect(jsonPath("$.content[0].revisionVencida").value(true));

        // La busqueda encuentra el paso por su nombre, una sola vez aunque este en el borrador y en la version
        mvc.perform(get("/api/v1/busqueda").param("q", "empacar").param("tipo", "ELEMENTO").with(principal(carla)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].bpmnId").value(tarea));

        // La ficha, con el dueno por su nombre
        como(carla, "/api/v1/procesos/{id}", proceso).andExpect(status().isOk())
                .andExpect(jsonPath("$.duenoNombre").value("Beto Bodega"))
                .andExpect(jsonPath("$.proceso.versionPublicada").value(1))
                .andExpect(jsonPath("$.proceso.clasificacion").value("CONFIDENCIAL"))
                .andExpect(jsonPath("$.proceso.revisionVencida").value(true));
        como(carla, "/api/v1/procesos/{id}/caracterizacion", proceso)
                .andExpect(jsonPath("$.objetivo").value("Entregar a tiempo"));

        // Lo que la web oye de los permisos: el rol de lectura exporta por defecto, pero la matriz no lo deja pedir
        como(carla, "/api/v1/procesos/{id}/permisos", proceso)
                .andExpect(jsonPath("$.acciones", containsInAnyOrder("VER", "COMENTAR", "EXPORTAR")));

        // La version vigente, para dibujarla
        mvc.perform(get("/api/v1/procesos/{id}/versiones/1/modelo", proceso).param("para", "dibujar")
                .with(principal(carla))).andExpect(status().isOk()).andExpect(content().string(containsString(tarea)));

        // La documentacion del paso, ya saneada
        como(carla, "/api/v1/procesos/{id}/documentacion", proceso).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bpmnId").value(tarea))
                .andExpect(jsonPath("$[0].html").value(containsString("<strong>doble</strong>")))
                .andExpect(jsonPath("$[0].html").value(not(containsString("<script>"))));

        // El documento del paso y su enlace firmado, que baja sin sesion y como adjunto
        mvc.perform(get("/api/v1/procesos/{id}/documentos", proceso).param("elemento", tarea)
                .with(principal(carla))).andExpect(jsonPath("$[0].id").value(documento))
                .andExpect(jsonPath("$[0].estado").value("DISPONIBLE"));
        String enlace = leer(como(carla, "/api/v1/documentos/{id}/enlace", documento).andExpect(status().isOk()))
                .get("url").asString();
        String bajado = mvc.perform(get(enlace)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(bajado).isEqualTo(INSTRUCCIONES);

        // El manual que otro pidio, listo para bajar
        como(carla, "/api/v1/procesos/{id}/manuales", proceso)
                .andExpect(jsonPath("$[*].id", hasItem((int) manual)))
                .andExpect(jsonPath("$[0].estado").value("DISPONIBLE"));
        como(carla, "/api/v1/manuales/{id}/enlace", manual).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(containsString("/descargas/")));
    }

    @Test
    @DisplayName("quien solo lee no cambia ni pide nada, aunque los permisos digan que exporta")
    void quienSoloLeeNoCambiaNada() throws Exception {
        publicar();

        mvc.perform(put("/api/v1/procesos/{id}/documentacion", proceso).contentType(MediaType.APPLICATION_JSON)
                .content("{\"markdown\":\"x\"}").with(principal(carla))).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/procesos/{id}/caracterizacion", proceso).contentType(MediaType.APPLICATION_JSON)
                .content("{\"objetivo\":\"x\"}").with(principal(carla))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/procesos/{id}/documentos", proceso).param("nombre", "a.txt")
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content("a".getBytes(StandardCharsets.UTF_8))
                .with(principal(carla))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/procesos/{id}/manuales", proceso).with(principal(carla)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/procesos/{id}/ficha", proceso).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clasificacion\":\"INTERNA\",\"etiquetas\":[],\"version\":0}").with(principal(carla)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sin dueno el detalle no nombra a nadie, y su revision sin fecha no vence")
    void sinDueno() throws Exception {
        publicar();

        como(carla, "/api/v1/procesos/{id}", proceso).andExpect(status().isOk())
                .andExpect(jsonPath("$.duenoNombre").isEmpty())
                .andExpect(jsonPath("$.proceso.revisionVencida").value(false));
    }

    @Test
    @DisplayName("otra tienda no encuentra nada del portal: ni el proceso, ni su version, ni sus documentos")
    void otraTiendaNoVeNada() throws Exception {
        archivar();
        publicar();

        como(ajeno, "/api/v1/procesos/{id}", proceso).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/procesos/{id}/versiones/1/modelo", proceso).param("para", "dibujar")
                .with(principal(ajeno))).andExpect(status().isNotFound());
        como(ajeno, "/api/v1/procesos/{id}/documentacion", proceso).andExpect(status().isNotFound());
        como(ajeno, "/api/v1/procesos/{id}/caracterizacion", proceso).andExpect(status().isNotFound());
        como(ajeno, "/api/v1/procesos/{id}/documentos", proceso).andExpect(status().isNotFound());
        como(ajeno, "/api/v1/procesos/{id}/manuales", proceso).andExpect(status().isNotFound());
    }
}
