package com.facimus.procesos.modelado.service.impl;

import static com.facimus.procesos.modelado.service.DiagramaArmado.ALMACEN;
import static com.facimus.procesos.modelado.service.DiagramaArmado.TIENDA;
import static com.facimus.procesos.modelado.service.DiagramaArmado.VENTAS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.ArcoResponse;
import com.facimus.procesos.modelado.dto.response.ArtefactoResponse;
import com.facimus.procesos.modelado.dto.response.AsociacionResponse;
import com.facimus.procesos.modelado.dto.response.CorrelacionResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.dto.response.HallazgoDiagnosticoResponse;
import com.facimus.procesos.modelado.dto.response.LaneResponse;
import com.facimus.procesos.modelado.dto.response.MensajeResponse;
import com.facimus.procesos.modelado.dto.response.PoolResponse;
import com.facimus.procesos.modelado.model.DireccionAsociacion;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoArtefacto;
import com.facimus.procesos.modelado.model.TipoAsociacion;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.service.DiagramaArmado;

/**
 * HU-10.3 y HU-13.3 sin la base: el diagrama que quedaria al borrar un elemento, con el mismo arrastre que el borrado
 * real, y el aviso A-06 de lo que se iria ademas del elemento por el que se pregunta. Sobre el proceso de la demo.
 */
class SimulacionDeBorradoTest {

    private final DiagramaArmado armado = DiagramaArmado.demo();

    @Test
    @DisplayName("Sin un pool externo se van sus mensajes y sus correlaciones, y lo demas queda igual")
    void sinPoolExterno() {
        DiagramaResponse antes = armado.diagrama();

        DiagramaResponse despues = sin(ElementoDelDiagrama.Tipo.POOL, "Payment gateway");

        assertThat(despues.pools()).extracting(PoolResponse::nombre)
                .containsExactly(TIENDA, "Customer", "Carrier");
        assertThat(nombresDeMensajes(despues)).containsExactly("Order placed", "Shipment request",
                "Shipment confirmation", "Order status notification");
        assertThat(despues.correlaciones()).extracting(CorrelacionResponse::mensajeId)
                .doesNotContain(armado.id("Payment authorization request"), armado.id("Payment authorization result"))
                .hasSize(4);
        assertThat(despues.lanes()).isEqualTo(antes.lanes());
        assertThat(despues.actividades()).isEqualTo(antes.actividades());
        assertThat(despues.arcos()).isEqualTo(antes.arcos());
        assertThat(despues.proceso()).isEqualTo(antes.proceso());
    }

    @Test
    @DisplayName("Sin el pool de la tienda se van sus lanes, sus nodos, sus flujos y todos sus mensajes")
    void sinPoolDeLaTienda() {
        DiagramaResponse despues = sin(ElementoDelDiagrama.Tipo.POOL, TIENDA);

        assertThat(despues.pools()).extracting(PoolResponse::nombre)
                .containsExactly("Customer", "Payment gateway", "Carrier");
        assertThat(despues.lanes()).isEmpty();
        assertThat(despues.actividades()).isEmpty();
        assertThat(despues.gateways()).isEmpty();
        assertThat(despues.eventos()).isEmpty();
        assertThat(despues.arcos()).isEmpty();
        assertThat(despues.mensajes()).isEmpty();
        assertThat(despues.correlaciones()).isEmpty();
    }

