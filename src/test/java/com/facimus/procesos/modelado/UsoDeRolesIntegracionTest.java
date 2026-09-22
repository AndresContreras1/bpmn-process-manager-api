package com.facimus.procesos.modelado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.gestion.dto.response.RolProcesoVistaResponse;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.PoolService;

/** El uso de un rol se mide en procesos activos: gestion lo pregunta y modelado lo responde con sus lanes. */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsoDeRolesIntegracionTest {

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
        empresaId = empresaService.registrar("Tienda de roles", "900444555-6", "contacto@roles.com",
                "Administrador", "admin@roles.com", "clave12345").id();
        adminId = usuarioRepository.findByEmail("admin@roles.com").orElseThrow().getId();
    }

    @Test
    @DisplayName("Un rol con dos lanes en el mismo proceso lo usa un proceso, no dos")
    void rolEnDosLanesDeUnProceso_cuentaUnProceso() {
        Long rolId = rolProcesoService.crear(empresaId, "Warehouse", "Picks and packs").id();
        Long procesoId = procesoService.crear(empresaId, adminId, "Order fulfillment", "Checkout to delivery",
                "Fulfillment").id();
        Long poolId = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        laneService.crear(empresaId, adminId, poolId, "Picking", rolId);
        laneService.crear(empresaId, adminId, poolId, "Packing", rolId);

        RolProcesoVistaResponse rol = rolProcesoService.obtener(empresaId, rolId);

        assertThat(rol.procesosQueLoUsan()).isEqualTo(1);
        assertThat(rol.enUso()).isTrue();
    }

    @Test
    @DisplayName("Si el unico proceso que usaba el rol se elimina, el rol queda libre y se puede eliminar")
    void procesoEliminado_liberaElRol() {
        Long rolId = rolProcesoService.crear(empresaId, "Returns desk", "Receives returned items").id();
        Long procesoId = procesoService.crear(empresaId, adminId, "Returns", "Return request to refund",
                "After-sales").id();
        Long poolId = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        laneService.crear(empresaId, adminId, poolId, "Returns desk", rolId);

        procesoService.eliminarLogico(empresaId, procesoId, adminId);

        assertThat(rolProcesoService.obtener(empresaId, rolId).enUso()).isFalse();
        assertThatCode(() -> rolProcesoService.eliminar(empresaId, rolId)).doesNotThrowAnyException();
    }
}
