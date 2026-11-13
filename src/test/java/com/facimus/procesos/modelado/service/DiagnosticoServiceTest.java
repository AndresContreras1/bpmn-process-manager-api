package com.facimus.procesos.modelado.service;

import static com.facimus.procesos.modelado.service.DiagramaArmado.ALMACEN;
import static com.facimus.procesos.modelado.service.DiagramaArmado.TIENDA;
import static com.facimus.procesos.modelado.service.DiagramaArmado.VENTAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.formularios.CampoArmado;
import com.facimus.procesos.common.formularios.TipoDeCampo;
import com.facimus.procesos.gestion.model.ModoProceso;
import com.facimus.procesos.modelado.dto.response.ArcoResponse;
import com.facimus.procesos.modelado.dto.response.DiagnosticoResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.HallazgoDiagnosticoResponse;
import com.facimus.procesos.modelado.model.AccionSiFalla;
import com.facimus.procesos.modelado.model.DireccionAsociacion;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.PoliticaSinCaso;
import com.facimus.procesos.modelado.model.Severidad;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoArtefacto;
import com.facimus.procesos.modelado.model.TipoAsociacion;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.model.TipoTemporizador;
import com.facimus.procesos.modelado.service.impl.DiagnosticoServiceImpl;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;

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

    @Spy
    private TestObservationRegistry observaciones = TestObservationRegistry.create();

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
    @DisplayName("Cada diagnostico es un span con el proceso y para que se pidio")
    void diagnostico_seObservaConElProceso() {
        diagnosticar(DiagramaArmado.demo());

        TestObservationRegistryAssert.assertThat(observaciones)
                .hasObservationWithNameEqualTo("procesos.diagnostico").that()
                .hasBeenStopped()
                .hasContextualNameEqualTo("diagnosticar el diagrama")
                .hasLowCardinalityKeyValue("uso", "consulta")
                .hasHighCardinalityKeyValue("proceso.id", String.valueOf(PROCESO));
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
    @DisplayName("E-15: un unico inicio sin mensaje es lo normal, y no se avisa")
    void unSoloInicioSinMensaje_noAvisa() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.evento(VENTAS, "Manual start", TipoEvento.INICIO);
        armado.arco("Manual start", "Receive order");

        assertThat(sobreQueElementos(armado, "E-15")).isEmpty();
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
    @DisplayName("A-13: el diagrama que se esta editando tiene cambios que la version publicada no incluye")
    void borradorConCambiosSinPublicar_A13() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.conCambiosSinPublicar(2);

        List<HallazgoDiagnosticoResponse> hallazgos = hallazgos(armado, "A-13");

        assertThat(hallazgos).hasSize(1);
        assertThat(hallazgos.getFirst().severidad()).isEqualTo(Severidad.BAJA);
        assertThat(hallazgos.getFirst().problema()).contains("la version 2");
        assertThat(hallazgos.getFirst().elementoId()).isNull();
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

    @Test
    @DisplayName("E-10: un evento que espera un mensaje sin ningun mensaje anclado no arranca nunca")
    void eventoQueEsperaSinMensaje_E10() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.evento(VENTAS, "Invoice received", TipoEvento.MENSAJE_INTERMEDIO);
        armado.arco("Receive order", "Invoice received");
        armado.arco("Invoice received", "Order cancelled");

        assertThat(sobreQueElementos(armado, "E-10")).containsExactly(armado.id("Invoice received"));
    }

    @Test
    @DisplayName("E-11: una actividad de envio sin mensaje anclado no manda nada")
    void actividadDeEnvioSinMensaje_E11() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.actividad(VENTAS, "Send invoice", TipoActividad.ENVIO);
        armado.arco("Receive order", "Send invoice");
        armado.arco("Send invoice", "Order cancelled");

        assertThat(sobreQueElementos(armado, "E-11")).containsExactly(armado.id("Send invoice"));
    }

    @Test
    @DisplayName("E-12: el nodo anclado tiene que estar en el pool del lado por el que sale el mensaje")
    void mensajeAncladoFueraDeSuPool_E12() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.mensaje("Invoice sent", "Customer", TIENDA, "Cancel order", "Order received", null,
                AccionSiFalla.CONTINUAR, null, false);

        assertThat(sobreQueElementos(armado, "E-12")).containsExactly(armado.id("Invoice sent"));
    }

    @Test
    @DisplayName("E-13: entre dos participantes modelados por dentro el mensaje dice por donde sale y por donde entra")
    void mensajeEntreDosPoolsModeladosSinAnclar_E13() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.dejaDeSerCajaNegra("Customer");
        armado.lane("Customer", "Purchasing");
        armado.actividad("Purchasing", "Place order", TipoActividad.USUARIO);

        // Los dos mensajes entre la tienda y el cliente pasan a necesitar sus dos anclajes.
        assertThat(sobreQueElementos(armado, "E-13"))
                .containsExactly(armado.id("Order placed"), armado.id("Order status notification"));
    }

    @Test
    @DisplayName("A-01: un mensaje sin anclar hacia un participante modelado puede quedarse sin receptor")
    void mensajeSinReceptor_A01() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.desanclarDestinoDe("Shipment confirmation");

        assertThat(sobreQueElementos(armado, "A-01")).containsExactly(armado.id("Shipment confirmation"));
    }

    @Test
    @DisplayName("A-02: un mensaje que el proceso espera y que nadie manda se queda sin llegar")
    void mensajeQueNadieManda_A02() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.evento(VENTAS, "Stock checked", TipoEvento.MENSAJE_INTERMEDIO);
        armado.arco("Receive order", "Stock checked");
        armado.arco("Stock checked", "Order cancelled");
        armado.mensaje("Stock check result", "Carrier", TIENDA, null, "Stock checked", null,
                AccionSiFalla.CONTINUAR, null, false);
        armado.correlacion("Stock check result", PoliticaSinCaso.DESCARTAR);

        assertThat(sobreQueElementos(armado, "A-02")).containsExactly(armado.id("Stock check result"));
    }

    @Test
    @DisplayName("A-03: un mensaje que se espera en mitad del flujo sin clave no encuentra su caso")
    void mensajeIntermedioSinClave_A03() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.quitarCorrelacionDe("Payment authorization result");

        assertThat(sobreQueElementos(armado, "A-03"))
                .containsExactly(armado.id("Payment authorization result"));
    }

    @Test
    @DisplayName("A-04: dos mensajes con el mismo nombre y la misma clave no se distinguen al llegar")
    void mensajesConElMismoNombreYClave_A04() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.duplicarMensaje("Shipment request");

        assertThat(sobreQueElementos(armado, "A-04")).containsExactly(armado.id("Shipment request"));
    }

    @Test
    @DisplayName("A-07: una clave que no dice en que campo viaja no se puede correlacionar")
    void correlacionSinCampo_A07() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.quitarCampoDeCorrelacionDe("Order placed");

        assertThat(sobreQueElementos(armado, "A-07")).containsExactly(armado.id("Order placed"));
    }

    @Test
    @DisplayName("A-08: un mensaje a un sistema externo dice por donde viaja")
    void mensajeASistemaExternoSinDestino_A08() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.sinTipoDestino("Payment authorization request");

        assertThat(sobreQueElementos(armado, "A-08"))
                .containsExactly(armado.id("Payment authorization request"));
    }

    @Test
    @DisplayName("A-09: un socio que nunca contesta deja al simulador sin nada que responder")
    void socioQueNuncaResponde_A09() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.quitarMensaje("Shipment confirmation");

        assertThat(sobreQueElementos(armado, "A-09")).containsExactly(armado.id("Carrier"));
    }

    @Test
    @DisplayName("A-11: un participante que no intercambia nada no pinta nada en el proceso")
    void participanteSinMensajes_A11() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.pool("Supplier", TipoParticipante.PROVEEDOR, true, Integracion.NINGUNA);

        assertThat(sobreQueElementos(armado, "A-11")).containsExactly(armado.id("Supplier"));
    }

    @Test
    @DisplayName("A-12: una actividad de servicio sin mensaje se completa sola, sin efecto")
    void actividadDeServicioSinMensaje_A12() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.quitarMensaje("Order status notification");

        assertThat(sobreQueElementos(armado, "A-12")).containsExactly(armado.id("Cancel order"));
    }

    @Test
    @DisplayName("A-06: borrar el gateway se lleva sus flujos y deja media tienda sin camino")
    void borrarUnGateway_diceQueSeVaYQueSeRompe_A06() {
        DiagramaArmado armado = DiagramaArmado.demo();

        DiagnosticoResponse diagnostico = diagnosticar(armado, "GATEWAY:" + armado.id("Payment approved?"));

        assertThat(diagnostico.sinElemento()).isEqualTo("GATEWAY:" + armado.id("Payment approved?"));
        assertThat(codigos(diagnostico, "A-06")).containsExactly(
                armado.arcoEntre("Payment result received", "Payment approved?"),
                armado.arcoEntre("Payment approved?", "Pick and pack items"),
                armado.arcoEntre("Payment approved?", "Cancel order"));
        assertThat(codigos(diagnostico, "E-04"))
                .contains(armado.id("Cancel order"), armado.id("Pick and pack items"));
    }

    @Test
    @DisplayName("Borrar un flujo no se lleva nada mas, pero deja sin camino lo que iba detras")
    void borrarUnArco_noSeLlevaNadaMas() {
        DiagramaArmado armado = DiagramaArmado.demo();

        DiagnosticoResponse diagnostico = diagnosticar(armado,
                "ARCO:" + armado.arcoEntre("Pick and pack items", "Ship order"));

        assertThat(codigos(diagnostico, "A-06")).isEmpty();
        assertThat(codigos(diagnostico, "E-04")).contains(armado.id("Ship order"));
        assertThat(codigos(diagnostico, "E-05")).contains(armado.id("Pick and pack items"));
    }

    @Test
    @DisplayName("Borrar un participante se lleva sus mensajes, y el nodo que los esperaba se queda sin ninguno")
    void borrarUnPool_seLlevaSusMensajes() {
        DiagramaArmado armado = DiagramaArmado.demo();

        DiagnosticoResponse diagnostico = diagnosticar(armado, "POOL:" + armado.id("Carrier"));

        assertThat(codigos(diagnostico, "A-06"))
                .containsExactly(armado.id("Shipment request"), armado.id("Shipment confirmation"));
        assertThat(codigos(diagnostico, "E-10")).contains(armado.id("Shipment confirmed"));
        assertThat(codigos(diagnostico, "E-11")).contains(armado.id("Ship order"));
    }

    @Test
    @DisplayName("Un elemento que no se entiende se rechaza antes de mirar el diagrama")
    void sinElementoMalEscrito_lanzaReglaNegocio() {
        assertThatThrownBy(() -> diagnosticoService.diagnosticar(EMPRESA, PROCESO, "GATEWAY-12"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("TIPO:id");
        verifyNoInteractions(diagramaService);
    }

    /** La demo con una rama de espera mas, para colgar de ella lo que cada prueba quiere romper. */
    private static DiagramaArmado conRamaDeEspera() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.actividad(VENTAS, "Hold order", TipoActividad.USUARIO);
        armado.arco("Hold order", "Order cancelled");
        return armado;
    }

    private DiagnosticoResponse diagnosticar(DiagramaArmado armado, String sinElemento) {
        when(diagramaService.obtener(EMPRESA, PROCESO)).thenReturn(armado.diagrama());
        return diagnosticoService.diagnosticar(EMPRESA, PROCESO, sinElemento);
    }

    private static List<Long> codigos(DiagnosticoResponse diagnostico, String codigo) {
        return diagnostico.hallazgos().stream()
                .filter(hallazgo -> hallazgo.codigo().equals(codigo))
                .map(HallazgoDiagnosticoResponse::elementoId)
                .toList();
    }

    @Test
    @DisplayName("E-16: un temporizador sin expresion, o con una que no es ISO 8601")
    void e16_temporizadorSinCuando() {
        DiagramaArmado armado = DiagramaArmado.demo();
        Long sinNada = armado.temporizador(DiagramaArmado.VENTAS, "Sin nada", TipoEvento.TEMPORIZADOR_INTERMEDIO, null,
                null, null);
        Long mal = armado.temporizador(DiagramaArmado.VENTAS, "Mal", TipoEvento.TEMPORIZADOR_INTERMEDIO,
                TipoTemporizador.DURACION, "two hours", null);
        Long bien = armado.temporizador(DiagramaArmado.VENTAS, "Bien", TipoEvento.TEMPORIZADOR_INTERMEDIO,
                TipoTemporizador.DURACION, "PT2H", null);

        List<Long> marcados = codigos(diagnosticar(armado), "E-16");

        assertThat(marcados).containsExactlyInAnyOrder(sinNada, mal).doesNotContain(bien);
    }

    @Test
    @DisplayName("E-17: una tarea sin tipo no puede correr")
    void e17_tareaSinTipo() {
        DiagramaArmado armado = DiagramaArmado.demo();
        Long tarea = armado.actividad(DiagramaArmado.VENTAS, "Sin tipo", TipoActividad.SIN_TIPO);

        assertThat(codigos(diagnosticar(armado), "E-17")).containsExactly(tarea);
    }

    @Test
    @DisplayName("E-18: un ciclo en una espera o en un borde que interrumpe, que solo pueden saltar una vez")
    void e18_cicloQueSaltaUnaVez() {
        DiagramaArmado armado = DiagramaArmado.demo();
        Long espera = armado.temporizador(DiagramaArmado.VENTAS, "Espera", TipoEvento.TEMPORIZADOR_INTERMEDIO,
                TipoTemporizador.CICLO, "R3/PT1H", null);
        Long interrumpe = armado.temporizador(DiagramaArmado.VENTAS, "Corta", TipoEvento.TEMPORIZADOR_BORDE,
                TipoTemporizador.CICLO, "R3/PT1H", "Receive order");
        armado.bordeQueNoInterrumpe(DiagramaArmado.VENTAS, "Avisa", TipoTemporizador.CICLO, "R3/PT1H",
                "Receive order");
        armado.temporizador(DiagramaArmado.VENTAS, "Cada lunes", TipoEvento.TEMPORIZADOR_INICIO,
                TipoTemporizador.CICLO, "0 0 9 * * MON", null);
        armado.temporizador(DiagramaArmado.VENTAS, "Dos dias", TipoEvento.TEMPORIZADOR_INTERMEDIO,
                TipoTemporizador.DURACION_HABIL, "P2D", null);

        assertThat(codigos(diagnosticar(armado), "E-18")).containsExactlyInAnyOrder(espera, interrumpe);
    }

    @Test
    @DisplayName("E-16: una duracion habil cuenta dias y horas; un mes habil no se entiende")
    void e16_duracionHabil() {
        DiagramaArmado armado = DiagramaArmado.demo();
        Long mes = armado.temporizador(DiagramaArmado.VENTAS, "Un mes", TipoEvento.TEMPORIZADOR_INTERMEDIO,
                TipoTemporizador.DURACION_HABIL, "P1M", null);
        armado.temporizador(DiagramaArmado.VENTAS, "Dos dias", TipoEvento.TEMPORIZADOR_INTERMEDIO,
                TipoTemporizador.DURACION_HABIL, "P2DT4H", null);

        assertThat(codigos(diagnosticar(armado), "E-16")).containsExactly(mes);
    }

    @Test
    @DisplayName("E-09: un evento de borde sin actividad, o con flujos que le llegan")
    void e09_eventoDeBorde() {
        DiagramaArmado armado = DiagramaArmado.demo();
        Long suelto = armado.temporizador(DiagramaArmado.VENTAS, "Suelto", TipoEvento.TEMPORIZADOR_BORDE,
                TipoTemporizador.DURACION, "PT2H", null);

        assertThat(codigos(diagnosticar(armado), "E-09")).containsExactly(suelto);
    }

    @Test
    @DisplayName("A un evento de borde se llega por su actividad: no es un nodo inalcanzable")
    void borde_seAlcanzaPorSuActividad() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(DiagramaArmado.TIENDA, "Sales");
        armado.evento("Sales", "Start", TipoEvento.INICIO);
        armado.actividad("Sales", "Work", TipoActividad.USUARIO);
        armado.evento("Sales", "End", TipoEvento.FIN);
        Long borde = armado.temporizador("Sales", "Late", TipoEvento.TEMPORIZADOR_BORDE, TipoTemporizador.DURACION,
                "PT2H", "Work");
        armado.evento("Sales", "Escalated", TipoEvento.FIN);
        armado.arco("Start", "Work");
        armado.arco("Work", "End");
        armado.arco("Late", "Escalated");

        assertThat(codigos(diagnosticar(armado), "E-04")).doesNotContain(borde, armado.id("Escalated"));
    }

    @Test
    @DisplayName("A-15: una asociacion con un extremo que ya no esta")
    void a15_asociacionColgando() {
        DiagramaArmado armado = DiagramaArmado.demo();
        String nota = armado.artefacto(null, null, "Nota", TipoArtefacto.ANOTACION, "Hello");
        armado.asociacion(TipoAsociacion.ASOCIACION, nota, "Activity_9999", DireccionAsociacion.NINGUNA);

        List<HallazgoDiagnosticoResponse> hallazgos = hallazgos(armado, "A-15");

        assertThat(hallazgos).singleElement()
                .satisfies(hallazgo -> assertThat(hallazgo.problema()).contains("Activity_9999"));
    }

    @Test
    @DisplayName("Un proceso documental solo cuenta lo estructural: lo que no puede correr no es un error")
    void documental_soloLoEstructural() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(DiagramaArmado.TIENDA, "Sales");
        armado.actividad("Sales", "Sin tipo", TipoActividad.SIN_TIPO);
        armado.temporizador("Sales", "Espera", TipoEvento.TEMPORIZADOR_INTERMEDIO, null, null, null);
        armado.modo(ModoProceso.DOCUMENTAL);

        DiagnosticoResponse diagnostico = diagnosticar(armado);

        assertThat(diagnostico.errores()).isZero();
        assertThat(diagnostico.hallazgos()).extracting(HallazgoDiagnosticoResponse::codigo)
                .doesNotContain("E-02", "E-03", "E-04", "E-05", "E-16", "E-17", "E-18");
    }

    @Test
    @DisplayName("Lo estructural sigue contando en un proceso documental")
    void documental_loEstructuralCuenta() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(DiagramaArmado.TIENDA, "Sales");
        Long inicio = armado.evento("Sales", "Start", TipoEvento.INICIO);
        armado.actividad("Sales", "Work", TipoActividad.SIN_TIPO);
        armado.arco("Work", "Start");
        armado.modo(ModoProceso.DOCUMENTAL);

        assertThat(codigos(diagnosticar(armado), "E-09")).containsExactly(inicio);
    }

    @Test
    @DisplayName("Simular el borrado de una actividad se lleva sus eventos de borde y sus asociaciones")
    void simular_seLlevaLoPegado() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(DiagramaArmado.TIENDA, "Sales");
        Long tarea = armado.actividad("Sales", "Work", TipoActividad.USUARIO);
        Long borde = armado.temporizador("Sales", "Late", TipoEvento.TEMPORIZADOR_BORDE, TipoTemporizador.DURACION,
                "PT2H", "Work");
        String nota = armado.artefacto(null, null, "Nota", TipoArtefacto.ANOTACION, "Hello");
        armado.asociacion(TipoAsociacion.ASOCIACION, nota, "Activity_" + tarea, DireccionAsociacion.NINGUNA);

        DiagnosticoResponse diagnostico = diagnosticar(armado, "ACTIVIDAD:" + tarea);

        assertThat(codigos(diagnostico, "A-06")).contains(borde);
        assertThat(codigos(diagnostico, "A-15")).isEmpty();
    }

    @Test
    @DisplayName("E-19: un subproceso no corre en un proceso ejecutable, y en uno documental si vale")
    void e19_subproceso() {
        DiagramaArmado armado = DiagramaArmado.demo();
        Long subproceso = armado.actividadEn(DiagramaArmado.VENTAS, "Inside", TipoActividad.SUBPROCESO, null, null);

        assertThat(codigos(diagnosticar(armado), "E-19")).containsExactly(subproceso);

        armado.modo(ModoProceso.DOCUMENTAL);
        assertThat(codigos(diagnosticar(armado), "E-19")).isEmpty();
    }

    @Test
    @DisplayName("E-20 y A-16: una llamada sin proceso es un error; con proceso avisa de que todavia no lo corre")
    void llamadas() {
        DiagramaArmado armado = DiagramaArmado.demo();
        Long sinProceso = armado.actividadEn(DiagramaArmado.VENTAS, "Nothing", TipoActividad.LLAMADA, null, null);
        Long conProceso = armado.actividadEn(DiagramaArmado.VENTAS, "Something", TipoActividad.LLAMADA, null, 7L);

        DiagnosticoResponse diagnostico = diagnosticar(armado);

        assertThat(codigos(diagnostico, "E-20")).containsExactly(sinProceso);
        assertThat(codigos(diagnostico, "A-16")).containsExactly(conProceso);
    }

    @Test
    @DisplayName("Lo de dentro de un subproceso no cuenta para los caminos del flujo principal")
    void contenido_noCuentaEnElFlujoPrincipal() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.actividadEn(DiagramaArmado.VENTAS, "Inside", TipoActividad.SUBPROCESO, null, null);
        Long hijo = armado.actividadEn(DiagramaArmado.VENTAS, "Child", TipoActividad.USUARIO, "Inside", null);

        DiagnosticoResponse diagnostico = diagnosticar(armado);

        assertThat(codigos(diagnostico, "E-04")).doesNotContain(hijo);
        assertThat(codigos(diagnostico, "E-05")).doesNotContain(hijo);
    }

    @Test
    @DisplayName("Simular el borrado de un subproceso se lleva lo que tiene dentro, a cualquier profundidad")
    void simular_seLlevaElContenido() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(DiagramaArmado.TIENDA, "Sales");
        Long fuera = armado.actividadEn("Sales", "Outer", TipoActividad.SUBPROCESO, null, null);
        Long medio = armado.actividadEn("Sales", "Middle", TipoActividad.SUBPROCESO, "Outer", null);
        Long dentro = armado.eventoEn("Sales", "Deep", TipoEvento.FIN, "Middle");

        DiagnosticoResponse diagnostico = diagnosticar(armado, "ACTIVIDAD:" + fuera);

        assertThat(codigos(diagnostico, "A-06")).contains(medio, dentro);
    }

    @Test
    @DisplayName("A-06: un flujo que se va se avisa aunque su id sea el de una lane que se queda")
    void a06_idsDeOtraTabla() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        Long lane = armado.lane(DiagramaArmado.TIENDA, "Sales");
        Long uno = armado.actividad("Sales", "One", TipoActividad.USUARIO);
        armado.actividad("Sales", "Two", TipoActividad.USUARIO);
        armado.arco("One", "Two");
        DiagramaResponse diagrama = armado.diagrama();
        ArcoResponse arco = diagrama.arcos().getFirst();
        ArcoResponse conElIdDeLaLane = new ArcoResponse(lane, arco.bpmnId(), arco.etiqueta(), arco.condicion(),
                arco.porDefecto(), arco.orden(), arco.origenId(), arco.destinoId(), arco.poolId(), arco.version(), null,
                null, null, null);
        when(diagramaService.obtener(EMPRESA, PROCESO)).thenReturn(new DiagramaResponse(diagrama.proceso(), false,
                diagrama.pools(), diagrama.lanes(), diagrama.actividades(), diagrama.gateways(), diagrama.eventos(),
                List.of(conElIdDeLaLane), diagrama.mensajes(), diagrama.correlaciones()));

        DiagnosticoResponse diagnostico = diagnosticoService.diagnosticar(EMPRESA, PROCESO, "ACTIVIDAD:" + uno);

        assertThat(codigos(diagnostico, "A-06")).containsExactly(lane);
    }

    private DiagnosticoResponse diagnosticar(DiagramaArmado armado) {
        return diagnosticar(armado, null);
    }

    private List<HallazgoDiagnosticoResponse> hallazgos(DiagramaArmado armado, String codigo) {
        return diagnosticar(armado).hallazgos().stream()
                .filter(hallazgo -> hallazgo.codigo().equals(codigo))
                .toList();
    }

    private List<Long> sobreQueElementos(DiagramaArmado armado, String codigo) {
        return hallazgos(armado, codigo).stream().map(HallazgoDiagnosticoResponse::elementoId).toList();
    }

    @Test
    @DisplayName("E-22: un formulario mal armado, con la propiedad y su problema; uno bien armado no dice nada")
    void e22_formularioMalArmado() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.formulario("Pick and pack items", CampoArmado.formulario(
                CampoArmado.campo("cajas", TipoDeCampo.NUMERO).minimo("1").requerido()));
        armado.formulario("Receive order", CampoArmado.formulario(
                CampoArmado.campo("total", TipoDeCampo.NUMERO).minimo("10").maximo("1"),
                CampoArmado.campo("nota", TipoDeCampo.TEXTO).visibleSi("total >")));

        DiagnosticoResponse diagnostico = diagnosticar(armado);

        assertThat(codigos(diagnostico, "E-22")).containsExactly(armado.id("Receive order"),
                armado.id("Receive order"));
        assertThat(diagnostico.hallazgos()).filteredOn(hallazgo -> hallazgo.codigo().equals("E-22"))
                .extracting(HallazgoDiagnosticoResponse::problema)
                .containsExactlyInAnyOrder(
                        "El formulario no esta bien armado: campos[0].minimo: El minimo no puede ser mayor que el "
                                + "maximo.",
                        "El formulario no esta bien armado: campos[1].visibleSi: Falta con que comparar \"total\".");
        assertThat(diagnostico.hallazgos()).filteredOn(hallazgo -> hallazgo.codigo().equals("E-22"))
                .allSatisfy(hallazgo -> {
                    assertThat(hallazgo.elemento()).isEqualTo("Actividad \"Receive order\"");
                    assertThat(hallazgo.severidad()).isEqualTo(Severidad.ALTA);
                });
    }

    @Test
    @DisplayName("E-22: un formulario en un nodo que no llena una persona")
    void e22_formularioDondeNadieLoLlena() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.formulario("Cancel order", CampoArmado.formulario(CampoArmado.campo("motivo", TipoDeCampo.TEXTO)));
        armado.formulario("Order received", CampoArmado.formulario(CampoArmado.campo("pedido", TipoDeCampo.TEXTO)));

        DiagnosticoResponse diagnostico = diagnosticar(armado);

        assertThat(codigos(diagnostico, "E-22")).containsExactlyInAnyOrder(armado.id("Cancel order"),
                armado.id("Order received"));
        assertThat(diagnostico.hallazgos()).filteredOn(hallazgo -> hallazgo.codigo().equals("E-22"))
                .extracting(HallazgoDiagnosticoResponse::elemento)
                .containsExactlyInAnyOrder("Actividad \"Cancel order\"", "Evento \"Order received\"");
    }

    @Test
    @DisplayName("E-22: un inicio simple si lleva formulario")
    void e22_inicioSimple() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(TIENDA, VENTAS);
        armado.evento(VENTAS, "Start", TipoEvento.INICIO);
        armado.actividad(VENTAS, "Work", TipoActividad.USUARIO);
        armado.evento(VENTAS, "End", TipoEvento.FIN);
        armado.arco("Start", "Work");
        armado.arco("Work", "End");
        armado.formulario("Start", CampoArmado.formulario(CampoArmado.campo("pedido", TipoDeCampo.TEXTO)));

        assertThat(codigos(diagnosticar(armado), "E-22")).isEmpty();
    }

    @Test
    @DisplayName("E-22 no cuenta en un proceso documental, que no corre")
    void e22_documental() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.modo(ModoProceso.DOCUMENTAL);
        armado.formulario("Cancel order", CampoArmado.formulario(CampoArmado.campo("motivo", TipoDeCampo.TEXTO)));

        assertThat(codigos(diagnosticar(armado), "E-22")).isEmpty();
    }
}
