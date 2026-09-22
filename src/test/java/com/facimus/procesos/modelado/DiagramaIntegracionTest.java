package com.facimus.procesos.modelado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.GatewayResponse;
import com.facimus.procesos.modelado.dto.response.LaneResponse;
import com.facimus.procesos.modelado.dto.response.PoolResponse;
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

/** El diagrama completo de un proceso: cada elemento, enlazado por id, y nada de los otros procesos de la tienda. */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DiagramaIntegracionTest {

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

    private Long empresaId;
    private Long adminId;
    private Long procesoId;

    @BeforeAll
    void modelarElDespachoDeUnPedido() {
        empresaId = empresaService.registrar("Tienda del diagrama", "900777888-9", "contacto@diagrama.com",
                "Administrador", "admin@diagrama.com", "clave12345").id();
        adminId = usuarioService.autenticar("admin@diagrama.com", "clave12345").id();
        procesoId = procesoService.crear(empresaId, adminId, "Order fulfillment", "Checkout to delivery",
                "Fulfillment").id();
        Long tiendaId = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        Long clienteId = poolService.crear(empresaId, procesoId, "Customer", TipoParticipante.CLIENTE, true).id();
        Long ventas = laneService.crear(empresaId, tiendaId, "Sales",
                rolProcesoService.crear(empresaId, "Sales", null).id()).id();
        Long bodega = laneService.crear(empresaId, tiendaId, "Warehouse",
                rolProcesoService.crear(empresaId, "Warehouse", null).id()).id();
        Long recibir = actividadService.crear(empresaId, ventas, "Receive order", null, 100, 80).id();
        Long pagado = gatewayService.crear(empresaId, ventas, "Payment approved?", TipoGateway.EXCLUSIVO, 260, 80)
                .id();
        Long empacar = actividadService.crear(empresaId, bodega, "Pick and pack items", null, 420, 200).id();
        arcoService.crear(empresaId, recibir, pagado, null, "Authorization response received");
        arcoService.crear(empresaId, pagado, empacar, "Approved", "payment.status == APPROVED");
        Long pedido = mensajeService.crear(empresaId, procesoId, "Order placed", "Cart items", clienteId, tiendaId)
                .id();
        correlacionService.definir(empresaId, pedido, "orderId");

        // Otro proceso de la misma tienda, con un participante propio que no pertenece al diagrama anterior.
        Long devoluciones = procesoService.crear(empresaId, adminId, "Returns and refunds", "Return to refund",
                "After-sales").id();
        poolService.crear(empresaId, devoluciones, "Carrier", TipoParticipante.PROVEEDOR, true);
    }

    @Test
    @DisplayName("El diagrama trae cada elemento del proceso, y cada id apunta a otro elemento del mismo diagrama")
    void diagrama_traeCadaElementoEnlazadoPorId() {
        DiagramaResponse diagrama = diagramaService.obtener(empresaId, procesoId);

        assertThat(diagrama.proceso().nombre()).isEqualTo("Order fulfillment");
        assertThat(diagrama.lanes()).extracting(LaneResponse::rolProcesoNombre).containsExactly("Sales", "Warehouse");
        Set<Long> pools = ids(diagrama.pools(), PoolResponse::id);
        Set<Long> lanes = ids(diagrama.lanes(), LaneResponse::id);
        Set<Long> nodos = new HashSet<>(ids(diagrama.actividades(), ActividadResponse::id));
        nodos.addAll(ids(diagrama.gateways(), GatewayResponse::id));

        assertThat(diagrama.lanes()).allMatch(lane -> pools.contains(lane.poolId()));
        assertThat(diagrama.actividades()).hasSize(2).allMatch(actividad -> lanes.contains(actividad.laneId()));
        assertThat(diagrama.gateways()).hasSize(1).allMatch(gateway -> lanes.contains(gateway.laneId()));
        assertThat(diagrama.arcos()).hasSize(2)
                .allMatch(arco -> nodos.contains(arco.origenId()) && nodos.contains(arco.destinoId()));
        assertThat(diagrama.mensajes()).singleElement()
                .satisfies(mensaje -> assertThat(pools).contains(mensaje.poolOrigenId(), mensaje.poolDestinoId()));
        assertThat(diagrama.correlaciones()).singleElement().satisfies(correlacion -> {
            assertThat(correlacion.criterio()).isEqualTo("orderId");
            assertThat(correlacion.mensajeId()).isEqualTo(diagrama.mensajes().getFirst().id());
        });
    }

    @Test
    @DisplayName("Los participantes van en el orden del diagrama y no se cuelan los de otro proceso de la tienda")
    void diagrama_soloTraeLosParticipantesDelProceso() {
        assertThat(diagramaService.obtener(empresaId, procesoId).pools())
                .extracting(PoolResponse::tipoParticipante)
                .containsExactly(TipoParticipante.EMPRESA, TipoParticipante.CLIENTE);
    }

    @Test
    @DisplayName("Un proceso eliminado ya no tiene diagrama: 404")
    void diagrama_procesoEliminado_noExiste() {
        Long eliminado = procesoService.crear(empresaId, adminId, "Payments", "Capture and refunds", "Payments").id();
        procesoService.eliminarLogico(empresaId, eliminado, adminId);

        assertThatThrownBy(() -> diagramaService.obtener(empresaId, eliminado))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Proceso no encontrado.");
    }

    private static <T> Set<Long> ids(List<T> elementos, Function<T, Long> id) {
        return elementos.stream().map(id).collect(Collectors.toSet());
    }
}
