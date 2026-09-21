package com.facimus.procesos.modelado;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.dto.response.LaneResponse;
import com.facimus.procesos.modelado.dto.response.PoolResponse;
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
    private UsuarioService usuarioService;

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
        adminId = usuarioService.autenticar("admin@orden.com", "clave12345").id();
    }

    @Test
    @DisplayName("Un pool nuevo va despues del ultimo, aunque se haya eliminado uno del medio")
    void poolNuevo_trasEliminarUnoDelMedio_noRepitePosicion() {
        Long procesoId = procesoService.crear(empresaId, adminId, "Order fulfillment", "Checkout to delivery",
                "Fulfillment").id();
        Long clienteId = poolService.crear(empresaId, procesoId, "Customer", TipoParticipante.CLIENTE, true).id();
        poolService.crear(empresaId, procesoId, "Carrier", TipoParticipante.PROVEEDOR, true);
        poolService.eliminar(empresaId, clienteId);

        PoolResponse nuevo = poolService.crear(empresaId, procesoId, "Payment gateway",
                TipoParticipante.SISTEMA_EXTERNO, true);

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
        Long primeraId = laneService.crear(empresaId, poolId, "Reception", rolId).id();
        laneService.crear(empresaId, poolId, "Inspection", rolId);
        laneService.eliminar(empresaId, primeraId);

        LaneResponse nueva = laneService.crear(empresaId, poolId, "Refunds", rolId);

        assertThat(nueva.orden()).isEqualTo(2);
        assertThat(laneService.listarPorPool(empresaId, poolId))
                .extracting(LaneResponse::orden)
                .doesNotHaveDuplicates();
    }
}
