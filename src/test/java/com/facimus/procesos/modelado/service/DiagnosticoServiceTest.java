package com.facimus.procesos.modelado.service;

import static com.facimus.procesos.modelado.service.DiagramaArmado.ALMACEN;
import static com.facimus.procesos.modelado.service.DiagramaArmado.TIENDA;
import static com.facimus.procesos.modelado.service.DiagramaArmado.VENTAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.modelado.dto.response.DiagnosticoResponse;
import com.facimus.procesos.modelado.dto.response.HallazgoDiagnosticoResponse;
import com.facimus.procesos.modelado.model.Severidad;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.service.impl.DiagnosticoServiceImpl;

/**
 * El catalogo del diagnostico, codigo por codigo. Cada prueba parte del proceso de la demo, que cumple todas las
 * reglas, y rompe una sola cosa: lo que sale es el codigo que se espera y sobre el elemento que se rompio. Que el
 * diagrama sano no dispare ninguno lo sostiene la primera prueba, que es la otra mitad de cada codigo.
 */
@ExtendWith(MockitoExtension.class)
class DiagnosticoServiceTest {

    private static final Long EMPRESA = 1L;
    private static final Long PROCESO = 1L;

    @Mock
    private DiagramaService diagramaService;

    @InjectMocks
    private DiagnosticoServiceImpl diagnosticoService;

    @Test
    @DisplayName("El proceso de la demo cumple las reglas: el diagnostico no encuentra nada")
    void demo_sinHallazgos() {
        DiagnosticoResponse diagnostico = diagnosticar(DiagramaArmado.demo());

        assertThat(diagnostico.hallazgos()).isEmpty();
        assertThat(diagnostico.errores()).isZero();
        assertThat(diagnostico.advertencias()).isZero();
        assertThat(diagnostico.procesoId()).isEqualTo(PROCESO);
    }

    @Test
    @DisplayName("E-01: el pool de la tienda sin lanes no tiene donde poner el trabajo")
    void poolDeLaTiendaSinLanes_E01() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();

