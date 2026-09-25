package com.facimus.procesos.ejecucion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.ejecucion.dto.response.CasoDetalleResponse;
import com.facimus.procesos.ejecucion.dto.response.CasoResponse;
import com.facimus.procesos.ejecucion.dto.response.MensajeEntranteResponse;
import com.facimus.procesos.ejecucion.dto.response.PedidosSimuladosResponse;
import com.facimus.procesos.ejecucion.model.OrigenMensajeEntrante;
import com.facimus.procesos.ejecucion.service.CasoService;
import com.facimus.procesos.ejecucion.service.MensajeriaService;
import com.facimus.procesos.ejecucion.service.SimulacionService;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;

/**
 * D7: el cliente simulado compra. Una tanda de pedidos entra por la misma puerta que cualquier otro mensaje, con
 * referencias numeradas y montos que salen de la semilla, asi que la misma tanda de la misma tienda vuelve a dar
 * exactamente los mismos pedidos.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PedidosSimuladosTest {

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ProcesoService procesoService;

    @Autowired
    private CasoService casoService;

    @Autowired
    private MensajeriaService mensajeriaService;

    @Autowired
    private SimulacionService simulacionService;

    @Autowired
    private ApplicationContext contexto;

    private TiendaConMensajeria tienda;
    private Long empresaId;
    private Long adminId;

    @BeforeAll
    void registrarLaTienda() {
        tienda = new TiendaConMensajeria(contexto);
        empresaId = empresaService.registrar("Tienda que genera pedidos", "900555999-1", "contacto@pedidos.com",
                "Administradora", "admin@pedidos.com", "clave12345").id();
        adminId = usuarioRepository.findByEmail("admin@pedidos.com").orElseThrow().getId();
    }

    @Test
    @DisplayName("Una tanda de veinte pedidos abre veinte casos, numerados y con su monto")
    void unaTanda_abreUnCasoPorPedido() {
        Long procesoId = tienda.publicar(empresaId, adminId, "Order fulfillment with a batch");

        PedidosSimuladosResponse tanda = simulacionService.pedidos(empresaId, procesoId, 20, null);

        assertThat(tanda.mensaje()).isEqualTo(TiendaConMensajeria.PEDIDO);
        assertThat(tanda.pedidos()).isEqualTo(20);
        assertThat(tanda.casosNuevos()).isEqualTo(20);
        assertThat(tanda.referencias()).hasSize(20)
                .startsWith("SIM-" + procesoId + "-1").endsWith("SIM-" + procesoId + "-20");
        assertThat(casosDe(procesoId)).hasSize(20);
        assertThat(casoService.obtener(empresaId, unCaso(procesoId).id()).variables())
                .extractingByKey("order").asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.map(String.class, Object.class))
                .containsKey("total");
    }

    @Test
    @DisplayName("Los pedidos entran como del cliente simulado, y la segunda tanda sigue numerando")
    void laSegundaTanda_sigueNumerando() {
        Long procesoId = tienda.publicar(empresaId, adminId, "Order fulfillment with two batches");
        simulacionService.pedidos(empresaId, procesoId, 3, null);

        PedidosSimuladosResponse segunda = simulacionService.pedidos(empresaId, procesoId, 2, null);

        assertThat(segunda.referencias()).containsExactly("SIM-" + procesoId + "-4", "SIM-" + procesoId + "-5");
        assertThat(casosDe(procesoId)).hasSize(5);
        assertThat(mensajeriaService.bandejaDeEntrada(empresaId, procesoId, null, Paginacion.de(0, 50)).content())
                .extracting(MensajeEntranteResponse::origen).containsOnly(OrigenMensajeEntrante.CLIENTE_SIMULADO);
    }

    @Test
    @DisplayName("Lo que trae la plantilla manda sobre lo que el cliente simulado inventa")
    void laPlantilla_mandaSobreLoInventado() {
        Long procesoId = tienda.publicar(empresaId, adminId, "Order fulfillment with a template");

        simulacionService.pedidos(empresaId, procesoId, 2, Map.of("total", 9999, "channel", "web"));

        assertThat(montos(procesoId)).containsOnly(9999);
        assertThat(casoService.obtener(empresaId, unCaso(procesoId).id()).variables())
                .extractingByKey("order").asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.map(String.class, Object.class))
                .containsEntry("channel", "web");
    }

    @Test
    @DisplayName("A un proceso que se abre a mano no se le piden pedidos simulados")
    void procesoQueSeAbreAMano_noRecibeTandas() {
        Long procesoId = procesoService.crear(empresaId, adminId, "Returns without a message", "Sin mensaje de "
                + "inicio", "Operations").id();

        assertThatThrownBy(() -> simulacionService.pedidos(empresaId, procesoId, 3, null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no tiene una versión publicada vigente");
    }

    @Test
    @DisplayName("Se piden entre uno y doscientos pedidos por vez")
    void laTanda_tieneLimites() {
        Long procesoId = tienda.publicar(empresaId, adminId, "Order fulfillment with a huge batch");

        assertThatThrownBy(() -> simulacionService.pedidos(empresaId, procesoId, 0, null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("entre 1 y 200");
        assertThatThrownBy(() -> simulacionService.pedidos(empresaId, procesoId, 201, null))
                .isInstanceOf(ReglaNegocioException.class);
    }

    private List<Integer> montos(Long procesoId) {
        return casosDe(procesoId).stream()
                .map(caso -> casoService.obtener(empresaId, caso.id()))
                .map(PedidosSimuladosTest::total)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Integer total(CasoDetalleResponse detalle) {
        Object pedido = detalle.variables().get("order");
        return pedido instanceof Map<?, ?> mapa
                ? (Integer) ((Map<String, Object>) mapa).get("total")
                : 0;
    }

    private List<CasoResponse> casosDe(Long procesoId) {
        return casoService.listar(empresaId, procesoId, null, null, Paginacion.de(0, 50)).content();
    }

    private CasoResponse unCaso(Long procesoId) {
        return casosDe(procesoId).getFirst();
    }
}
