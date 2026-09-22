package com.facimus.procesos.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.gestion.dto.response.HistorialCambioResponse;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
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

import jakarta.persistence.EntityManagerFactory;

/**
 * Las relaciones son LAZY: un listado que muestra datos de una asociacion la trae con un @EntityGraph en la misma
 * consulta, en vez de disparar una consulta por fila (N+1). Se cuentan las sentencias SQL con las estadisticas de
 * Hibernate.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CargaPerezosaTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

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

    @Autowired
    private DiagramaService diagramaService;

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
        adminId = usuarioService.autenticar("admin@consultas.com", "clave12345").id();
        Long editorId = usuarioService.crearColaborador(empresaId, "Editora", "editora@consultas.com", "clave12345",
                RolAcceso.EDITOR).id();

        procesoId = procesoService.crear(empresaId, adminId, "Order fulfillment", "Checkout to delivery",
                "Fulfillment").id();
        procesoService.editarDatos(empresaId, procesoId, editorId, "Order fulfillment", "Checkout to delivery, v2",
                "Fulfillment");
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
        correlacionService.definir(empresaId, mensajeId, "orderId");
    }
}
