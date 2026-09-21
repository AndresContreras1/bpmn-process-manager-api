package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.controller.dto.CambiarEstadoProcesoRequest;
import com.facimus.procesos.gestion.controller.dto.ActualizarUsuarioRequest;
import com.facimus.procesos.gestion.controller.dto.EditarProcesoRequest;
import com.facimus.procesos.gestion.controller.dto.LoginRequest;
import com.facimus.procesos.gestion.controller.dto.RolProcesoRequest;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.controller.dto.ActividadRequest;
import com.facimus.procesos.modelado.controller.dto.ArcoRequest;
import com.facimus.procesos.modelado.controller.dto.CorrelacionRequest;
import com.facimus.procesos.modelado.controller.dto.EditarArcoRequest;
import com.facimus.procesos.modelado.controller.dto.EditarMensajeRequest;
import com.facimus.procesos.modelado.controller.dto.EditarPoolRequest;
import com.facimus.procesos.modelado.controller.dto.GatewayRequest;
import com.facimus.procesos.modelado.controller.dto.LaneRequest;
import com.facimus.procesos.modelado.controller.dto.MensajeRequest;
import com.facimus.procesos.modelado.controller.dto.PoolRequest;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.CorrelacionService;
import com.facimus.procesos.modelado.service.GatewayService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.MensajeService;
import com.facimus.procesos.modelado.service.PoolService;

import tools.jackson.databind.json.JsonMapper;

