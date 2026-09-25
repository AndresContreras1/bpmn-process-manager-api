package com.facimus.procesos.ejecucion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.ejecucion.dto.response.CasoResponse;
import com.facimus.procesos.ejecucion.service.CasoService;
import com.facimus.procesos.ejecucion.service.TareaService;
import com.facimus.procesos.gestion.dto.request.CompartirProcesoRequest;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoCompartidoService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.DatosDeArco;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.PoolService;

import tools.jackson.databind.json.JsonMapper;

/**
 * D10: la multitenencia tambien manda en la ejecucion. Un caso, una tarea y su linea de tiempo son de la tienda del
 * proceso: para cualquier otra no existen, y eso incluye a la invitada con la que se comparte un proceso (HU-23),
 * que puede leer lo publicado y nada mas.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AislamientoDeLaEjecucionTest {

    private static final String ADMIN_A = "admin@ejecucion-a.com";
    private static final String ADMIN_B = "admin@ejecucion-b.com";
    private static final String CLAVE = "clave12345";
    private static final String NIT_A = "900910001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ProcesoService procesoService;

    @Autowired
    private ProcesoCompartidoService procesoCompartidoService;

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
    private TareaService tareaService;

    private String tokenA;
    private Long empresaA;
    private Long empresaB;
    private Long adminB;
    private Long procesoB;
    private Long casoB;
    private Long tareaB;

    @BeforeAll
    void dosTiendasYUnCasoEnLaSegunda() throws Exception {
        empresaA = empresaService.registrar("Tienda A", NIT_A, "contacto@ejecucion-a.com", "Admin A", ADMIN_A,
                CLAVE).id();
        Long adminA = usuarioRepository.findByEmail(ADMIN_A).orElseThrow().getId();
        Long procesoA = publicarUnProceso(empresaA, adminA, "Order fulfillment A");
        casoService.abrir(empresaA, adminA, procesoA, "ORD-A", Map.of());

        empresaB = empresaService.registrar("Tienda B", "900910002", "contacto@ejecucion-b.com", "Admin B", ADMIN_B,
                CLAVE).id();
        adminB = usuarioRepository.findByEmail(ADMIN_B).orElseThrow().getId();
        procesoB = publicarUnProceso(empresaB, adminB, "Order fulfillment B");
        CasoResponse caso = casoService.abrir(empresaB, adminB, procesoB, "ORD-B", Map.of());
        casoB = caso.id();
        tareaB = tareaService.bandeja(empresaB, null, null, null, Paginacion.de(0, 10)).content().getFirst().id();

        tokenA = login(ADMIN_A);
    }

    Stream<Arguments> recursosDeLaOtraTienda() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/v1/casos/" + casoB, null, "Caso no encontrado."),
                Arguments.of(HttpMethod.GET, "/api/v1/casos/" + casoB + "/eventos", null, "Caso no encontrado."),
                Arguments.of(HttpMethod.POST, "/api/v1/casos/" + casoB + "/cancelar", null, "Caso no encontrado."),
                Arguments.of(HttpMethod.POST, "/api/v1/casos/" + casoB + "/reintentar", null, "Caso no encontrado."),
                Arguments.of(HttpMethod.PATCH, "/api/v1/casos/" + casoB + "/variables",
                        "{\"variables\":{\"payment\":{\"status\":\"APPROVED\"}},\"version\":0}",
                        "Caso no encontrado."),
                Arguments.of(HttpMethod.GET, "/api/v1/tareas/" + tareaB, null, "Tarea no encontrada."),
                Arguments.of(HttpMethod.POST, "/api/v1/tareas/" + tareaB + "/completar", "{}",
                        "Tarea no encontrada."),
                Arguments.of(HttpMethod.POST, "/api/v1/tareas/" + tareaB + "/asignar", "{}",
                        "Tarea no encontrada."),
                Arguments.of(HttpMethod.POST, "/api/v1/procesos/" + procesoB + "/casos", "{\"referencia\":\"ORD-X\"}",
                        "Proceso no encontrado."));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("recursosDeLaOtraTienda")
    @DisplayName("Un caso, una tarea o un proceso de otra tienda responden 404 y no se tocan")
    void Aislamiento_ejecucionDeOtraTienda_devuelve404(HttpMethod metodo, String ruta, String cuerpo, String detalle)
            throws Exception {
        var peticion = request(metodo, ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA);
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }

        mockMvc.perform(peticion)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.detail").value(detalle));
    }

    @Test
    @DisplayName("El listado de casos de una tienda no trae los de la otra")
    void listarCasos_soloLosDeLaTienda() throws Exception {
        mockMvc.perform(get("/api/v1/casos").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].referencia").value("ORD-A"));
    }

    @Test
    @DisplayName("La bandeja de una tienda no trae las tareas de la otra")
    void bandeja_soloLasDeLaTienda() throws Exception {
        mockMvc.perform(get("/api/v1/tareas").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].casoReferencia").value("ORD-A"));
    }

    @Test
    @DisplayName("HU-23: la invitada con la que se comparte un proceso tampoco abre casos en el")
    void invitada_noAbreCasos() throws Exception {
        procesoCompartidoService.compartir(empresaB, procesoB, adminB, new CompartirProcesoRequest(NIT_A).nit());

        mockMvc.perform(post("/api/v1/procesos/" + procesoB + "/casos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"referencia\":\"ORD-X\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Proceso no encontrado."));
    }

    /** Lo minimo publicable: una lane con su rol, un inicio, una tarea de usuario y un fin. */
    private Long publicarUnProceso(Long empresaId, Long adminId, String nombre) {
        Long procesoId = procesoService.crear(empresaId, adminId, nombre, "De la compra a la entrega",
                "Fulfillment").id();
        Long tienda = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        Long rol = rolProcesoService.crear(empresaId, adminId, "Sales", null).id();
        Long lane = laneService.crear(empresaId, adminId, tienda, "Sales", rol).id();
        Long inicio = eventoService.crear(empresaId, adminId, lane, "Order received", TipoEvento.INICIO, 20, 80)
                .id();
        Long recibir = actividadService.crear(empresaId, adminId, lane, "Receive order", "Check the cart",
                TipoActividad.USUARIO, 160, 80).id();
        Long fin = eventoService.crear(empresaId, adminId, lane, "Order accepted", TipoEvento.FIN, 320, 80).id();
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(inicio, recibir));
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(recibir, fin));
        procesoService.cambiarEstado(empresaId, procesoId, adminId, EstadoProceso.PUBLICADO,
                procesoService.obtener(empresaId, procesoId, false).version());
        return procesoId;
    }

    private String login(String correo) throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(correo, CLAVE))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(respuesta).get("accessToken").asString();
    }
}
