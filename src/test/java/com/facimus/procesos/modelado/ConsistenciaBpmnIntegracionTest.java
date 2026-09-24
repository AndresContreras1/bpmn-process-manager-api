package com.facimus.procesos.modelado;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.DatosDeArco;
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
        clienteId = poolService.crear(empresaId, adminId, procesoId, "Customer", TipoParticipante.CLIENTE, true,
                Integracion.NINGUNA).id();
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
                true,Integracion.NINGUNA).id();

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
    @DisplayName("R-37 y R-38: el mensaje se ancla a un nodo del pool de su lado que sepa mandarlo")
    void mensaje_conAnclajeInvalido_devuelve409() throws Exception {
        Long avisar = actividadService.crear(empresaId, adminId, laneId, "Notify the customer", null,
                TipoActividad.ENVIO, 100, 620).id();
        Long revisar = actividadService.crear(empresaId, adminId, laneId, "Check the address", null,
                TipoActividad.USUARIO, 260, 620).id();

        // El cliente es una caja negra: por dentro no se modela, asi que no ancla nodos.
        pedir(post("/api/v1/procesos/{id}/mensajes", procesoId), Map.of("nombre", "Delivery notice",
                "contenido", "Aviso", "poolOrigenId", tiendaId, "poolDestinoId", clienteId,
                "nodoDestinoId", avisar))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("caja negra")));
        pedir(post("/api/v1/procesos/{id}/mensajes", procesoId), Map.of("nombre", "Delivery notice",
                "contenido", "Aviso", "poolOrigenId", tiendaId, "poolDestinoId", clienteId,
                "nodoOrigenId", revisar))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El nodo de origen del mensaje debe poder enviarlo."));
        pedir(post("/api/v1/procesos/{id}/mensajes", procesoId), Map.of("nombre", "Delivery notice",
                "contenido", "Aviso", "poolOrigenId", tiendaId, "poolDestinoId", clienteId,
                "nodoOrigenId", avisar, "tipoDestino", "CORREO"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nodoOrigenId").value(avisar))
                .andExpect(jsonPath("$.variable").value("deliveryNotice"));
    }

    @Test
    @DisplayName("R-39: desviar el flujo por un envio fallido exige una actividad del pool que envia")
    void mensaje_conManejoDeError_exigeActividad() throws Exception {
        Long reintentar = actividadService.crear(empresaId, adminId, laneId, "Retry the shipment", null,
                TipoActividad.SERVICIO, 420, 620).id();

        pedir(post("/api/v1/procesos/{id}/mensajes", procesoId), Map.of("nombre", "Carrier request",
                "contenido", "Paquete", "poolOrigenId", tiendaId, "poolDestinoId", clienteId,
                "siFalla", "MANEJAR_ERROR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Indique la actividad que maneja el error."));
        pedir(post("/api/v1/procesos/{id}/mensajes", procesoId), Map.of("nombre", "Carrier request",
                "contenido", "Paquete", "poolOrigenId", tiendaId, "poolDestinoId", clienteId,
                "siFalla", "MANEJAR_ERROR", "nodoManejoErrorId", reintentar))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.siFalla").value("MANEJAR_ERROR"))
                .andExpect(jsonPath("$.nodoManejoErrorId").value(reintentar));
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
                .andExpect(jsonPath("$.detail").value("Un arco que sale de un gateway exclusivo o inclusivo "
                        + "requiere condicion o ser la salida por defecto."));
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
                .andExpect(jsonPath("$.detail").value("Un arco que sale de un gateway exclusivo o inclusivo "
                        + "requiere condicion o ser la salida por defecto."));
        pedir(put("/api/v1/arcos/{id}", arcoId), Map.of("etiqueta", "Yes", "condicion", "return.ok", "version", 0))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("R-42: el arco se reengancha a otro nodo sin borrarlo y volverlo a crear")
    void arco_cambiaDeExtremoConLasMismasReglas() throws Exception {
        Long recibir = actividadService.crear(empresaId, adminId, laneId, "Receive parcel", null,
                TipoActividad.USUARIO, 100, 800).id();
        Long revisar = actividadService.crear(empresaId, adminId, laneId, "Inspect parcel", null,
                TipoActividad.USUARIO, 260, 800).id();
        Long archivar = actividadService.crear(empresaId, adminId, laneId, "File parcel", null,
                TipoActividad.USUARIO, 420, 800).id();
        Long arcoId = arcoService.crear(empresaId, adminId, DatosDeArco.entre(recibir, revisar)).id();

        pedir(put("/api/v1/arcos/{id}", arcoId), Map.of("destinoId", archivar, "version", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinoId").value(archivar))
                .andExpect(jsonPath("$.origenId").value(recibir));
        // El tramo nuevo pasa por las mismas reglas: un nodo no se conecta consigo mismo.
        pedir(put("/api/v1/arcos/{id}", arcoId), Map.of("origenId", archivar, "version", 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Un arco no puede tener el mismo nodo como origen y destino."));
    }

    @Test
    @DisplayName("R-41: un nodo se arrastra a otra lane, y con arcos no se sale de su pool")
    void nodo_seMueveDeLaneYNoCruzaDePoolConArcos() throws Exception {
        Long rolId = rolProcesoService.crear(empresaId, "Dispatch", null).id();
        Long otraLane = laneService.crear(empresaId, adminId, tiendaId, "Shipping", rolId).id();
        Long externo = poolService.crear(empresaId, adminId, procesoId, "Courier", TipoParticipante.PROVEEDOR,
                false, Integracion.TRANSPORTE).id();
        Long laneExterna = laneService.crear(empresaId, adminId, externo, "Dispatch", rolId).id();
        Long empacar = actividadService.crear(empresaId, adminId, laneId, "Pack refund", null,
                TipoActividad.USUARIO, 100, 700).id();
        Long despachar = actividadService.crear(empresaId, adminId, laneId, "Dispatch refund", null,
                TipoActividad.USUARIO, 260, 700).id();

        // Suelta, la actividad se muda a donde sea, incluso a otro participante del mismo proceso.
        pedir(put("/api/v1/actividades/{id}", empacar), Map.of("nombre", "Pack refund", "laneId", laneExterna,
                "posicionX", 20, "posicionY", 30, "version", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.laneId").value(laneExterna))
                .andExpect(jsonPath("$.posicionX").value(20));
        pedir(put("/api/v1/actividades/{id}", empacar), Map.of("nombre", "Pack refund", "laneId", otraLane,
                "posicionX", 20, "posicionY", 30, "version", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.laneId").value(otraLane));

        // Con un arco, ya solo se mueve dentro de su pool.
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(empacar, despachar));
        pedir(put("/api/v1/actividades/{id}", empacar), Map.of("nombre", "Pack refund", "laneId", laneExterna,
                "posicionX", 20, "posicionY", 30, "version", 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("El nodo tiene arcos y solo puede moverse a una lane del mismo pool."));
        pedir(put("/api/v1/actividades/{id}", empacar), Map.of("nombre", "Pack refund", "laneId", laneId,
                "posicionX", 20, "posicionY", 30, "version", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.laneId").value(laneId));
    }

    @Test
    @DisplayName("R-35: la salida por defecto de un gateway es una sola y no lleva condicion")
    void arco_salidaPorDefecto_esUnicaYSinCondicion() throws Exception {
        Long decidir = gatewayService.crear(empresaId, adminId, laneId, "Discount?", TipoGateway.EXCLUSIVO, 100, 560)
                .id();
        Long conDescuento = actividadService.crear(empresaId, adminId, laneId, "Apply discount", null,
                TipoActividad.USUARIO, 260, 520).id();
        Long sinDescuento = actividadService.crear(empresaId, adminId, laneId, "Charge full price", null,
                TipoActividad.USUARIO, 260, 600).id();
        Long revisar = actividadService.crear(empresaId, adminId, laneId, "Review price", null, TipoActividad.USUARIO,
                420, 600).id();

        pedir(post("/api/v1/arcos"), Map.of("origenId", decidir, "destinoId", conDescuento,
                "condicion", "order.total > 100"))
                .andExpect(status().isCreated());
        // La salida por defecto es la que se toma cuando ninguna condicion se cumple, asi que no lleva una.
        pedir(post("/api/v1/arcos"), Map.of("origenId", decidir, "destinoId", sinDescuento,
                "porDefecto", true, "condicion", "order.total <= 100"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La salida por defecto no lleva condicion."));
        pedir(post("/api/v1/arcos"), Map.of("origenId", decidir, "destinoId", sinDescuento, "porDefecto", true,
                "orden", 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.porDefecto").value(true))
                .andExpect(jsonPath("$.orden").value(1));
        pedir(post("/api/v1/arcos"), Map.of("origenId", decidir, "destinoId", revisar, "porDefecto", true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Un gateway solo puede tener una salida por defecto."));
        // R-36: el gateway se sigue editando aunque una de sus salidas no lleve condicion, porque es la por defecto.
        pedir(put("/api/v1/gateways/{id}", decidir), Map.of("nombre", "Discount?", "tipoGateway", "EXCLUSIVO",
                "posicionX", 100, "posicionY", 580, "version", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        // R-34: un paralelo sigue todas sus salidas, asi que las condiciones y la salida por defecto se retiran.
        pedir(put("/api/v1/gateways/{id}", decidir), Map.of("nombre", "Discount?", "tipoGateway", "PARALELO",
                "posicionX", 100, "posicionY", 560, "version", 1))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/pools/{id}/arcos", tiendaId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.origenId == " + decidir + ")].condicion").value(everyItem(nullValue())))
                .andExpect(jsonPath("$[?(@.origenId == " + decidir + ")].porDefecto")
                        .value(everyItem(is(false))));
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
        Long haciaA = arcoService.crear(empresaId, adminId, DatosDeArco.entre(repartir, cajaA)).id();
        arcoService.crear(empresaId, adminId, new DatosDeArco(repartir, cajaB, null, "order.hasBoxB", false, 0));

        pedir(put("/api/v1/gateways/{id}", repartir), Map.of("nombre", "Ship boxes", "tipoGateway", "INCLUSIVO",
                "posicionX", 100, "posicionY", 400, "version", 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Todos los arcos que salen de un gateway exclusivo o "
                        + "inclusivo requieren condicion o ser la salida por defecto."));
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
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(intermedio, pagar));

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
