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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.dto.request.ActualizarUsuarioRequest;
import com.facimus.procesos.gestion.dto.request.CambiarEstadoProcesoRequest;
import com.facimus.procesos.gestion.dto.request.EditarProcesoRequest;
import com.facimus.procesos.gestion.dto.request.EditarRolProcesoRequest;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.dto.request.ProcesoRequest;
import com.facimus.procesos.gestion.dto.request.RolProcesoRequest;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.dto.response.RolProcesoVistaResponse;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.dto.request.ActividadRequest;
import com.facimus.procesos.modelado.dto.request.ArcoRequest;
import com.facimus.procesos.modelado.dto.request.CorrelacionRequest;
import com.facimus.procesos.modelado.dto.request.EditarActividadRequest;
import com.facimus.procesos.modelado.dto.request.EditarArcoRequest;
import com.facimus.procesos.modelado.dto.request.EditarEventoRequest;
import com.facimus.procesos.modelado.dto.request.EditarGatewayRequest;
import com.facimus.procesos.modelado.dto.request.EditarLaneRequest;
import com.facimus.procesos.modelado.dto.request.EditarMensajeRequest;
import com.facimus.procesos.modelado.dto.request.EditarPoolRequest;
import com.facimus.procesos.modelado.dto.request.EventoRequest;
import com.facimus.procesos.modelado.dto.request.GatewayRequest;
import com.facimus.procesos.modelado.dto.request.LaneRequest;
import com.facimus.procesos.modelado.dto.request.MensajeRequest;
import com.facimus.procesos.modelado.dto.request.PoolRequest;
import com.facimus.procesos.modelado.dto.response.LaneResponse;
import com.facimus.procesos.modelado.dto.response.MensajeResponse;
import com.facimus.procesos.modelado.dto.response.PoolResponse;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.CorrelacionService;
import com.facimus.procesos.modelado.service.DatosDeMensaje;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.modelado.service.GatewayService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.MensajeService;
import com.facimus.procesos.modelado.service.PoolService;

import tools.jackson.databind.json.JsonMapper;

