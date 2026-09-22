package com.facimus.procesos.modelado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.PoolService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Trazabilidad del modelado: cada cambio del diagrama queda en el historial del proceso, con quien lo hizo. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HistorialModeladoIntegracionTest {

    private static final String ADMIN = "admin@historial.com";
    private static final String EDITORA = "editora@historial.com";
    private static final String CLAVE = "clave12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

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
    private ArcoService arcoService;

    private Long empresaId;
    private Long adminId;
    private String tokenEditora;

    @BeforeAll
    void registrarTienda() throws Exception {
        empresaId = empresaService.registrar("Tienda de historial", "900131313-4", "contacto@historial.com",
                "Administradora", ADMIN, CLAVE).id();
        adminId = usuarioRepository.findByEmail(ADMIN).orElseThrow().getId();
        usuarioService.crearColaborador(empresaId, "Editora", EDITORA, CLAVE, RolAcceso.EDITOR);
        tokenEditora = jsonMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(EDITORA, CLAVE))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("accessToken").asString();
    }

    @Test
    @DisplayName("Cada cambio del modelado queda en el historial del proceso, en orden y con su autor")
    void cambiosDelModelado_quedanEnElHistorialConSuAutor() throws Exception {
        Long proceso = procesoService.crear(empresaId, adminId, "Order fulfillment", "Checkout to delivery",
                "Fulfillment").id();
        Long tienda = poolService.listarPorProceso(empresaId, proceso).getFirst().id();
        Long rol = rolProcesoService.crear(empresaId, "Warehouse", null).id();

        Long cliente = id(post("/api/v1/procesos/{id}/pools", proceso),
                Map.of("nombre", "Customer", "tipoParticipante", "CLIENTE", "cajaNegra", true));
        Long lane = id(post("/api/v1/pools/{id}/lanes", tienda), Map.of("nombre", "Warehouse", "rolProcesoId", rol));
        Long recoger = id(post("/api/v1/lanes/{id}/actividades", lane),
                Map.of("nombre", "Pick items", "posicionX", 100, "posicionY", 80));
        Long empacar = id(post("/api/v1/lanes/{id}/actividades", lane),
                Map.of("nombre", "Pack items", "posicionX", 260, "posicionY", 80));
        id(post("/api/v1/lanes/{id}/gateways", lane),
                Map.of("nombre", "Split", "tipoGateway", "PARALELO", "posicionX", 420, "posicionY", 80));
        Long flujo = id(post("/api/v1/arcos"), Map.of("origenId", recoger, "destinoId", empacar));
        Long pedido = id(post("/api/v1/procesos/{id}/mensajes", proceso), Map.of("nombre", "Order placed",
                "contenido", "Cart and address", "poolOrigenId", cliente, "poolDestinoId", tienda));
        responder(put("/api/v1/mensajes/{id}/correlacion", pedido), Map.of("criterio", "orderId"));
        responder(put("/api/v1/actividades/{id}", recoger), Map.of("nombre", "Pick and scan items",
                "posicionX", 100, "posicionY", 80, "version", 0));
        // Los borrados son del administrador
        arcoService.eliminar(empresaId, adminId, flujo);

        JsonNode historial = responder(get("/api/v1/procesos/{id}/historial", proceso), null);

        List<JsonNode> cronologico = historial.valueStream().toList().reversed();
        assertThat(cronologico).extracting(cambio -> cambio.get("descripcionCambio").asString(),
                        cambio -> cambio.get("autorNombre").asString())
                .containsExactly(
                        tuple("Proceso creado.", "Administradora"),
                        tuple("Pool \"Customer\" agregado.", "Editora"),
                        tuple("Lane \"Warehouse\" agregada al pool \"Tienda de historial\".", "Editora"),
                        tuple("Actividad \"Pick items\" agregada.", "Editora"),
                        tuple("Actividad \"Pack items\" agregada.", "Editora"),
                        tuple("Gateway \"Split\" agregado.", "Editora"),
                        tuple("Flujo de \"Pick items\" a \"Pack items\" agregado.", "Editora"),
                        tuple("Mensaje \"Order placed\" agregado.", "Editora"),
                        tuple("Clave de correlación \"orderId\" definida para el mensaje \"Order placed\".", "Editora"),
                        tuple("Actividad \"Pick and scan items\" editada.", "Editora"),
                        tuple("Flujo de \"Pick and scan items\" a \"Pack items\" eliminado.", "Administradora"));
    }

    private Long id(MockHttpServletRequestBuilder peticion, Map<String, Object> cuerpo) throws Exception {
        return jsonMapper.readTree(mockMvc.perform(conCuerpo(peticion, cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode responder(MockHttpServletRequestBuilder peticion, Map<String, Object> cuerpo) throws Exception {
        return jsonMapper.readTree(mockMvc.perform(conCuerpo(peticion, cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder conCuerpo(MockHttpServletRequestBuilder peticion, Map<String, Object> cuerpo)
            throws Exception {
        peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenEditora);
        return cuerpo == null ? peticion
                : peticion.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(cuerpo));
    }
}