/** Aislamiento entre empresas: con el token de la empresa A no se alcanza nada de la empresa B (README §10 y §11). */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:aislamiento-it;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AislamientoEmpresasIntegracionTest {

    private static final String ADMIN_A = "admin@empresa-a.com";
    private static final String AUDITOR_A = "auditor@empresa-a.com";
    private static final String ADMIN_B = "admin@empresa-b.com";
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
    private MensajeService mensajeService;

    @Autowired
    private CorrelacionService correlacionService;

    private String tokenA;
    private String tokenB;

    private Long empresaA;
    private Long procesoA;
    private Long poolA;
    private Long rolA;
    private Long laneA;
    private Long gatewayA;

    private Long empresaB;
    private Long adminB;
    private Long procesoB;
    private Long poolB;
    private Long rolB;
    private Long laneB;
    private Long actividadB;
    private Long gatewayB;
    private Long gatewayCierreB;
    private Long arcoB;
    private Long mensajeB;

    @BeforeAll
    void crearDosEmpresas() throws Exception {
        empresaA = empresaService
                .registrar("Empresa A", "900100200", "contacto@empresa-a.com", "Admin A", ADMIN_A, CLAVE).getId();
        Long adminA = usuarioService.autenticar(ADMIN_A, CLAVE).getId();
        procesoA = procesoService.crear(empresaA, adminA, "Ventas", "Proceso de ventas", "Comercial").getId();
        poolA = poolService.listarPorProceso(empresaA, procesoA).getFirst().getId();
        rolA = rolProcesoService.crear(empresaA, "Vendedor", "Atiende a los clientes").getId();
        laneA = laneService.crear(empresaA, poolA, "Ventas", rolA).getId();
        gatewayA = gatewayService.crear(empresaA, laneA, "Revisar venta", TipoGateway.PARALELO, 100, 100).getId();
        // Quien ataca tiene un usuarioId distinto del empresaId de su empresa: si un controller
        // confundiera los dos ids, estas pruebas lo notarian.
        Long auditorA = usuarioService
                .crearColaborador(empresaA, "Auditor A", AUDITOR_A, CLAVE, RolAcceso.ADMINISTRADOR).getId();
        assertThat(auditorA).isNotEqualTo(empresaA);

        empresaB = empresaService
                .registrar("Empresa B", "900300400", "contacto@empresa-b.com", "Admin B", ADMIN_B, CLAVE).getId();
        adminB = usuarioService.autenticar(ADMIN_B, CLAVE).getId();
        procesoB = procesoService.crear(empresaB, adminB, "Compras", "Proceso de compras", "Logistica").getId();
        poolB = poolService.listarPorProceso(empresaB, procesoB).getFirst().getId();
        Long poolProveedorB = poolService
                .crear(empresaB, procesoB, "Proveedor", TipoParticipante.PROVEEDOR, true).getId();
        rolB = rolProcesoService.crear(empresaB, "Comprador", "Gestiona las compras").getId();
        laneB = laneService.crear(empresaB, poolB, "Compras", rolB).getId();
        actividadB = actividadService.crear(empresaB, laneB, "Solicitar cotizacion", "Pide precios", 100, 300).getId();
        gatewayB =gatewayService.crear(empresaB, laneB, "Aprobar compra", TipoGateway.PARALELO, 100, 100).getId();
        gatewayCierreB = gatewayService
                .crear(empresaB, laneB, "Cerrar compra", TipoGateway.PARALELO, 300, 100).getId();
        arcoB = arcoService.crear(empresaB, gatewayB, gatewayCierreB, "Continuar", null).getId();
        mensajeB = mensajeService.crear(empresaB, procesoB, "Orden de compra", "Pedido", poolB, poolProveedorB).getId();
        correlacionService.definir(empresaB, mensajeB, "numeroPedido");

        tokenA = login(AUDITOR_A);
        tokenB = login(ADMIN_B);
    }

    Stream<Arguments> listadosPorRecursoPadre() {
        return Stream.of(
                Arguments.of("/api/v1/procesos/{id}/pools", procesoB),
                Arguments.of("/api/v1/procesos/{id}/mensajes", procesoB),
                Arguments.of("/api/v1/pools/{id}/lanes", poolB),
                Arguments.of("/api/v1/lanes/{id}/actividades", laneB),
                Arguments.of("/api/v1/lanes/{id}/gateways", laneB),
                Arguments.of("/api/v1/pools/{id}/arcos", poolB));
    }

    @ParameterizedTest(name = "GET {0}")
    @MethodSource("listadosPorRecursoPadre")
    void Aislamiento_listarDesdeRecursoPadreDeOtraEmpresa_devuelve404(String ruta, Long idDeLaEmpresaB)
            throws Exception {
        // La empresa B si ve su listado: el 404 de la empresa A no se debe a una ruta o un id equivocados.
        mockMvc.perform(get(ruta, idDeLaEmpresaB).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isNotEmpty());

        mockMvc.perform(get(ruta, idDeLaEmpresaB).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    // README §11: cada recurso de la empresa B pedido por su id con el token de la empresa A.
    Stream<Arguments> recursosDeLaEmpresaBPorId() {
        ActividadRequest actividad = new ActividadRequest("Intrusa", "Desde la empresa A", 0, 0);
        GatewayRequest gateway = new GatewayRequest("Intruso", TipoGateway.PARALELO, 0, 0);
        LaneRequest lane = new LaneRequest("Intrusa", rolA);
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/v1/usuarios/{id}", adminB, null, "Usuario no encontrado"),
                Arguments.of(HttpMethod.PATCH, "/api/v1/usuarios/{id}", adminB,
                        new ActualizarUsuarioRequest(RolAcceso.SOLO_LECTURA, null), "Usuario no encontrado"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/usuarios/{id}", adminB, null, "Usuario no encontrado"),
                Arguments.of(HttpMethod.GET, "/api/v1/procesos/{id}", procesoB, null, "Proceso no encontrado"),
                Arguments.of(HttpMethod.PUT, "/api/v1/procesos/{id}", procesoB,
                        new EditarProcesoRequest("Intruso", "Desde la empresa A", "Otra"),
                        "Proceso no encontrado"),
                Arguments.of(HttpMethod.PATCH, "/api/v1/procesos/{id}", procesoB,
                        new CambiarEstadoProcesoRequest(EstadoProceso.PUBLICADO), "Proceso no encontrado"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/procesos/{id}", procesoB, null, "Proceso no encontrado"),
                Arguments.of(HttpMethod.GET, "/api/v1/roles/{id}", rolB, null, "Rol de proceso no encontrado"),
                Arguments.of(HttpMethod.PUT, "/api/v1/roles/{id}", rolB,
                        new RolProcesoRequest("Intruso", "Desde la empresa A"), "Rol de proceso no encontrado"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/roles/{id}", rolB, null, "Rol de proceso no encontrado"),
                Arguments.of(HttpMethod.POST, "/api/v1/procesos/{id}/pools", procesoB,
                        new PoolRequest("Intruso", TipoParticipante.CLIENTE, false), "Proceso no encontrado"),
                Arguments.of(HttpMethod.GET, "/api/v1/pools/{id}", poolB, null, "Pool no encontrado"),
                Arguments.of(HttpMethod.PUT, "/api/v1/pools/{id}", poolB,
                        new EditarPoolRequest("Intruso", TipoParticipante.CLIENTE), "Pool no encontrado"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/pools/{id}", poolB, null, "Pool no encontrado"),
                Arguments.of(HttpMethod.POST, "/api/v1/pools/{id}/lanes", poolB, lane, "Pool no encontrado"),
                Arguments.of(HttpMethod.GET, "/api/v1/lanes/{id}", laneB, null, "Lane no encontrada"),
                Arguments.of(HttpMethod.PUT, "/api/v1/lanes/{id}", laneB, lane, "Lane no encontrada"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/lanes/{id}", laneB, null, "Lane no encontrada"),
                Arguments.of(HttpMethod.POST, "/api/v1/lanes/{id}/actividades", laneB, actividad, "Lane no encontrada"),
                Arguments.of(HttpMethod.GET, "/api/v1/actividades/{id}", actividadB, null, "Actividad no encontrada"),
                Arguments.of(HttpMethod.PUT, "/api/v1/actividades/{id}", actividadB, actividad,
                        "Actividad no encontrada"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/actividades/{id}", actividadB, null, "Actividad no encontrada"),
                Arguments.of(HttpMethod.POST, "/api/v1/lanes/{id}/gateways", laneB, gateway, "Lane no encontrada"),
                Arguments.of(HttpMethod.GET, "/api/v1/gateways/{id}", gatewayB, null, "Gateway no encontrado"),
                Arguments.of(HttpMethod.PUT, "/api/v1/gateways/{id}", gatewayB, gateway, "Gateway no encontrado"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/gateways/{id}", gatewayB, null, "Gateway no encontrado"),
                Arguments.of(HttpMethod.GET, "/api/v1/arcos/{id}", arcoB, null, "Arco no encontrado"),
                Arguments.of(HttpMethod.PUT, "/api/v1/arcos/{id}", arcoB, new EditarArcoRequest("Intruso", null),
                        "Arco no encontrado"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/arcos/{id}", arcoB, null, "Arco no encontrado"),
                Arguments.of(HttpMethod.POST, "/api/v1/procesos/{id}/mensajes", procesoB,
                        new MensajeRequest("Intruso", "Desde la empresa A", poolA, poolB), "Proceso no encontrado"),
                Arguments.of(HttpMethod.GET, "/api/v1/mensajes/{id}", mensajeB, null, "Mensaje no encontrado"),
                Arguments.of(HttpMethod.PUT, "/api/v1/mensajes/{id}", mensajeB,
                        new EditarMensajeRequest("Intruso", "Desde la empresa A"), "Mensaje no encontrado"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/mensajes/{id}", mensajeB, null, "Mensaje no encontrado"),
                Arguments.of(HttpMethod.GET, "/api/v1/mensajes/{id}/correlacion", mensajeB, null,
                        "no tiene correlacion"),
                Arguments.of(HttpMethod.PUT, "/api/v1/mensajes/{id}/correlacion", mensajeB,
                        new CorrelacionRequest("Intruso"), "Mensaje no encontrado"));
    }

    @ParameterizedTest(name = "{0} {1} -> 404 {4}")
    @MethodSource("recursosDeLaEmpresaBPorId")
    void Aislamiento_recursoDeOtraEmpresaPorId_devuelve404SinCambiarlo(HttpMethod metodo, String ruta, Long id,
            Object cuerpo, String mensaje) throws Exception {
        List<Object> empresaBAntes = estadoEmpresaB();

        pedirComoEmpresaA(metodo, ruta, id, cuerpo, mensaje);

        assertThat(estadoEmpresaB()).isEqualTo(empresaBAntes);
    }

    // Recursos propios de la empresa A que referencian por id algo de la empresa B.
    Stream<Arguments> relacionesConRecursosDeLaEmpresaB() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/v1/pools/{id}/lanes", poolA, new LaneRequest("Mixta", rolB),
                        "Rol de proceso no encontrado"),
                Arguments.of(HttpMethod.PUT, "/api/v1/lanes/{id}", laneA, new LaneRequest("Mixta", rolB),
                        "Rol de proceso no encontrado"),
                Arguments.of(HttpMethod.POST, "/api/v1/arcos", null, new ArcoRequest(gatewayA, gatewayB, null, null),
                        "Nodo de destino no encontrado"),
                Arguments.of(HttpMethod.POST, "/api/v1/procesos/{id}/mensajes", procesoA,
                        new MensajeRequest("Mixto", "Hacia la empresa B", poolA, poolB),
                        "Pool de destino no encontrado"));
    }

    @ParameterizedTest(name = "{0} {1} -> 404 {4}")
    @MethodSource("relacionesConRecursosDeLaEmpresaB")
    void Aislamiento_relacionConRecursoDeOtraEmpresa_devuelve404SinCambiarla(HttpMethod metodo, String ruta,
            Long id, Object cuerpo, String mensaje) throws Exception {
        List<Object> empresaBAntes = estadoEmpresaB();

        pedirComoEmpresaA(metodo, ruta, id, cuerpo, mensaje);

        assertThat(estadoEmpresaB()).isEqualTo(empresaBAntes);
    }

    @Test
    @DisplayName("Un empresaId de otra empresa en el cuerpo o en la URL se ignora: manda el token")
    void Aislamiento_empresaIdEnElRequest_seIgnora() throws Exception {
        Map<String, Object> cuerpo = Map.of("nombre", "Devoluciones", "descripcion", "Proceso de devoluciones",
                "categoria", "Comercial", "empresaId", empresaB);

        String respuesta = mockMvc.perform(post("/api/v1/procesos?empresaId={id}", empresaB)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long procesoCreado = jsonMapper.readTree(respuesta).get("id").asLong();

        assertThat(procesoService.obtener(empresaA, procesoCreado).getNombre()).isEqualTo("Devoluciones");
        assertThatThrownBy(() -> procesoService.obtener(empresaB, procesoCreado))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Los listados de la empresa A solo muestran lo de la empresa A")
    void Aislamiento_listadosGenerales_soloMuestranLaEmpresaDelToken() throws Exception {
        assertThat(valoresComoEmpresaA("/api/v1/procesos", "nombre")).contains("Ventas").doesNotContain("Compras");
        assertThat(valoresComoEmpresaA("/api/v1/roles", "nombre")).contains("Vendedor").doesNotContain("Comprador");
        assertThat(valoresComoEmpresaA("/api/v1/usuarios", "email")).contains(ADMIN_A).doesNotContain(ADMIN_B);
    }

    private void pedirComoEmpresaA(HttpMethod metodo, String ruta, Long id, Object cuerpo, String mensaje)
            throws Exception {
        MockHttpServletRequestBuilder peticion = request(metodo, ruta, id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA);
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(cuerpo));
        }
        // El mensaje confirma que el 404 lo dio el service al no encontrar el recurso en la empresa A,
        // y no una ruta mal escrita.
        mockMvc.perform(peticion)
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString(mensaje)));
    }

    // Lo que la empresa B tiene, leido con su propia empresa: si una peticion de la empresa A cambia algo, esta
    // lista cambia.
    private List<Object> estadoEmpresaB() {
        Proceso proceso = procesoService.obtener(empresaB, procesoB);
        RolProceso rol = rolProcesoService.obtener(empresaB, rolB);
        Usuario admin = usuarioService.obtener(empresaB, adminB);
        return List.of(
                proceso.getNombre(), proceso.getEstado(), proceso.isActivo(),
                rol.getNombre(), rol.isActivo(),
                admin.getRolAcceso(), admin.isActivo(),
                poolService.listarPorProceso(empresaB, procesoB).stream().map(Pool::getNombre).toList(),
                laneService.listarPorPool(empresaB, poolB).stream().map(Lane::getNombre).toList(),
                actividadService.obtener(empresaB, actividadB).getNombre(),
                gatewayService.obtener(empresaB, gatewayB).getNombre(),
                gatewayService.obtener(empresaB, gatewayCierreB).getNombre(),
                arcoService.obtener(empresaB, arcoB).getEtiqueta(),
                mensajeService.listarPorProceso(empresaB, procesoB).stream().map(Mensaje::getNombre).toList(),
                correlacionService.obtener(empresaB, mensajeB).getCriterio());
    }

    private List<String> valoresComoEmpresaA(String ruta, String campo) throws Exception {
        String respuesta = mockMvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(respuesta).findValues(campo).stream().map(valor -> valor.asString()).toList();
    }

    private String login(String email) throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, CLAVE))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(respuesta).get("accessToken").asString();
    }
}