    @Test
    @DisplayName("Sin una lane se van sus nodos y los flujos que los tocan; los mensajes quedan sin ese anclaje")
    void sinLane() {
        DiagramaResponse antes = armado.diagrama();

        DiagramaResponse despues = sin(ElementoDelDiagrama.Tipo.LANE, ALMACEN);

        assertThat(despues.lanes()).extracting(LaneResponse::nombre).containsExactly(VENTAS);
        assertThat(despues.actividades()).extracting(ActividadResponse::nombre)
                .containsExactly("Receive order", "Request payment authorization", "Cancel order");
        assertThat(despues.eventos()).extracting(EventoResponse::nombre)
                .containsExactly("Order received", "Payment result received", "Order cancelled");
        assertThat(despues.gateways()).hasSize(1);
        assertThat(despues.arcos()).hasSize(antes.arcos().size() - 4)
                .noneMatch(arco -> arco.destinoId().equals(armado.id("Pick and pack items")));
        assertThat(despues.pools()).hasSize(4);
        MensajeResponse envio = mensaje(despues, "Shipment request");
        assertThat(envio.nodoOrigenId()).isNull();
        assertThat(envio.poolOrigenId()).isEqualTo(armado.id(TIENDA));
        assertThat(envio.poolDestinoId()).isEqualTo(armado.id("Carrier"));
        assertThat(envio.tipoDestino()).isEqualTo(mensaje(antes, "Shipment request").tipoDestino());
        assertThat(mensaje(despues, "Shipment confirmation").nodoDestinoId()).isNull();
        assertThat(mensaje(despues, "Order placed")).isSameAs(mensaje(antes, "Order placed"));
        assertThat(despues.mensajes()).hasSize(6);
        assertThat(despues.correlaciones()).hasSize(6);
    }

    @Test
    @DisplayName("Sin un nodo se van sus flujos, y los mensajes que lo usaban quedan sin el")
    void sinNodo() {
        DiagramaResponse antes = armado.diagrama();
        Long cancelar = armado.id("Cancel order");

        DiagramaResponse despues = sin(ElementoDelDiagrama.Tipo.ACTIVIDAD, "Cancel order");

        assertThat(despues.actividades()).extracting(ActividadResponse::id).doesNotContain(cancelar).hasSize(4);
        assertThat(despues.arcos()).hasSize(antes.arcos().size() - 2)
                .noneMatch(arco -> cancelar.equals(arco.origenId()) || cancelar.equals(arco.destinoId()));
        assertThat(despues.lanes()).isEqualTo(antes.lanes());
        assertThat(despues.eventos()).isEqualTo(antes.eventos());
        MensajeResponse pedido = mensaje(despues, "Payment authorization request");
        assertThat(pedido.nodoManejoErrorId()).isNull();
        assertThat(pedido.nodoOrigenId()).isEqualTo(armado.id("Request payment authorization"));
        assertThat(pedido.respuestaEsperadaId()).isEqualTo(armado.id("Payment authorization result"));
        assertThat(mensaje(despues, "Order status notification").nodoOrigenId()).isNull();
        assertThat(mensaje(despues, "Shipment request")).isSameAs(mensaje(antes, "Shipment request"));
    }

    @Test
    @DisplayName("Sin un gateway o un evento tambien se van sus flujos")
    void sinGatewayOEvento() {
        DiagramaResponse sinGateway = sin(ElementoDelDiagrama.Tipo.GATEWAY, "Payment approved?");
        assertThat(sinGateway.gateways()).isEmpty();
        assertThat(sinGateway.arcos()).hasSize(armado.diagrama().arcos().size() - 3);

        DiagramaResponse sinEvento = sin(ElementoDelDiagrama.Tipo.EVENTO, "Order received");
        assertThat(sinEvento.eventos()).extracting(EventoResponse::nombre).doesNotContain("Order received");
        assertThat(mensaje(sinEvento, "Order placed").nodoDestinoId()).isNull();
    }

    @Test
    @DisplayName("Sin un flujo se va solo ese flujo")
    void sinArco() {
        DiagramaResponse antes = armado.diagrama();
        Long arco = armado.arcoEntre("Receive order", "Request payment authorization");

        DiagramaResponse despues = SimulacionDeBorrado.sinElElemento(antes,
                new ElementoDelDiagrama(ElementoDelDiagrama.Tipo.ARCO, arco));

        assertThat(despues.arcos()).extracting(ArcoResponse::id).doesNotContain(arco)
                .hasSize(antes.arcos().size() - 1);
        assertThat(despues.actividades()).isEqualTo(antes.actividades());
        assertThat(despues.mensajes()).isEqualTo(antes.mensajes());
    }