        assertThat(sobreQueElementos(armado, "E-01")).containsExactly(armado.id(TIENDA));
    }

    @Test
    @DisplayName("E-02: sin evento de inicio el proceso no tiene por donde empezar")
    void sinEventoDeInicio_E02() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(TIENDA, VENTAS);
        armado.actividad(VENTAS, "Receive order", TipoActividad.USUARIO);

        assertThat(sobreQueElementos(armado, "E-02")).containsExactly(armado.id(TIENDA));
    }

    @Test
    @DisplayName("E-03: un proceso que da vueltas sobre si mismo nunca llega a un fin")
    void sinFinAlcanzable_E03() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(TIENDA, VENTAS);
        armado.evento(VENTAS, "Order received", TipoEvento.INICIO);
        armado.actividad(VENTAS, "Receive order", TipoActividad.USUARIO);
        armado.actividad(VENTAS, "Review order", TipoActividad.USUARIO);
        armado.arco("Order received", "Receive order");
        armado.arco("Receive order", "Review order");
        armado.arco("Review order", "Receive order");

        assertThat(sobreQueElementos(armado, "E-03")).containsExactly(armado.id(TIENDA));
    }

    @Test
    @DisplayName("E-04: a un nodo al que no llega ningun flujo no se llega nunca")
    void nodoInalcanzable_E04() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.actividad(ALMACEN, "Print invoice", TipoActividad.USUARIO);
        armado.arco("Print invoice", "Order shipped");

        assertThat(sobreQueElementos(armado, "E-04")).containsExactly(armado.id("Print invoice"));
    }

    @Test
    @DisplayName("E-05: un nodo del que no sale nada, y que no es un fin, deja el caso parado")
    void nodoSinSalida_E05() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.actividad(VENTAS, "Print invoice", TipoActividad.USUARIO);
        armado.arco("Receive order", "Print invoice");

        assertThat(sobreQueElementos(armado, "E-05")).containsExactly(armado.id("Print invoice"));
    }

    @Test
    @DisplayName("E-06: un gateway con una sola entrada y una sola salida no decide nada")
    void gatewayQueNoDecideNada_E06() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.gateway(ALMACEN, "Ready to ship?", TipoGateway.PARALELO);
        armado.quitarSalidasDe("Pick and pack items");
        armado.arco("Pick and pack items", "Ready to ship?");
        armado.arco("Ready to ship?", "Ship order");

        assertThat(sobreQueElementos(armado, "E-06")).containsExactly(armado.id("Ready to ship?"));
    }

    @Test
    @DisplayName("E-07: una salida de un gateway que decide, sin condicion y sin ser la por defecto")
    void salidaDeGatewaySinCondicion_E07() {
        DiagramaArmado armado = conRamaDeEspera();
        armado.arco("Payment approved?", "Hold order");

        assertThat(sobreQueElementos(armado, "E-07")).hasSize(1);
    }

    @Test
    @DisplayName("E-08: una condicion a medio escribir no la entenderia el motor")
    void condicionQueNoCompila_E08() {
        DiagramaArmado armado = conRamaDeEspera();
        armado.arcoCon("Payment approved?", "Hold order", "payment.status ==");

        List<HallazgoDiagnosticoResponse> hallazgos = hallazgos(armado, "E-08");

        assertThat(hallazgos).hasSize(1);
        assertThat(hallazgos.getFirst().problema()).contains("Falta con que comparar");
    }

    @Test
    @DisplayName("E-09: a un inicio no le llega ningun flujo, y de un fin no sale ninguno")
    void eventosMalConectados_E09() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.arco("Receive order", "Order received");
        armado.arco("Order cancelled", "Receive order");

        assertThat(sobreQueElementos(armado, "E-09"))
                .containsExactly(armado.id("Order received"), armado.id("Order cancelled"));
    }

    @Test
    @DisplayName("E-14: un gateway que une varios caminos y los vuelve a repartir hace dos cosas a la vez")
    void gatewayQueUneYReparte_E14() {
        DiagramaArmado armado = conRamaDeEspera();
        armado.gateway(VENTAS, "Continue?", TipoGateway.PARALELO);
        armado.arco("Receive order", "Continue?");
        armado.arco("Hold order", "Continue?");
        armado.arco("Continue?", "Request payment authorization");
        armado.arco("Continue?", "Cancel order");

        assertThat(sobreQueElementos(armado, "E-14")).containsExactly(armado.id("Continue?"));
    }

    @Test
    @DisplayName("E-15: un caso se abre por un solo inicio sin mensaje")
    void variosIniciosSinMensaje_E15() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.evento(VENTAS, "Manual start", TipoEvento.INICIO);
        armado.evento(VENTAS, "Backfill start", TipoEvento.INICIO);
        armado.arco("Manual start", "Receive order");
        armado.arco("Backfill start", "Receive order");

        assertThat(sobreQueElementos(armado, "E-15")).containsExactly(armado.id(TIENDA));
    }

    @Test
    @DisplayName("A-05: un gateway exclusivo sin salida por defecto puede dejar al caso sin camino")
    void gatewayExclusivoSinSalidaPorDefecto_A05() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.quitarSalidasDe("Payment approved?");
        armado.arcoCon("Payment approved?", "Pick and pack items", "payment.status == APPROVED");
        armado.arcoCon("Payment approved?", "Cancel order", "payment.status == DECLINED");

        List<HallazgoDiagnosticoResponse> hallazgos = hallazgos(armado, "A-05");

        assertThat(hallazgos).hasSize(1);
        assertThat(hallazgos.getFirst().elementoId()).isEqualTo(armado.id("Payment approved?"));
        assertThat(hallazgos.getFirst().severidad()).isEqualTo(Severidad.MEDIA);
    }

    @Test
    @DisplayName("A-05: dos salidas con la misma condicion dejan a la segunda sin usarse nunca")
    void dosSalidasConLaMismaCondicion_A05() {
        DiagramaArmado armado = conRamaDeEspera();
        armado.quitarSalidasDe("Payment approved?");
        armado.arcoCon("Payment approved?", "Pick and pack items", "payment.status == APPROVED");
        armado.arcoCon("Payment approved?", "Cancel order", "payment.status == APPROVED");
        armado.arcoPorDefecto("Payment approved?", "Hold order");

        List<HallazgoDiagnosticoResponse> hallazgos = hallazgos(armado, "A-05");

        assertThat(hallazgos).hasSize(1);
        assertThat(hallazgos.getFirst().problema()).contains("la misma condicion");
    }

    @Test
    @DisplayName("A-14: un gateway inclusivo sin salida por defecto se avisa, pero no bloquea publicar")
    void gatewayInclusivoSinSalidaPorDefecto_A14() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.gateway(VENTAS, "Notify?", TipoGateway.INCLUSIVO);
        armado.actividad(VENTAS, "Notify customer", TipoActividad.USUARIO);
        armado.actividad(VENTAS, "Notify warehouse", TipoActividad.USUARIO);
        armado.arco("Receive order", "Notify?");
        armado.arcoCon("Notify?", "Notify customer", "order.notifyCustomer == true");
        armado.arcoCon("Notify?", "Notify warehouse", "order.notifyWarehouse == true");
        armado.arco("Notify customer", "Order cancelled");
        armado.arco("Notify warehouse", "Order cancelled");

        List<HallazgoDiagnosticoResponse> hallazgos = hallazgos(armado, "A-14");

        assertThat(hallazgos).hasSize(1);
        assertThat(hallazgos.getFirst().severidad()).isEqualTo(Severidad.BAJA);
    }

    @Test
    @DisplayName("A-10: una lane sin nodos no dice nada del proceso")
    void laneSinNodos_A10() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.lane(TIENDA, "Returns");

        assertThat(sobreQueElementos(armado, "A-10")).containsExactly(armado.id("Returns"));
    }

    @Test
    @DisplayName("Los errores van antes que las advertencias, y el conteo separa unos de otras")
    void hallazgos_ordenadosPorGravedad() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.lane(TIENDA, "Returns");
        armado.actividad(VENTAS, "Print invoice", TipoActividad.USUARIO);
        armado.arco("Receive order", "Print invoice");

        DiagnosticoResponse diagnostico = diagnosticar(armado);

        assertThat(diagnostico.hallazgos()).extracting(HallazgoDiagnosticoResponse::codigo)
                .containsExactly("E-05", "A-10");
        assertThat(diagnostico.errores()).isEqualTo(1);
        assertThat(diagnostico.advertencias()).isEqualTo(1);
    }

    /** La demo con una rama de espera mas, para colgar de ella lo que cada prueba quiere romper. */
    private static DiagramaArmado conRamaDeEspera() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.actividad(VENTAS, "Hold order", TipoActividad.USUARIO);
        armado.arco("Hold order", "Order cancelled");
        return armado;
    }

    private DiagnosticoResponse diagnosticar(DiagramaArmado armado) {
        when(diagramaService.obtener(EMPRESA, PROCESO)).thenReturn(armado.diagrama());
        return diagnosticoService.diagnosticar(EMPRESA, PROCESO);
    }

    private List<HallazgoDiagnosticoResponse> hallazgos(DiagramaArmado armado, String codigo) {
        return diagnosticar(armado).hallazgos().stream()
                .filter(hallazgo -> hallazgo.codigo().equals(codigo))
                .toList();
    }

    private List<Long> sobreQueElementos(DiagramaArmado armado, String codigo) {
        return hallazgos(armado, codigo).stream().map(HallazgoDiagnosticoResponse::elementoId).toList();
    }
}
