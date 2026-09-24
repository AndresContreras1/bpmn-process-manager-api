package com.facimus.procesos.modelado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
import com.facimus.procesos.modelado.service.DatosDeMensaje;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.modelado.service.CorrelacionService;
import com.facimus.procesos.modelado.service.GatewayService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.MensajeService;
import com.facimus.procesos.modelado.service.PoolService;

import tools.jackson.databind.json.JsonMapper;

/** Baja logica del modelado: lo que se elimina deja de verse y de cambiarse, pero su fila sigue en la base. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BajaLogicaIntegracionTest {

    private static final String ADMIN = "admin@bajas.com";
    private static final String CLAVE = "clave12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Autowired
    private MensajeService mensajeService;

    @Autowired
    private CorrelacionService correlacionService;

    private String token;
    private Long empresaId;
    private Long adminId;
    private Long procesoId;
    private Long tiendaId;
    private Long rolId;
    private Long laneId;

    @BeforeAll
    void modelarUnProceso() throws Exception {
        empresaId = empresaService.registrar("Tienda de bajas", "900121212-3", "contacto@bajas.com", "Administradora",
                ADMIN, CLAVE).id();
        adminId = usuarioRepository.findByEmail(ADMIN).orElseThrow().getId();
        procesoId = procesoService.crear(empresaId, adminId, "Order fulfillment", "Checkout to delivery",
                "Fulfillment").id();
        tiendaId = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        rolId = rolProcesoService.crear(empresaId, "Warehouse", null).id();
        laneId = laneService.crear(empresaId, adminId, tiendaId, "Warehouse", rolId).id();
        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(ADMIN, CLAVE))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = jsonMapper.readTree(login).get("accessToken").asString();
    }

    @Test
    @DisplayName("Eliminar un pool con mensajes da de baja sus mensajes y sus claves de correlacion")
    void poolConMensajes_alEliminarse_daDeBajaSusMensajes() throws Exception {
        Long transportadora = poolService.crear(empresaId, adminId, procesoId, "Carrier", TipoParticipante.PROVEEDOR,
                true,Integracion.NINGUNA).id();
        Long pedido = mensajeService.crear(empresaId, adminId, procesoId, DatosDeMensaje.basico("Shipment request",
                "Package and address",
                tiendaId, transportadora)).id();
        Long confirmacion = mensajeService.crear(empresaId, adminId, procesoId,
                DatosDeMensaje.basico("Shipment confirmed", "Tracking number",
                transportadora, tiendaId)).id();
        correlacionService.definir(empresaId, adminId, pedido, "orderId", null, null, null);

        pedir(delete("/api/v1/pools/{id}", transportadora)).andExpect(status().isNoContent());

        pedir(get("/api/v1/mensajes/{id}", pedido)).andExpect(status().isNotFound());
        pedir(get("/api/v1/mensajes/{id}", confirmacion)).andExpect(status().isNotFound());
        pedir(get("/api/v1/mensajes/{id}/correlacion", pedido)).andExpect(status().isNotFound());
        assertThat(activo("mensajes", pedido)).isFalse();
        assertThat(activo("mensajes", confirmacion)).isFalse();
        assertThat(activo("pools", transportadora)).isFalse();
    }

    /** Un arco necesita dos nodos que no existan todavia, asi que se crean con el. */
    private Long arcoEntreDosActividadesNuevas() {
        Long pesar = actividadService.crear(empresaId, adminId, laneId, "Weigh package", null,
                TipoActividad.USUARIO, 300, 100).id();
        Long etiquetar = actividadService.crear(empresaId, adminId, laneId, "Print label", null,
                TipoActividad.USUARIO, 400, 100).id();
        return arcoService.crear(empresaId, adminId, DatosDeArco.entre(pesar, etiquetar)).id();
    }

    /** Cada elemento se crea en el momento, para que los casos no dependan unos de otros. */
    Stream<Arguments> elementos() {
        return Stream.of(
                elemento("pool", "pools", "/api/v1/pools/{id}", () -> poolService
                        .crear(empresaId, adminId, procesoId, "Supplier", TipoParticipante.PROVEEDOR,
                                true, Integracion.NINGUNA).id()),
                elemento("lane", "lanes", "/api/v1/lanes/{id}", () -> laneService
                        .crear(empresaId, adminId, tiendaId, "Returns desk", rolId).id()),
                elemento("actividad", "nodos_flujo", "/api/v1/actividades/{id}", () -> actividadService
                        .crear(empresaId, adminId, laneId, "Label package", null, TipoActividad.USUARIO,
                                100, 100).id()),
                elemento("gateway", "nodos_flujo", "/api/v1/gateways/{id}", () -> gatewayService
                        .crear(empresaId, adminId, laneId, "Fragile?", TipoGateway.PARALELO, 200, 100).id()),
                elemento("arco", "arcos", "/api/v1/arcos/{id}", this::arcoEntreDosActividadesNuevas),
                elemento("mensaje", "mensajes", "/api/v1/mensajes/{id}", () -> mensajeService.crear(empresaId, adminId,
                        procesoId, DatosDeMensaje.basico("Invoice", "Order total", tiendaId,
                                poolService.crear(empresaId, adminId, procesoId, "Accounting",
                                        TipoParticipante.SISTEMA_EXTERNO, true, Integracion.NINGUNA)
                                        .id())).id()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("elementos")
    @DisplayName("Un elemento eliminado responde 404, pero su fila sigue en la base, dada de baja")
    void elementoEliminado_noSeVePeroSigueEnLaBase(String elemento, String tabla, String ruta, Supplier<Long> crear)
            throws Exception {
        Long id = crear.get();

        pedir(delete(ruta, id)).andExpect(status().isNoContent());

        pedir(get(ruta, id)).andExpect(status().isNotFound());
        pedir(delete(ruta, id)).andExpect(status().isNotFound());
        assertThat(activo(tabla, id)).isFalse();
    }

    @Test
    @DisplayName("Un arco eliminado libera su par de nodos: se puede volver a trazar entre los mismos")
    void arcoEliminado_liberaSuParDeNodos() throws Exception {
        Long empacar = actividadService.crear(empresaId, adminId, laneId, "Pack items", null, TipoActividad.USUARIO,
                100, 300).id();
        Long enviar = actividadService.crear(empresaId, adminId, laneId, "Ship items", null, TipoActividad.USUARIO, 300,
                300).id();
        Map<String, Object> arco = Map.of("origenId", empacar, "destinoId", enviar);
        Long primero = jsonMapper.readTree(pedir(post("/api/v1/arcos"), arco)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        pedir(delete("/api/v1/arcos/{id}", primero)).andExpect(status().isNoContent());

        pedir(post("/api/v1/arcos"), arco).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("HU-06: eliminar un proceso da de baja todo su modelo, que ya no se ve ni acepta cambios")
    void procesoEliminado_retiraSuModelo() throws Exception {
        Long devoluciones = procesoService.crear(empresaId, adminId, "Returns", "Return to refund", "After-sales").id();
        Long tienda = poolService.listarPorProceso(empresaId, devoluciones).getFirst().id();
        Long cliente = poolService.crear(empresaId, adminId, devoluciones, "Customer", TipoParticipante.CLIENTE,
                true,Integracion.NINGUNA).id();
        Long mostrador = laneService.crear(empresaId, adminId, tienda, "Returns desk", rolId).id();
        Long recibir = actividadService.crear(empresaId, adminId, mostrador, "Receive item", null,
                TipoActividad.USUARIO, 100, 100).id();
        Long revisar = gatewayService.crear(empresaId, adminId, mostrador, "Damaged?", TipoGateway.PARALELO, 200,
                100).id();
        Long arco = arcoService.crear(empresaId, adminId, DatosDeArco.entre(recibir, revisar)).id();
        Long cerrado = eventoService.crear(empresaId, adminId, mostrador, "Return closed", TipoEvento.FIN,
                300, 100).id();
        Long solicitud = mensajeService.crear(empresaId, adminId, devoluciones, DatosDeMensaje.basico("Return request",
                "Order and reason",
                cliente, tienda)).id();
        correlacionService.definir(empresaId, adminId, solicitud, "orderId", null, null, null);

        pedir(delete("/api/v1/procesos/{id}", devoluciones)).andExpect(status().isNoContent());

        for (String ruta : List.of("/api/v1/pools/" + cliente, "/api/v1/lanes/" + mostrador,
                "/api/v1/actividades/" + recibir, "/api/v1/gateways/" + revisar, "/api/v1/arcos/" + arco,
                "/api/v1/eventos/" + cerrado,
                "/api/v1/mensajes/" + solicitud, "/api/v1/mensajes/" + solicitud + "/correlacion",
                "/api/v1/procesos/" + devoluciones + "/pools", "/api/v1/procesos/" + devoluciones + "/mensajes")) {
            pedir(get(ruta)).andExpect(status().isNotFound());
        }
        pedir(post("/api/v1/procesos/{id}/pools", devoluciones),
                Map.of("nombre", "Carrier", "tipoParticipante", "PROVEEDOR", "cajaNegra", true))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Proceso no encontrado."));
        pedir(post("/api/v1/procesos/{id}/mensajes", devoluciones), Map.of("nombre", "Refund notice",
                "contenido", "Amount", "poolOrigenId", tienda, "poolDestinoId", cliente))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Proceso no encontrado."));
        assertThat(List.of(activo("pools", tienda), activo("pools", cliente), activo("lanes", mostrador),
                activo("nodos_flujo", recibir), activo("nodos_flujo", revisar),
                activo("nodos_flujo", cerrado), activo("arcos", arco),
                activo("mensajes", solicitud))).containsOnly(false);
    }

    @Test
    @DisplayName("Una lane no acepta un rol de proceso eliminado")
    void lane_conRolEliminado_devuelve404() throws Exception {
        Long temporada = rolProcesoService.crear(empresaId, "Seasonal staff", null).id();
        rolProcesoService.eliminar(empresaId, temporada);

        pedir(post("/api/v1/pools/{id}/lanes", tiendaId), Map.of("nombre", "Holiday rush", "rolProcesoId", temporada))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Rol de proceso no encontrado."));
    }

    private static Arguments elemento(String elemento, String tabla, String ruta, Supplier<Long> crear) {
        return Arguments.of(elemento, tabla, ruta, crear);
    }

    private Boolean activo(String tabla, Long id) {
        return jdbcTemplate.queryForObject("select activo from " + tabla + " where id = ?", Boolean.class, id);
    }

    private ResultActions pedir(MockHttpServletRequestBuilder peticion)
            throws Exception {
        return mockMvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions pedir(MockHttpServletRequestBuilder peticion,
            Map<String, Object> cuerpo) throws Exception {
        return pedir(peticion.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(cuerpo)));
    }
}