    @Test
    @DisplayName("Sin un mensaje se va su correlacion, y el que lo esperaba como respuesta se queda sin ella")
    void sinMensaje() {
        DiagramaResponse despues = sin(ElementoDelDiagrama.Tipo.MENSAJE, "Payment authorization result");

        assertThat(nombresDeMensajes(despues)).doesNotContain("Payment authorization result").hasSize(5);
        assertThat(despues.correlaciones()).hasSize(5);
        MensajeResponse pedido = mensaje(despues, "Payment authorization request");
        assertThat(pedido.respuestaEsperadaId()).isNull();
        assertThat(pedido.nodoManejoErrorId()).isEqualTo(armado.id("Cancel order"));
        assertThat(despues.arcos()).hasSize(armado.diagrama().arcos().size());
    }

    @Test
    @DisplayName("Un subproceso se lleva su contenido a cualquier profundidad")
    void sinSubproceso() {
        armado.actividad(VENTAS, "Review", TipoActividad.SUBPROCESO);
        armado.actividadEn(VENTAS, "Check stock", TipoActividad.SUBPROCESO, "Review", null);
        armado.eventoEn(VENTAS, "Stock checked", TipoEvento.FIN, "Check stock");
        armado.actividad(VENTAS, "Outside", TipoActividad.USUARIO);
        armado.arco("Check stock", "Outside");

        DiagramaResponse despues = sin(ElementoDelDiagrama.Tipo.ACTIVIDAD, "Review");

        assertThat(despues.actividades()).extracting(ActividadResponse::nombre)
                .doesNotContain("Review", "Check stock").contains("Outside");
        assertThat(despues.eventos()).extracting(EventoResponse::nombre).doesNotContain("Stock checked");
        assertThat(despues.arcos()).noneMatch(arco -> arco.destinoId().equals(armado.id("Outside")));
    }

    @Test
    @DisplayName("Los artefactos de un pool se van con el, los sueltos no, y una asociacion se va con cualquier extremo")
    void artefactosYAsociaciones() {
        String nota = armado.artefacto(TIENDA, VENTAS, "Note", TipoArtefacto.ANOTACION, "Check twice");
        String suelta = armado.artefacto(null, null, "Loose", TipoArtefacto.ANOTACION, "Loose note");
        String alNodo = armado.asociacion(TipoAsociacion.ASOCIACION, nota, "Activity_" + armado.id("Receive order"),
                DireccionAsociacion.NINGUNA);
        String sueltaAlNodo = armado.asociacion(TipoAsociacion.ASOCIACION, suelta,
                "Activity_" + armado.id("Ship order"), DireccionAsociacion.NINGUNA);
        String entreNotas = armado.asociacion(TipoAsociacion.ASOCIACION, suelta, nota, DireccionAsociacion.NINGUNA);
        Long flujo = armado.arcoEntre("Receive order", "Request payment authorization");
        String alFlujo = armado.asociacion(TipoAsociacion.ASOCIACION, suelta, "Flow_" + flujo,
                DireccionAsociacion.NINGUNA);

        DiagramaResponse sinNodo = sin(ElementoDelDiagrama.Tipo.ACTIVIDAD, "Receive order");
        assertThat(sinNodo.artefactos()).extracting(ArtefactoResponse::bpmnId).containsExactly(nota, suelta);
        assertThat(sinNodo.asociaciones()).extracting(AsociacionResponse::bpmnId)
                .containsExactly(sueltaAlNodo, entreNotas);

        DiagramaResponse sinTienda = sin(ElementoDelDiagrama.Tipo.POOL, TIENDA);
        assertThat(sinTienda.artefactos()).extracting(ArtefactoResponse::bpmnId).containsExactly(suelta);
        assertThat(sinTienda.asociaciones()).isEmpty();

        DiagramaResponse sinOtroPool = sin(ElementoDelDiagrama.Tipo.POOL, "Carrier");
        assertThat(sinOtroPool.artefactos()).hasSize(2);
        assertThat(sinOtroPool.asociaciones()).extracting(AsociacionResponse::bpmnId)
                .containsExactly(alNodo, sueltaAlNodo, entreNotas, alFlujo);
    }

