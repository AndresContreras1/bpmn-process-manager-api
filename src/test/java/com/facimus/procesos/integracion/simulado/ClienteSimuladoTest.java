package com.facimus.procesos.integracion.simulado;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.ejecucion.puerto.ParametrosDeSimulacion;

/**
 * D7: los pedidos que el cliente simulado inventa salen de la semilla, asi que la misma tienda pidiendo los
 * mismos pedidos vuelve a obtener exactamente los mismos. Lo que trae la plantilla manda sobre lo inventado.
 */
class ClienteSimuladoTest {

    private static final List<String> REFERENCIAS = List.of("SIM-1-1", "SIM-1-2", "SIM-1-3");

    private final ClienteSimulado cliente = new ClienteSimulado();

    @Test
    @DisplayName("Cada pedido lleva su referencia y un monto, y los mismos pedidos dan siempre lo mismo")
    void losMismosPedidos_danSiempreLoMismo() {
        List<Map<String, Object>> unos = cliente.pedidos(3, null, REFERENCIAS, ParametrosDeSimulacion.deFabrica());
        List<Map<String, Object>> otros = cliente.pedidos(3, null, REFERENCIAS, ParametrosDeSimulacion.deFabrica());

        assertThat(unos).isEqualTo(otros).hasSize(3);
        assertThat(unos).extracting(pedido -> pedido.get("orderId")).containsExactlyElementsOf(REFERENCIAS);
        assertThat(unos).allSatisfy(pedido ->
                assertThat((Integer) pedido.get("total")).isBetween(100, 10_000));
    }

    @Test
    @DisplayName("Con otra semilla los montos cambian; dos pedidos distintos tampoco valen lo mismo")
    void otraSemilla_cambiaLosMontos() {
        List<Map<String, Object>> conLaDeFabrica = cliente.pedidos(3, null, REFERENCIAS,
                ParametrosDeSimulacion.deFabrica());
        List<Map<String, Object>> conOtra = cliente.pedidos(3, null, REFERENCIAS,
                new ParametrosDeSimulacion(99L, 10, 1, null, 1, 3, 5, 2));

        assertThat(montos(conLaDeFabrica)).isNotEqualTo(montos(conOtra));
        assertThat(montos(conLaDeFabrica)).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Lo que trae la plantilla manda: quien pide la tanda sabe mejor lo que quiere")
    void laPlantilla_mandaSobreLoInventado() {
        List<Map<String, Object>> pedidos = cliente.pedidos(2, Map.of("total", 7_777, "channel", "web"),
                REFERENCIAS, ParametrosDeSimulacion.deFabrica());

        assertThat(montos(pedidos)).containsOnly(7_777);
        assertThat(pedidos).allSatisfy(pedido -> assertThat(pedido).containsEntry("channel", "web"));
        assertThat(pedidos).extracting(pedido -> pedido.get("orderId")).containsExactly("SIM-1-1", "SIM-1-2");
    }

    private static List<Object> montos(List<Map<String, Object>> pedidos) {
        return pedidos.stream().map(pedido -> pedido.get("total")).toList();
    }
}
