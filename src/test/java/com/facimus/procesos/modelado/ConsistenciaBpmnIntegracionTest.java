package com.facimus.procesos.modelado;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.modelado.service.GatewayService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.PoolService;

import tools.jackson.databind.json.JsonMapper;

/** Reglas BPMN que el modelo cumple siempre, se cree o se edite cada elemento. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsistenciaBpmnIntegracionTest {

    private static final String ADMIN = "admin@consistencia.com";
    private static final String CLAVE = "clave12345";

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
    private RolProcesoService rolProcesoService;

    @Autowired
    private PoolService poolService;

    @Autowired
    private LaneService laneService;

    @Autowired
    private ActividadService actividadService;

    @Autowired
    private GatewayService gatewayService;

    @Autowired
    private ArcoService arcoService;

    @Autowired
    private EventoService eventoService;

    private String token;
    private Long empresaId;
    private Long adminId;
    private Long procesoId;
    private Long tiendaId;
    private Long clienteId;
    private Long laneId;

    @BeforeAll
    void modelarUnProceso() throws Exception {
        empresaId = empresaService.registrar("Tienda consistente", "900141414-5", "contacto@consistencia.com",
                "Administradora", ADMIN, CLAVE).id();
        adminId = usuarioRepository.findByEmail(ADMIN).orElseThrow().getId();
        procesoId = procesoService.crear(empresaId, adminId, "Order fulfillment", "Checkout to delivery",
                "Fulfillment").id();
        tiendaId = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        clienteId = poolService.crear(empresaId, adminId, procesoId, "Customer", TipoParticipante.CLIENTE, true).id();
        laneId = laneService.crear(empresaId, adminId, tiendaId, "Warehouse",
                rolProcesoService.crear(empresaId, "Warehouse", null).id()).id();
        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(ADMIN, CLAVE))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = jsonMapper.readTree(login).get("accessToken").asString();
    }

    @Test
    @DisplayName("Un mensaje solo conecta participantes de su propio proceso")
    void mensaje_conPoolDeOtroProceso_devuelve409() throws Exception {
        Long otroProceso = procesoService.crear(empresaId, adminId, "Returns", "Return to refund", "After-sales").id();
        Long poolAjeno = poolService.crear(empresaId, adminId, otroProceso, "Carrier", TipoParticipante.PROVEEDOR,
                true).id();

        pedir(post("/api/v1/procesos/{id}/mensajes", procesoId), Map.of("nombre", "Shipment request",
                "contenido", "Package", "poolOrigenId", tiendaId, "poolDestinoId", poolAjeno))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Los dos pools de un mensaje tienen que ser participantes de su proceso."));
        pedir(post("/api/v1/procesos/{id}/mensajes", procesoId), Map.of("nombre", "Pickup notice",
                "contenido", "Package", "poolOrigenId", poolAjeno, "poolDestinoId", tiendaId))
                .andExpect(status().isConflict());
        pedir(post("/api/v1/procesos/{id}/mensajes", procesoId), Map.of("nombre", "Order placed",
                "contenido", "Cart", "poolOrigenId", clienteId, "poolDestinoId", tiendaId))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Renombrar un nodo con el nombre de otro nodo del proceso responde 409; con el suyo, no")
    void nodo_renombradoComoOtro_devuelve409() throws Exception {
        actividadService.crear(empresaId, adminId, laneId, "Pick items", null, TipoActividad.USUARIO, 100, 80);
        Long empacar = actividadService.crear(empresaId, adminId, laneId, "Pack items", null, TipoActividad.USUARIO,
                260, 80).id();
        Long decidir = gatewayService.crear(empresaId, adminId, laneId, "Split", TipoGateway.PARALELO, 420, 80).id();

        pedir(put("/api/v1/actividades/{id}", empacar), Map.of("nombre", "PICK ITEMS", "posicionX", 260,
                "posicionY", 80, "version", 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Ya existe un nodo con el nombre \"PICK ITEMS\" en este proceso."));
        pedir(put("/api/v1/gateways/{id}", decidir), Map.of("nombre", "Pick items", "tipoGateway", "PARALELO",
                "posicionX", 420, "posicionY", 80, "version", 0))
                .andExpect(status().isConflict());
        pedir(put("/api/v1/actividades/{id}", empacar), Map.of("nombre", "Pack Items", "posicionX", 300,
                "posicionY", 80, "version", 0))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Los arcos que salen de un gateway exclusivo o inclusivo llevan condicion; los que entran, no")
    void arco_queSaleDeUnGatewayQueDecide_exigeCondicion() throws Exception {
        Long revisar = actividadService.crear(empresaId, adminId, laneId, "Review return", null, TipoActividad.USUARIO,
                100, 240).id();
        Long aprobada = gatewayService.crear(empresaId, adminId, laneId, "Return approved?", TipoGateway.EXCLUSIVO,
                260, 240).id();
        Long reembolsar = actividadService.crear(empresaId, adminId, laneId, "Refund", null, TipoActividad.USUARIO, 420,
                240).id();
        Long avisar = gatewayService.crear(empresaId, adminId, laneId, "Notify?", TipoGateway.INCLUSIVO, 580, 240).id();
        Long repartir = gatewayService.crear(empresaId, adminId, laneId, "Fork", TipoGateway.PARALELO, 740, 240).id();

        pedir(post("/api/v1/arcos"), Map.of("origenId", revisar, "destinoId", aprobada))
                .andExpect(status().isCreated());
        pedir(post("/api/v1/arcos"), Map.of("origenId", aprobada, "destinoId", reembolsar, "etiqueta", "Yes"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Un arco que sale de un gateway exclusivo o inclusivo requiere condicion."));
        pedir(post("/api/v1/arcos"), Map.of("origenId", avisar, "destinoId", reembolsar, "condicion", "  "))
                .andExpect(status().isConflict());
        pedir(post("/api/v1/arcos"), Map.of("origenId", repartir, "destinoId", reembolsar))
                .andExpect(status().isCreated());
        String creado = pedir(post("/api/v1/arcos"), Map.of("origenId", aprobada, "destinoId", reembolsar,
                "etiqueta", "Yes", "condicion", "return.approved"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long arcoId = jsonMapper.readTree(creado).get("id").asLong();

        pedir(put("/api/v1/arcos/{id}", arcoId), Map.of("etiqueta", "Yes", "version", 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Un arco que sale de un gateway exclusivo o inclusivo requiere condicion."));
        pedir(put("/api/v1/arcos/{id}", arcoId), Map.of("etiqueta", "Yes", "condicion", "return.ok", "version", 0))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un gateway pasa a exclusivo o inclusivo solo cuando todos sus arcos de salida llevan condicion")
    void gateway_quePasaADecidirConSalidasSinCondicion_devuelve409() throws Exception {
        Long repartir = gatewayService.crear(empresaId, adminId, laneId, "Ship boxes", TipoGateway.PARALELO, 100, 400)
                .id();
        Long cajaA = actividadService.crear(empresaId, adminId, laneId, "Ship box A", null, TipoActividad.USUARIO, 260,
                360).id();
        Long cajaB = actividadService.crear(empresaId, adminId, laneId, "Ship box B", null, TipoActividad.USUARIO, 260,
                440).id();
        Long haciaA = arcoService.crear(empresaId, adminId, repartir, cajaA, null, null).id();
        arcoService.crear(empresaId, adminId, repartir, cajaB, null, "order.hasBoxB");

        pedir(put("/api/v1/gateways/{id}", repartir), Map.of("nombre", "Ship boxes", "tipoGateway", "INCLUSIVO",
                "posicionX", 100, "posicionY", 400, "version", 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Todos los arcos que salen de un gateway exclusivo o inclusivo requieren condicion."));
        pedir(put("/api/v1/arcos/{id}", haciaA), Map.of("condicion", "order.hasBoxA", "version", 0))
                .andExpect(status().isOk());
        pedir(put("/api/v1/gateways/{id}", repartir), Map.of("nombre", "Ship boxes", "tipoGateway", "INCLUSIVO",
                "posicionX", 100, "posicionY", 400, "version", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoGateway").value("INCLUSIVO"));
    }

    @Test
    @DisplayName("R-31 y R-32: nada llega a un evento de inicio y nada sale de un evento de fin")
    void arco_conEventosDeInicioYFin_devuelve409() throws Exception {
        Long inicio = eventoService.crear(empresaId, adminId, laneId, "Return requested", TipoEvento.MENSAJE_INICIO,
                20, 500).id();
        Long revisar = actividadService.crear(empresaId, adminId, laneId, "Inspect item", null,
                TipoActividad.USUARIO, 160, 500).id();
        Long fin = eventoService.crear(empresaId, adminId, laneId, "Return closed", TipoEvento.FIN, 320, 500).id();

        pedir(post("/api/v1/arcos"), Map.of("origenId", inicio, "destinoId", revisar))
                .andExpect(status().isCreated());
        pedir(post("/api/v1/arcos"), Map.of("origenId", revisar, "destinoId", fin))
                .andExpect(status().isCreated());
        pedir(post("/api/v1/arcos"), Map.of("origenId", revisar, "destinoId", inicio))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Un evento de inicio no puede tener arcos entrantes."));
        pedir(post("/api/v1/arcos"), Map.of("origenId", fin, "destinoId", revisar))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Un evento de fin no puede tener arcos salientes."));

        // Los eventos de mensaje cuentan igual: el catch de inicio abre el proceso y el throw de fin lo cierra.
        Long avisoFinal = eventoService.crear(empresaId, adminId, laneId, "Refund notified",
                TipoEvento.MENSAJE_FIN, 460, 500).id();
        pedir(post("/api/v1/arcos"), Map.of("origenId", revisar, "destinoId", avisoFinal))
                .andExpect(status().isCreated());
        pedir(post("/api/v1/arcos"), Map.of("origenId", avisoFinal, "destinoId", revisar))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Un evento de fin no puede tener arcos salientes."));
    }

    @Test
    @DisplayName("Un evento con arcos no cambia a un tipo que esos arcos no admiten")
    void evento_conArcos_noCambiaAUnTipoQueLosProhibe() throws Exception {
        Long intermedio = eventoService.crear(empresaId, adminId, laneId, "Refund confirmed",
                TipoEvento.MENSAJE_INTERMEDIO, 480, 500).id();
        Long pagar = actividadService.crear(empresaId, adminId, laneId, "Refund the customer", null,
                TipoActividad.SERVICIO, 640, 500).id();
        arcoService.crear(empresaId, adminId, intermedio, pagar, null, null);

        pedir(put("/api/v1/eventos/{id}", intermedio), Map.of("nombre", "Refund confirmed", "tipoEvento", "FIN",
                "posicionX", 480, "posicionY", 500, "version", 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Un evento de fin no puede tener arcos salientes."));
        pedir(put("/api/v1/eventos/{id}", intermedio), Map.of("nombre", "Refund confirmed", "tipoEvento", "INICIO",
                "posicionX", 480, "posicionY", 500, "version", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoEvento").value("INICIO"));
    }

    private ResultActions pedir(MockHttpServletRequestBuilder peticion, Map<String, Object> cuerpo) throws Exception {
        return mockMvc.perform(peticion
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(cuerpo)));
    }
}
