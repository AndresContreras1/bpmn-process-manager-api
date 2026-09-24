package com.facimus.procesos.modelado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.modelado.dto.response.LaneResponse;
import com.facimus.procesos.modelado.dto.response.PoolResponse;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.PoolService;

/** El orden de pools y lanes define como se dibuja el diagrama: dos elementos nunca comparten posicion. */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrdenIntegracionTest {

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

    private Long empresaId;
    private Long adminId;

    @BeforeAll
    void registrarTienda() {
        empresaId = empresaService.registrar("Tienda de orden", "900888999-0", "contacto@orden.com",
                "Administrador", "admin@orden.com", "clave12345").id();
        adminId = usuarioRepository.findByEmail("admin@orden.com").orElseThrow().getId();
    }

    @Test
    @DisplayName("Un pool nuevo va despues del ultimo, aunque se haya eliminado uno del medio")
    void poolNuevo_trasEliminarUnoDelMedio_noRepitePosicion() {
        Long procesoId = procesoService.crear(empresaId, adminId, "Order fulfillment", "Checkout to delivery",
                "Fulfillment").id();
        Long clienteId = poolService.crear(empresaId, adminId, procesoId, "Customer", TipoParticipante.CLIENTE,
                true,Integracion.NINGUNA).id();
        poolService.crear(empresaId, adminId, procesoId, "Carrier", TipoParticipante.PROVEEDOR, true,
                Integracion.NINGUNA);
        poolService.eliminar(empresaId, adminId, clienteId);

        PoolResponse nuevo = poolService.crear(empresaId, adminId, procesoId, "Payment gateway",
                TipoParticipante.SISTEMA_EXTERNO, true, Integracion.NINGUNA);

        assertThat(nuevo.orden()).isEqualTo(3);
        assertThat(poolService.listarPorProceso(empresaId, procesoId))
                .extracting(PoolResponse::orden)
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Una lane nueva va despues de la ultima, aunque se haya eliminado una del medio")
    void laneNueva_trasEliminarUnaDelMedio_noRepitePosicion() {
        Long procesoId = procesoService.crear(empresaId, adminId, "Returns", "Return request to refund",
                "After-sales").id();
        Long poolId = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        Long rolId = rolProcesoService.crear(empresaId, "Returns desk", null).id();
        Long primeraId = laneService.crear(empresaId, adminId, poolId, "Reception", rolId).id();
        laneService.crear(empresaId, adminId, poolId, "Inspection", rolId);
        laneService.eliminar(empresaId, adminId, primeraId);

        LaneResponse nueva = laneService.crear(empresaId, adminId, poolId, "Refunds", rolId);

        assertThat(nueva.orden()).isEqualTo(2);
        assertThat(laneService.listarPorPool(empresaId, poolId))
                .extracting(LaneResponse::orden)
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("R-43: reordenar las lanes del pool las deja en el orden de la lista")
    void reordenar_lanesDelPool_lasDejaEnElOrdenPedido() {
        Long procesoId = procesoService.crear(empresaId, adminId, "Picking", "Pick to ship", "Fulfillment").id();
        Long poolId = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        Long rolId = rolProcesoService.crear(empresaId, "Picking", null).id();
        Long ventas = laneService.crear(empresaId, adminId, poolId, "Sales", rolId).id();
        Long almacen = laneService.crear(empresaId, adminId, poolId, "Warehouse", rolId).id();
        Long envios = laneService.crear(empresaId, adminId, poolId, "Shipping", rolId).id();

        List<LaneResponse> reordenadas = laneService.reordenar(empresaId, adminId, poolId,
                List.of(envios, ventas, almacen));

        assertThat(reordenadas).extracting(LaneResponse::id).containsExactly(envios, ventas, almacen);
        assertThat(reordenadas).extracting(LaneResponse::orden).containsExactly(0, 1, 2);
        assertThat(laneService.listarPorPool(empresaId, poolId)).extracting(LaneResponse::id)
                .containsExactly(envios, ventas, almacen);
    }

    @Test
    @DisplayName("R-43: una lista que no trae todas las lanes del pool no reordena nada")
    void reordenar_conLaListaIncompleta_lanzaReglaNegocio() {
        Long procesoId = procesoService.crear(empresaId, adminId, "Packing", "Pack to ship", "Fulfillment").id();
        Long poolId = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        Long rolId = rolProcesoService.crear(empresaId, "Packing", null).id();
        Long primera = laneService.crear(empresaId, adminId, poolId, "Packing", rolId).id();
        Long segunda = laneService.crear(empresaId, adminId, poolId, "Labelling", rolId).id();

        assertThatThrownBy(() -> laneService.reordenar(empresaId, adminId, poolId, List.of(segunda)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La lista de orden debe contener exactamente las lanes del pool.");
        assertThatThrownBy(() -> laneService.reordenar(empresaId, adminId, poolId, List.of(primera, primera)))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(laneService.listarPorPool(empresaId, poolId)).extracting(LaneResponse::id)
                .containsExactly(primera, segunda);
    }

    @Test
    @DisplayName("R-43: los participantes del proceso se reordenan con la misma regla")
    void reordenar_poolsDelProceso_losDejaEnElOrdenPedido() {
        Long procesoId = procesoService.crear(empresaId, adminId, "Shipping", "Ship to deliver", "Logistics").id();
        Long tienda = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        Long cliente = poolService.crear(empresaId, adminId, procesoId, "Customer", TipoParticipante.CLIENTE, true,
                Integracion.NINGUNA).id();

        List<PoolResponse> reordenados = poolService.reordenar(empresaId, adminId, procesoId,
                List.of(cliente, tienda));

        assertThat(reordenados).extracting(PoolResponse::id).containsExactly(cliente, tienda);
        assertThatThrownBy(() -> poolService.reordenar(empresaId, adminId, procesoId, List.of(tienda)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La lista de orden debe contener exactamente los pools del proceso.");
    }
}