/** Aislamiento entre empresas: con el token de la empresa A no se alcanza nada de la empresa B (README §10 y §11). */
@SpringBootTest
@ActiveProfiles("test")
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
    private EventoService eventoService;

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
    private Long poolClienteA;
    private Long eventoB;

    @BeforeAll
    void crearDosEmpresas() throws Exception {
        empresaA = empresaService
                .registrar("Empresa A", "900100200", "contacto@empresa-a.com", "Admin A", ADMIN_A, CLAVE).id();
        Long adminA = usuarioRepository.findByEmail(ADMIN_A).orElseThrow().getId();
        procesoA = procesoService.crear(empresaA, adminA, "Ventas", "Proceso de ventas", "Comercial").id();
        poolA = poolService.listarPorProceso(empresaA, procesoA).getFirst().id();
        rolA = rolProcesoService.crear(empresaA, "Vendedor", "Atiende a los clientes").id();
        laneA = laneService.crear(empresaA, adminA, poolA, "Ventas", rolA).id();
        gatewayA = gatewayService.crear(empresaA, adminA, laneA, "Revisar venta", TipoGateway.PARALELO, 100, 100).id();
        poolClienteA = poolService.crear(empresaA, adminA, procesoA, "Cliente", TipoParticipante.CLIENTE,
                true, Integracion.CLIENTE).id();
        // Quien ataca tiene un usuarioId distinto del empresaId de su empresa: si un controller
        // confundiera los dos ids, estas pruebas lo notarian.
        Long auditorA = usuarioService
                .crearColaborador(empresaA, "Auditor A", AUDITOR_A, CLAVE, RolAcceso.ADMINISTRADOR).id();
        assertThat(auditorA).isNotEqualTo(empresaA);

        empresaB = empresaService
                .registrar("Empresa B", "900300400", "contacto@empresa-b.com", "Admin B", ADMIN_B, CLAVE).id();
        adminB = usuarioRepository.findByEmail(ADMIN_B).orElseThrow().getId();
        procesoB = procesoService.crear(empresaB, adminB, "Compras", "Proceso de compras", "Logistica").id();
        poolB = poolService.listarPorProceso(empresaB, procesoB).getFirst().id();
        Long poolProveedorB = poolService.crear(empresaB, adminB, procesoB, "Proveedor",
                TipoParticipante.PROVEEDOR, true, Integracion.NINGUNA).id();
        rolB = rolProcesoService.crear(empresaB, "Comprador", "Gestiona las compras").id();
        laneB = laneService.crear(empresaB, adminB, poolB, "Compras", rolB).id();
        actividadB = actividadService.crear(empresaB, adminB, laneB, "Solicitar cotizacion", "Pide precios",
                TipoActividad.USUARIO, 100,
                300).id();
        gatewayB =gatewayService.crear(empresaB, adminB, laneB, "Aprobar compra", TipoGateway.PARALELO, 100, 100).id();
        gatewayCierreB = gatewayService
                .crear(empresaB, adminB, laneB, "Cerrar compra", TipoGateway.PARALELO, 300, 100).id();
        arcoB = arcoService.crear(empresaB, adminB, gatewayB, gatewayCierreB, "Continuar", null, false, 0).id();
        eventoB = eventoService.crear(empresaB, adminB, laneB, "Compra recibida", TipoEvento.INICIO,
                20, 300).id();
        mensajeB = mensajeService.crear(empresaB, adminB, procesoB, DatosDeMensaje.basico("Orden de compra", "Pedido",
                poolB,
                poolProveedorB)).id();
        correlacionService.definir(empresaB, adminB, mensajeB, "numeroPedido", null, null, null);

        tokenA = login(AUDITOR_A);
        tokenB = login(ADMIN_B);
    }

    Stream<Arguments> listadosPorRecursoPadre() {
        return Stream.of(
                Arguments.of("/api/v1/procesos/{id}/pools", procesoB),
                Arguments.of("/api/v1/procesos/{id}/diagrama", procesoB),
                Arguments.of("/api/v1/procesos/{id}/mensajes", procesoB),
                Arguments.of("/api/v1/pools/{id}/lanes", poolB),
                Arguments.of("/api/v1/lanes/{id}/actividades", laneB),
                Arguments.of("/api/v1/lanes/{id}/gateways", laneB),
                Arguments.of("/api/v1/lanes/{id}/eventos", laneB),
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
        ActividadRequest actividad = new ActividadRequest("Intrusa", "Desde la empresa A",
                TipoActividad.USUARIO, 0, 0);
        GatewayRequest gateway = new GatewayRequest("Intruso", TipoGateway.PARALELO, 0, 0);
        EventoRequest evento = new EventoRequest("Intruso", TipoEvento.INICIO, 0, 0);
        LaneRequest lane = new LaneRequest("Intrusa", rolA);
        EditarLaneRequest edicionLane = new EditarLaneRequest("Intrusa", rolA, 0L);
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/v1/usuarios/{id}", adminB, null, "Usuario no encontrado"),
                Arguments.of(HttpMethod.PATCH, "/api/v1/usuarios/{id}", adminB,
                        new ActualizarUsuarioRequest(RolAcceso.SOLO_LECTURA, null, 0L), "Usuario no encontrado"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/usuarios/{id}", adminB, null, "Usuario no encontrado"),
                Arguments.of(HttpMethod.GET, "/api/v1/empresas/{id}", empresaB, null, "Empresa no encontrada"),
                Arguments.of(HttpMethod.GET, "/api/v1/procesos/{id}", procesoB, null, "Proceso no encontrado"),
                Arguments.of(HttpMethod.PUT, "/api/v1/procesos/{id}", procesoB,
                        new EditarProcesoRequest("Intruso", "Desde la empresa A", "Otra", 0L),
                        "Proceso no encontrado"),
                Arguments.of(HttpMethod.PATCH, "/api/v1/procesos/{id}", procesoB,
                        new CambiarEstadoProcesoRequest(EstadoProceso.PUBLICADO, 0L), "Proceso no encontrado"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/procesos/{id}", procesoB, null, "Proceso no encontrado"),
                Arguments.of(HttpMethod.GET, "/api/v1/roles/{id}", rolB, null, "Rol de proceso no encontrado"),
                Arguments.of(HttpMethod.PUT, "/api/v1/roles/{id}", rolB,
                        new EditarRolProcesoRequest("Intruso", "Desde la empresa A", 0L),
                        "Rol de proceso no encontrado"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/roles/{id}", rolB, null, "Rol de proceso no encontrado"),
                Arguments.of(HttpMethod.POST, "/api/v1/procesos/{id}/pools", procesoB,
                        new PoolRequest("Intruso", TipoParticipante.CLIENTE, false, null),
                        "Proceso no encontrado"),
                Arguments.of(HttpMethod.GET, "/api/v1/pools/{id}", poolB, null, "Pool no encontrado"),
                Arguments.of(HttpMethod.PUT, "/api/v1/pools/{id}", poolB,
                        new EditarPoolRequest("Intruso", TipoParticipante.CLIENTE, null, 0L),
                        "Pool no encontrado"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/pools/{id}", poolB, null, "Pool no encontrado"),
                Arguments.of(HttpMethod.POST, "/api/v1/pools/{id}/lanes", poolB, lane, "Pool no encontrado"),
                Arguments.of(HttpMethod.GET, "/api/v1/lanes/{id}", laneB, null, "Lane no encontrada"),
                Arguments.of(HttpMethod.PUT, "/api/v1/lanes/{id}", laneB, edicionLane, "Lane no encontrada"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/lanes/{id}", laneB, null, "Lane no encontrada"),
                Arguments.of(HttpMethod.POST, "/api/v1/lanes/{id}/actividades", laneB, actividad, "Lane no encontrada"),
                Arguments.of(HttpMethod.GET, "/api/v1/actividades/{id}", actividadB, null, "Actividad no encontrada"),
                Arguments.of(HttpMethod.PUT, "/api/v1/actividades/{id}", actividadB,
                        new EditarActividadRequest("Intrusa", "Desde la empresa A", TipoActividad.USUARIO,
                                null, 0, 0, 0L),
                        "Actividad no encontrada"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/actividades/{id}", actividadB, null,
                        "Actividad no encontrada"),
                Arguments.of(HttpMethod.POST, "/api/v1/lanes/{id}/gateways", laneB, gateway, "Lane no encontrada"),
                Arguments.of(HttpMethod.GET, "/api/v1/gateways/{id}", gatewayB, null, "Gateway no encontrado"),
                Arguments.of(HttpMethod.PUT, "/api/v1/gateways/{id}", gatewayB,
                        new EditarGatewayRequest("Intruso", TipoGateway.PARALELO, null, 0, 0, 0L),
                        "Gateway no encontrado"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/gateways/{id}", gatewayB, null, "Gateway no encontrado"),
                Arguments.of(HttpMethod.POST, "/api/v1/lanes/{id}/eventos", laneB, evento, "Lane no encontrada"),
                Arguments.of(HttpMethod.GET, "/api/v1/eventos/{id}", eventoB, null, "Evento no encontrado"),
                Arguments.of(HttpMethod.PUT, "/api/v1/eventos/{id}", eventoB,
                        new EditarEventoRequest("Intruso", TipoEvento.INICIO, null, 0, 0, 0L),
                        "Evento no encontrado"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/eventos/{id}", eventoB, null, "Evento no encontrado"),
                Arguments.of(HttpMethod.GET, "/api/v1/arcos/{id}", arcoB, null, "Arco no encontrado"),
                Arguments.of(HttpMethod.PUT, "/api/v1/arcos/{id}", arcoB,
                        new EditarArcoRequest("Intruso", null, null, null, 0L),
                        "Arco no encontrado"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/arcos/{id}", arcoB, null, "Arco no encontrado"),
                Arguments.of(HttpMethod.POST, "/api/v1/procesos/{id}/mensajes", procesoB,
                        mensaje("Intruso", poolA, poolB), "Proceso no encontrado"),
                Arguments.of(HttpMethod.GET, "/api/v1/mensajes/{id}", mensajeB, null, "Mensaje no encontrado"),
                Arguments.of(HttpMethod.PUT, "/api/v1/mensajes/{id}", mensajeB,
                        new EditarMensajeRequest("Intruso", "Desde la empresa A", null, null, null, null,
                                null, false, null, null, null, null, 0L),
                        "Mensaje no encontrado"),
                Arguments.of(HttpMethod.DELETE, "/api/v1/mensajes/{id}", mensajeB, null, "Mensaje no encontrado"),
                Arguments.of(HttpMethod.GET, "/api/v1/mensajes/{id}/correlacion", mensajeB, null,
                        "no tiene correlacion"),
                Arguments.of(HttpMethod.PUT, "/api/v1/mensajes/{id}/correlacion", mensajeB,
                        new CorrelacionRequest("Intruso", null, null, null), "Mensaje no encontrado"));
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
                Arguments.of(HttpMethod.PUT, "/api/v1/lanes/{id}", laneA, new EditarLaneRequest("Mixta", rolB, 0L),
                        "Rol de proceso no encontrado"),
                Arguments.of(HttpMethod.POST, "/api/v1/arcos", null,
                        new ArcoRequest(gatewayA, gatewayB, null, null, null, null),
                        "Nodo de destino no encontrado"),
                // Mudar un nodo propio a una lane de la otra tienda: esa lane no existe para esta empresa.
                Arguments.of(HttpMethod.PUT, "/api/v1/gateways/{id}", gatewayA,
                        new EditarGatewayRequest("Revisar venta", TipoGateway.PARALELO, laneB, 100, 100, 0L),
                        "Lane no encontrada"),
                Arguments.of(HttpMethod.POST, "/api/v1/procesos/{id}/mensajes", procesoA,
                        mensaje("Mixto", poolA, poolB), "Pool de destino no encontrado"),
                // El anclaje tambien entra por id: un nodo de la empresa B no existe para la A.
                Arguments.of(HttpMethod.POST, "/api/v1/procesos/{id}/mensajes", procesoA,
                        anclado("Mixto", poolA, poolClienteA, actividadB),
                        "Nodo de origen del mensaje no encontrado"));
    }

    /** Un mensaje de prueba con lo minimo: el resto de campos llega vacio. */
    private static MensajeRequest mensaje(String nombre, Long origen, Long destino) {
        return new MensajeRequest(nombre, "Desde la empresa A", origen, destino, null, null, null, null,
                null, false, null, null, null, null);
    }

    /** Un mensaje que se ancla a un nodo por su id. */
    private static MensajeRequest anclado(String nombre, Long origen, Long destino, Long nodoOrigen) {
        return new MensajeRequest(nombre, "Desde la empresa A", origen, destino, nodoOrigen, null, null,
                null, null, false, null, null, null, null);
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
    @DisplayName("Un empresaId de otra empresa en la URL se ignora: manda el token")
    void Aislamiento_empresaIdEnLaUrl_seIgnora() throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/procesos?empresaId={id}", empresaB)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new ProcesoRequest("Devoluciones", "Proceso de devoluciones", "Comercial"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long procesoCreado = jsonMapper.readTree(respuesta).get("id").asLong();

        assertThat(procesoService.obtener(empresaA, procesoCreado).nombre()).isEqualTo("Devoluciones");
        assertThatThrownBy(() -> procesoService.obtener(empresaB, procesoCreado))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Un empresaId en el cuerpo se rechaza con 400 y no crea nada en ninguna empresa")
    void Aislamiento_empresaIdEnElCuerpo_seRechaza() throws Exception {
        List<Object> empresaBAntes = estadoEmpresaB();
        Map<String, Object> cuerpo = Map.of("nombre", "Cambios", "descripcion", "Proceso de cambios",
                "categoria", "Comercial", "empresaId", empresaB);

        mockMvc.perform(post("/api/v1/procesos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.empresaId").value("El campo no existe en esta operación."));

        assertThat(valoresComoEmpresaA("/api/v1/procesos", "nombre")).doesNotContain("Cambios");
        assertThat(estadoEmpresaB()).isEqualTo(empresaBAntes);
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
        ProcesoResponse proceso = procesoService.obtener(empresaB, procesoB);
        RolProcesoVistaResponse rol = rolProcesoService.obtener(empresaB, rolB);
        UsuarioResponse admin = usuarioService.obtener(empresaB, adminB);
        return List.of(
                proceso.nombre(), proceso.estado(), proceso.activo(),
                rol.nombre(), rol.descripcion(),
                admin.rolAcceso(), admin.activo(),
                poolService.listarPorProceso(empresaB, procesoB).stream().map(PoolResponse::nombre).toList(),
                laneService.listarPorPool(empresaB, poolB).stream().map(LaneResponse::nombre).toList(),
                actividadService.obtener(empresaB, actividadB).nombre(),
                gatewayService.obtener(empresaB, gatewayB).nombre(),
                gatewayService.obtener(empresaB, gatewayCierreB).nombre(),
                arcoService.obtener(empresaB, arcoB).etiqueta(),
                mensajeService.listarPorProceso(empresaB, procesoB).stream().map(MensajeResponse::nombre).toList(),
                correlacionService.obtener(empresaB, mensajeB).criterio());
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
