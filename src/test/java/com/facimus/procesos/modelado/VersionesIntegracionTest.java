package com.facimus.procesos.modelado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.function.Function;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.repository.PoolRepository;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.DatosDeArco;
import com.facimus.procesos.modelado.service.CorrelacionService;
import com.facimus.procesos.modelado.service.DatosDeMensaje;
import com.facimus.procesos.modelado.service.GatewayService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.MensajeService;
import com.facimus.procesos.modelado.service.PoolService;

import tools.jackson.databind.json.JsonMapper;

/**
 * Bloqueo optimista de punta a punta: todo PUT y PATCH exige la version que se leyo, la sube al guardar y responde
 * 409 si otra persona guardo antes, sin tocar el recurso.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VersionesIntegracionTest {

    private static final String ADMIN = "admin@versiones.com";
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
    private PoolRepository poolRepository;

    @Autowired
    private LaneService laneService;

    @Autowired
    private ActividadService actividadService;

    @Autowired
    private GatewayService gatewayService;

    @Autowired
    private ArcoService arcoService;

    @Autowired
    private MensajeService mensajeService;

    @Autowired
    private CorrelacionService correlacionService;

    private String token;
    private Long empresaId;
    private Long adminId;
    private Long procesoId;
    private Long rolId;
    private Long colaboradorId;
    private Long poolId;
    private Long laneId;
    private Long actividadId;
    private Long gatewayId;
    private Long arcoId;
    private Long mensajeId;
    private Long mensajeSinClaveId;

    @BeforeAll
    void modelarUnProceso() throws Exception {
        empresaId = empresaService.registrar("Tienda de versiones", "900888999-0", "contacto@versiones.com",
                "Administradora", ADMIN, CLAVE).id();
        adminId = usuarioRepository.findByEmail(ADMIN).orElseThrow().getId();
        colaboradorId = usuarioService.crearColaborador(empresaId, "Editora", "editora@versiones.com", CLAVE,
                RolAcceso.EDITOR).id();

        procesoId = procesoService.crear(empresaId, adminId, "Order fulfillment", "Checkout to delivery",
                "Fulfillment").id();
        rolId = rolProcesoService.crear(empresaId, "Warehouse", "Picks and packs").id();
        Long tienda = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        poolId = poolService.crear(empresaId, adminId, procesoId, "Customer", TipoParticipante.CLIENTE, true,
                Integracion.NINGUNA).id();
        laneId = laneService.crear(empresaId, adminId, tienda, "Warehouse", rolId).id();
        actividadId = actividadService.crear(empresaId, adminId, laneId, "Pick items", "From the shelves",
                TipoActividad.USUARIO, 100, 80).id();
        Long empacar = actividadService.crear(empresaId, adminId, laneId, "Pack items", "Into the box",
                TipoActividad.USUARIO, 260, 80).id();
        gatewayId = gatewayService.crear(empresaId, adminId, laneId, "Split", TipoGateway.PARALELO, 420, 80).id();
        arcoId = arcoService.crear(empresaId, adminId, DatosDeArco.entre(actividadId, empacar)).id();
        mensajeId = mensajeService.crear(empresaId, adminId, procesoId, DatosDeMensaje.basico("Order placed",
                "Cart and address", poolId,
                tienda))
                .id();
        mensajeSinClaveId = mensajeService.crear(empresaId, adminId, procesoId, DatosDeMensaje.basico("Order status",
                "Tracking number",
                tienda, poolId)).id();
        correlacionService.definir(empresaId, adminId, mensajeId, "orderId", null, null, null);

        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(ADMIN, CLAVE))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = jsonMapper.readTree(login).get("accessToken").asString();
    }

    /** Cada edicion: como se lee el recurso, como se edita y el cuerpo con la version que se manda. */
    Stream<Arguments> ediciones() {
        return Stream.of(
                // El detalle del proceso trae su historial: la version esta en "proceso"
                edicion("PUT proceso", HttpMethod.PUT, "/api/v1/procesos/{id}", "/api/v1/procesos/{id}", procesoId,
                        v -> Map.of("nombre", "Order fulfillment", "descripcion", "Checkout to delivery, v2",
                                "categoria", "Fulfillment", "version", v)),
                edicion("PATCH proceso", HttpMethod.PATCH, "/api/v1/procesos/{id}", "/api/v1/procesos/{id}",
                        procesoId, v -> Map.of("estado", "PUBLICADO", "version", v)),
                edicion("PUT rol", HttpMethod.PUT, "/api/v1/roles/{id}", "/api/v1/roles/{id}", rolId,
                        v -> Map.of("nombre", "Warehouse", "descripcion", "Picks, packs and ships", "version", v)),
                edicion("PATCH usuario", HttpMethod.PATCH, "/api/v1/usuarios/{id}", "/api/v1/usuarios/{id}",
                        colaboradorId, v -> Map.of("rolAcceso", "SOLO_LECTURA", "version", v)),
                edicion("PUT pool", HttpMethod.PUT, "/api/v1/pools/{id}", "/api/v1/pools/{id}", poolId,
                        v -> Map.of("nombre", "Shopper", "tipoParticipante", "CLIENTE", "version", v)),
                edicion("PUT lane", HttpMethod.PUT, "/api/v1/lanes/{id}", "/api/v1/lanes/{id}", laneId,
                        v -> Map.of("nombre", "Warehouse floor", "rolProcesoId", rolId, "version", v)),
                edicion("PUT actividad", HttpMethod.PUT, "/api/v1/actividades/{id}", "/api/v1/actividades/{id}",
                        actividadId, v -> Map.of("nombre", "Pick items", "descripcion", "From the bins",
                                "posicionX", 120, "posicionY", 90, "version", v)),
                edicion("PUT gateway", HttpMethod.PUT, "/api/v1/gateways/{id}", "/api/v1/gateways/{id}", gatewayId,
                        v -> Map.of("nombre", "Split", "tipoGateway", "PARALELO", "posicionX", 430,
                                "posicionY", 90, "version", v)),
                edicion("PUT arco", HttpMethod.PUT, "/api/v1/arcos/{id}", "/api/v1/arcos/{id}", arcoId,
                        v -> Map.of("etiqueta", "Next", "version", v)),
                edicion("PUT mensaje", HttpMethod.PUT, "/api/v1/mensajes/{id}", "/api/v1/mensajes/{id}", mensajeId,
                        v -> Map.of("nombre", "Order placed", "contenido", "Cart, address and payment", "version", v)),
                edicion("PUT correlacion", HttpMethod.PUT, "/api/v1/mensajes/{id}/correlacion",
                        "/api/v1/mensajes/{id}/correlacion", mensajeId,
                        v -> Map.of("criterio", "orderNumber", "version", v)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ediciones")
    @DisplayName("Con la version leida la edicion sube la version; repetida con la vieja responde 409 y no cambia nada")
    void edicion_conLaVersionLeida_guardaYConUnaViejaResponde409(String caso, HttpMethod metodo, String ruta,
            String rutaDeLectura, Long id, Function<Long, Map<String, Object>> cuerpo) throws Exception {
        long leida = jsonMapper.readTree(leer(rutaDeLectura, id)).findValue("version").asLong();

        editar(metodo, ruta, id, cuerpo.apply(leida))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(leida + 1));
        String guardado = leer(rutaDeLectura, id);

        editar(metodo, ruta, id, cuerpo.apply(leida))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflicto de versión"))
                .andExpect(jsonPath("$.detail").value("Otra persona guardó un cambio después de que leíste este "
                        + "recurso: enviaste la versión " + leida + " y la actual es la " + (leida + 1)
                        + ". Recarga y vuelve a intentar."));
        assertThat(leer(rutaDeLectura, id)).isEqualTo(guardado);
    }

    @Test
    @DisplayName("Una edicion sin version responde 400 y dice que falta el campo")
    void edicion_sinVersion_devuelve400ConElCampo() throws Exception {
        editar(HttpMethod.PUT, "/api/v1/pools/{id}", poolId, Map.of("nombre", "Shopper", "tipoParticipante", "CLIENTE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.version").value("La versión es obligatoria."));
    }

    @Test
    @DisplayName("La primera clave de un mensaje se crea sin version; reemplazarla sin la version leida responde 409")
    void correlacion_seCreaSinVersionYSeReemplazaConLaLeida() throws Exception {
        String ruta = "/api/v1/mensajes/{id}/correlacion";

        editar(HttpMethod.PUT, ruta, mensajeSinClaveId, Map.of("criterio", "orderId"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
        editar(HttpMethod.PUT, ruta, mensajeSinClaveId, Map.of("criterio", "trackingNumber"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Otra persona guardó un cambio después de que leíste este "
                        + "recurso: no enviaste la versión que leíste y la actual es la 0. "
                        + "Recarga y vuelve a intentar."));
        editar(HttpMethod.PUT, ruta, mensajeSinClaveId, Map.of("criterio", "trackingNumber", "version", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criterio").value("trackingNumber"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("Si dos ediciones de la misma version pasan la comprobacion a la vez, la base rechaza la segunda")
    void copiaVieja_alGuardarLaRechazaLaBase() {
        Pool copiaVieja = poolRepository.findByIdAndEmpresaId(poolId, empresaId).orElseThrow();
        poolService.editar(empresaId, adminId, poolId, "Buyer", TipoParticipante.CLIENTE, false, Integracion.NINGUNA,
                copiaVieja.getVersion());

        copiaVieja.setNombre("Guest");

        assertThatThrownBy(() -> poolRepository.saveAndFlush(copiaVieja))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(poolRepository.findByIdAndEmpresaId(poolId, empresaId).orElseThrow().getNombre())
                .isEqualTo("Buyer");
    }

    private static Arguments edicion(String caso, HttpMethod metodo, String ruta, String rutaDeLectura, Long id,
            Function<Long, Map<String, Object>> cuerpo) {
        return Arguments.of(caso, metodo, ruta, rutaDeLectura, id, cuerpo);
    }

    private String leer(String ruta, Long id) throws Exception {
        return mockMvc.perform(get(ruta, id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions editar(HttpMethod metodo, String ruta, Long id, Map<String, Object> cuerpo)
            throws Exception {
        return mockMvc.perform(request(metodo, ruta, id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(cuerpo)));
    }
}