    @Test
    @DisplayName("El aviso nombra lo que se iria ademas del elemento, y no el elemento mismo")
    void avisoDeLoQueSeVa() {
        DiagramaResponse antes = armado.diagrama();
        ElementoDelDiagrama lane = new ElementoDelDiagrama(ElementoDelDiagrama.Tipo.LANE, armado.id(ALMACEN));
        Hallazgos hallazgos = new Hallazgos();

        SimulacionDeBorrado.avisarDeLoQueSeVa(new MapaDelDiagrama(antes),
                new MapaDelDiagrama(SimulacionDeBorrado.sinElElemento(antes, lane)), lane, hallazgos);

        List<HallazgoDiagnosticoResponse> avisos = hallazgos.ordenados();
        assertThat(avisos).allMatch(aviso -> "A-06".equals(aviso.codigo()));
        assertThat(avisos).extracting(HallazgoDiagnosticoResponse::elementoId)
                .doesNotContain(armado.id(ALMACEN))
                .contains(armado.id("Pick and pack items"), armado.id("Order shipped"));
        assertThat(avisos).hasSize(8);
        assertThat(avisos).anyMatch(aviso -> aviso.problema().endsWith("tambien se va el nodo."));
        assertThat(avisos).anyMatch(aviso -> aviso.problema().endsWith("tambien se va el flujo."));
    }

    @Test
    @DisplayName("Al borrar el pool de la tienda el aviso nombra sus lanes y sus mensajes, pero no el pool")
    void avisoDelPool() {
        DiagramaResponse antes = armado.diagrama();
        ElementoDelDiagrama pool = new ElementoDelDiagrama(ElementoDelDiagrama.Tipo.POOL, armado.id(TIENDA));
        Hallazgos hallazgos = new Hallazgos();

        SimulacionDeBorrado.avisarDeLoQueSeVa(new MapaDelDiagrama(antes),
                new MapaDelDiagrama(SimulacionDeBorrado.sinElElemento(antes, pool)), pool, hallazgos);

        List<HallazgoDiagnosticoResponse> avisos = hallazgos.ordenados();
        assertThat(avisos).extracting(HallazgoDiagnosticoResponse::elemento)
                .contains("Lane \"" + VENTAS + "\"", "Lane \"" + ALMACEN + "\"", "Mensaje \"Order placed\"");
        assertThat(avisos).filteredOn(aviso -> aviso.problema().endsWith("el mensaje.")).hasSize(6);
        assertThat(avisos).filteredOn(aviso -> aviso.problema().endsWith("la lane.")).hasSize(2);
    }

    @Test
    @DisplayName("Borrar un mensaje o un flujo solo no avisa de nada mas")
    void avisoDeUnoSolo() {
        DiagramaResponse antes = armado.diagrama();
        ElementoDelDiagrama mensaje = new ElementoDelDiagrama(ElementoDelDiagrama.Tipo.MENSAJE,
                armado.id("Order placed"));
        ElementoDelDiagrama arco = new ElementoDelDiagrama(ElementoDelDiagrama.Tipo.ARCO,
                armado.arcoEntre("Receive order", "Request payment authorization"));
        Hallazgos hallazgos = new Hallazgos();

        SimulacionDeBorrado.avisarDeLoQueSeVa(new MapaDelDiagrama(antes),
                new MapaDelDiagrama(SimulacionDeBorrado.sinElElemento(antes, mensaje)), mensaje, hallazgos);
        SimulacionDeBorrado.avisarDeLoQueSeVa(new MapaDelDiagrama(antes),
                new MapaDelDiagrama(SimulacionDeBorrado.sinElElemento(antes, arco)), arco, hallazgos);

        assertThat(hallazgos.ordenados()).isEmpty();
    }

    private DiagramaResponse sin(ElementoDelDiagrama.Tipo tipo, String nombre) {
        return SimulacionDeBorrado.sinElElemento(armado.diagrama(), new ElementoDelDiagrama(tipo, armado.id(nombre)));
    }

    private static List<String> nombresDeMensajes(DiagramaResponse diagrama) {
        return diagrama.mensajes().stream().map(MensajeResponse::nombre).toList();
    }

    private static MensajeResponse mensaje(DiagramaResponse diagrama, String nombre) {
        return diagrama.mensajes().stream().filter(mensaje -> mensaje.nombre().equals(nombre)).findFirst()
                .orElseThrow();
    }
}
