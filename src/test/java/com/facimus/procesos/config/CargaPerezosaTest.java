package com.facimus.procesos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.gestion.dto.response.HistorialCambioResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoRecibidoResponse;
import com.facimus.procesos.gestion.dto.response.RolProcesoVistaResponse;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoCompartidoService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.SesionService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.LaneResponse;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.CorrelacionService;
import com.facimus.procesos.modelado.service.DiagramaService;
import com.facimus.procesos.modelado.service.GatewayService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.MensajeService;
import com.facimus.procesos.modelado.service.PoolService;
import com.facimus.procesos.security.ApiPrincipal;
import com.facimus.procesos.security.JwtService;

import jakarta.persistence.EntityManagerFactory;

/**
 * Las relaciones son LAZY: un listado que muestra datos de una asociacion la trae con un @EntityGraph en la misma
 * consulta, en vez de disparar una consulta por fila (N+1). Se cuentan las sentencias SQL con las estadisticas de
 * Hibernate.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CargaPerezosaTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SesionService sesionService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

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
    private ArcoService arcoService;

    @Autowired
    private MensajeService mensajeService;

    @Autowired
    private CorrelacionService correlacionService;

    @Autowired
    private DiagramaService diagramaService;

    @Autowired
    private ProcesoCompartidoService procesoCompartidoService;

    private Statistics estadisticas;
    private Long empresaId;
    private Long adminId;
    private Long procesoId;
    private Long poolId;

    @BeforeAll
    void modelarUnProcesoConTresLanes() {
        estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        empresaId = empresaService.registrar("Tienda de consultas", "900666777-8", "contacto@consultas.com",
                "Administrador", "admin@consultas.com", "clave12345").id();
        adminId = usuarioRepository.findByEmail("admin@consultas.com").orElseThrow().getId();
        Long editorId = usuarioService.crearColaborador(empresaId, "Editora", "editora@consultas.com", "clave12345",
                RolAcceso.EDITOR).id();

        procesoId = procesoService.crear(empresaId, adminId, "Order fulfillment", "Checkout to delivery",
                "Fulfillment").id();
        procesoService.editarDatos(empresaId, procesoId, editorId, "Order fulfillment", "Checkout to delivery, v2",
                "Fulfillment", 0L);
        poolId = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        for (String rol : new String[] {"Sales", "Warehouse", "Shipping"}) {
            laneService.crear(empresaId, poolId, rol, rolProcesoService.crear(empresaId, rol, null).id());
        }
    }

    @Test
    @DisplayName("Listar las lanes de un pool trae el rol de cada una en la misma consulta")
    void lanesDeUnPool_seListanConSusRolesEnUnaConsulta() {
        estadisticas.clear();

        assertThat(laneService.listarPorPool(empresaId, poolId))
                .extracting(LaneResponse::rolProcesoNombre)
                .containsExactly("Sales", "Warehouse", "Shipping");
        // Una para comprobar que el pool es de la empresa y otra para las lanes con sus roles.
        assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("El historial de un proceso trae el autor de cada cambio en la misma consulta")
    void historialDeUnProceso_seListaConSusAutoresEnUnaConsulta() {
        estadisticas.clear();

        assertThat(procesoService.listarHistorial(empresaId, procesoId))
                .extracting(HistorialCambioResponse::autorNombre)
                .containsExactlyInAnyOrder("Administrador", "Editora");
        // Una para el proceso y otra para el historial con sus autores.
        assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Una pagina de roles trae el uso de todos sus roles en una sola consulta, y se busca por nombre")
    void paginaDeRoles_traeElUsoDeTodosEnUnaConsulta() {
        estadisticas.clear();

        PageResponse<RolProcesoVistaResponse> roles = rolProcesoService.buscar(empresaId, null,
                Paginacion.de(0, 10, "nombre,asc"));

        assertThat(roles.content()).hasSizeGreaterThanOrEqualTo(3).allMatch(rol -> rol.procesosQueLoUsan() == 1);
        // Una para la pagina y otra para el uso de sus roles; la pagina no se llena, asi que no hace falta contar.
        assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(2);
        // HU-20: parte del nombre, sin distinguir mayusculas.
        assertThat(rolProcesoService.buscar(empresaId, "WARE", Paginacion.de(0, 10, "nombre,asc")).content())
                .extracting(RolProcesoVistaResponse::nombre)
                .containsExactly("Warehouse");
    }

    @Test
    @DisplayName("Los procesos compartidos con una tienda traen a su duena en la misma consulta (HU-23)")
    void procesosCompartidos_traenASuDuenaEnLaMismaConsulta() {
        Long aliadaId = empresaService.registrar("Tienda aliada", "900666999-1", "contacto@aliada.com",
                "Administrador", "admin@aliada.com", "clave12345").id();
        for (String nombre : new String[] {"Payments", "Inventory count"}) {
            Long compartido = procesoService.crear(empresaId, adminId, nombre, "Shared process", "Operations").id();
            procesoCompartidoService.compartir(empresaId, compartido, adminId, "900666999-1");
        }
        estadisticas.clear();

        assertThat(procesoCompartidoService.buscarRecibidos(aliadaId, Paginacion.de(0, 10, "nombre,asc")).content())
                .extracting(ProcesoRecibidoResponse::empresaPropietariaNombre)
                .containsExactly("Tienda de consultas", "Tienda de consultas");
        // Una sola: la pagina no se llena, asi que no hace falta contar, y la duena llega con el @EntityGraph.
        assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("El filtro JWT autentica sin consultar la base: una peticion que la autorizacion rechaza no corre SQL")
    void filtroJwt_autenticaSinConsultarLaBase() throws Exception {
        UsuarioResponse lectora = usuarioService.crearColaborador(empresaId, "Lectora", "lectora@consultas.com",
                "clave12345", RolAcceso.SOLO_LECTURA);
        String token = jwtService.generarToken(
                ApiPrincipal.of(lectora, sesionService.iniciar(empresaId, lectora.id()).sesion()));

        estadisticas.clear();
        mockMvc.perform(post("/api/v1/procesos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        // El rol y la tienda salen de los claims, y las sesiones cerradas se miran en memoria.
        assertThat(estadisticas.getPrepareStatementCount()).isZero();
    }

    @Test
    @DisplayName("Renovar cuesta tres sentencias: leer el token con su sesion y su usuario, usarlo y emitir otro")
    void renovarSesion_cuestaTresSentencias() {
        String refreshToken = sesionService.iniciar(empresaId, adminId).refreshToken();

        estadisticas.clear();
        sesionService.renovar(refreshToken);

        assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("El diagrama completo se arma con una consulta por tipo de elemento, crezca lo que crezca")
    void diagramaCompleto_seArmaConUnaConsultaPorTipoDeElemento() {
        // Un proceso propio del test: los demas de la clase comparten el de @BeforeAll y cuentan sus lanes.
        Long devoluciones = procesoService.crear(empresaId, adminId, "Returns and refunds", "Return to refund",
                "After-sales").id();
        Long tienda = poolService.listarPorProceso(empresaId, devoluciones).getFirst().id();
        Long cliente = poolService.crear(empresaId, devoluciones, "Customer", TipoParticipante.CLIENTE, true).id();
        Long rol = rolProcesoService.crear(empresaId, "After-sales", null).id();
        agregarUnaLaneConSuFlujo(devoluciones, tienda, cliente, rol, "Customer service");

        estadisticas.clear();
        DiagramaResponse pequeno = diagramaService.obtener(empresaId, devoluciones);
        long sentenciasDelPequeno = estadisticas.getPrepareStatementCount();

        agregarUnaLaneConSuFlujo(devoluciones, tienda, cliente, rol, "Refunds");
        estadisticas.clear();
        DiagramaResponse grande = diagramaService.obtener(empresaId, devoluciones);

        assertThat(grande.lanes()).hasSize(2 * pequeno.lanes().size());
        assertThat(grande.actividades()).hasSize(2 * pequeno.actividades().size());
        assertThat(grande.arcos()).hasSize(2 * pequeno.arcos().size());
        assertThat(grande.correlaciones()).hasSize(2 * pequeno.correlaciones().size());
        // El proceso, y una por pools, lanes con sus roles, actividades, gateways, arcos, mensajes y correlaciones.
        assertThat(sentenciasDelPequeno).isEqualTo(8);
        assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(8);
    }

    /** Una lane con dos actividades y un gateway unidos por arcos, y un mensaje correlacionado desde el cliente. */
    private void agregarUnaLaneConSuFlujo(Long procesoId, Long tiendaId, Long clienteId, Long rolId, String lane) {
        Long laneId = laneService.crear(empresaId, tiendaId, lane, rolId).id();
        Long recibir = actividadService.crear(empresaId, laneId, lane + ": receive", null, 100, 80).id();
        Long decidir = gatewayService.crear(empresaId, laneId, lane + ": approved?", TipoGateway.EXCLUSIVO, 260, 80)
                .id();
        Long reembolsar = actividadService.crear(empresaId, laneId, lane + ": refund", null, 420, 80).id();
        arcoService.crear(empresaId, recibir, decidir, null, "Return received");
        arcoService.crear(empresaId, decidir, reembolsar, "Approved", "return.status == APPROVED");
        Long mensajeId = mensajeService.crear(empresaId, procesoId, lane + ": return request", "Order and items",
                clienteId, tiendaId).id();
        correlacionService.definir(empresaId, mensajeId, "orderId", null);
    }
}
