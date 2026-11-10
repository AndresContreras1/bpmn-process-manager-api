package com.facimus.procesos.modelado.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.modelado.model.DisposicionBpmn.ArcoPlano;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Arista;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Forma;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Geometria;
import com.facimus.procesos.modelado.model.DisposicionBpmn.LanePlana;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Limites;
import com.facimus.procesos.modelado.model.DisposicionBpmn.MensajePlano;
import com.facimus.procesos.modelado.model.DisposicionBpmn.NodoPlano;
import com.facimus.procesos.modelado.model.DisposicionBpmn.PoolPlano;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Punto;

/**
 * PR 52: la cuenta de la geometria BPMN DI es la del dibujo de la web. El diagrama de referencia es el mismo de
 * {@code lienzo.spec.ts}, con los mismos numeros: si uno de los dos lados cambia la cuenta, falla su prueba.
 */
class DisposicionBpmnTest {

    private static final Geometria REFERENCIA = DisposicionBpmn.calcular(
            List.of(new PoolPlano(1, "Participant_1", "EMPRESA", 0, false),
                    new PoolPlano(2, "Participant_2", "CLIENTE", 0, true)),
            List.of(new LanePlana(10, "Lane_10", 1, 0), new LanePlana(11, "Lane_11", 1, 1)),
            List.of(new NodoPlano(100, "Event_100", 10, "EVENTO", 0, 0, "Order received"),
                    new NodoPlano(101, "Activity_101", 10, "TAREA", 180, 0, "Task 101"),
                    new NodoPlano(102, "Gateway_102", 11, "COMPUERTA", 360, 40, "Paid?"),
                    new NodoPlano(103, "Event_103", 11, "EVENTO", 540, 40, "End")),
            List.of(new ArcoPlano("Flow_1", 100, 101, null), new ArcoPlano("Flow_2", 101, 102, null),
                    new ArcoPlano("Flow_3", 102, 103, "Yes")),
            List.of(new MensajePlano(200, "MessageFlow_200", 2, 1, null, 100L)));

    private static final Map<String, Forma> FORMAS = REFERENCIA.formas().stream()
            .collect(Collectors.toMap(Forma::elemento, Function.identity()));
    private static final Map<String, Arista> ARISTAS = REFERENCIA.aristas().stream()
            .collect(Collectors.toMap(Arista::elemento, Function.identity()));

    @Test
    @DisplayName("Los pools, las lanes y los nodos quedan donde los dibuja la web")
    void formas() {
        assertThat(FORMAS.get("Participant_2").limites()).isEqualTo(new Limites(16, 16, 824, 56));
        assertThat(FORMAS.get("Participant_1").limites()).isEqualTo(new Limites(16, 120, 824, 240));
        assertThat(FORMAS.get("Lane_10").limites()).isEqualTo(new Limites(46, 120, 794, 120));
        assertThat(FORMAS.get("Lane_11").limites()).isEqualTo(new Limites(46, 240, 794, 120));
        assertThat(FORMAS.get("Activity_101").limites()).isEqualTo(new Limites(304, 144, 128, 56));
        assertThat(FORMAS.get("Gateway_102").limites()).isEqualTo(new Limites(523, 267, 50, 50));
        assertThat(FORMAS.get("Event_100").limites()).isEqualTo(new Limites(170, 154, 36, 36));
        assertThat(FORMAS.get("Event_103").limites()).isEqualTo(new Limites(710, 274, 36, 36));
        assertThat(FORMAS.get("Event_100").tipo()).isEqualTo("EVENTO");
        assertThat(FORMAS.get("Participant_1").tipo()).isEqualTo("PARTICIPANTE");
    }

    @Test
    @DisplayName("Las etiquetas van debajo del rombo y del circulo, y la tarea la lleva dentro")
    void etiquetas() {
        assertThat(FORMAS.get("Activity_101").etiqueta()).isNull();
        // "Paid?" desde la izquierda del rombo, con su linea base 12 por debajo
        assertThat(FORMAS.get("Gateway_102").etiqueta()).isEqualTo(new Limites(542, 318, 35, 14));
        // "Order received", centrada bajo el circulo
        assertThat(FORMAS.get("Event_100").etiqueta()).isEqualTo(new Limites(139, 193, 98, 14));
        assertThat(ARISTAS.get("Flow_3").etiqueta()).isEqualTo(new Limites(631, 275, 21, 14));
    }

    @Test
    @DisplayName("Los flujos y el mensaje pasan por los mismos puntos que en la web")
    void aristas() {
        assertThat(ARISTAS.get("Flow_1").puntos()).containsExactly(new Punto(206, 172), new Punto(304, 172));
        assertThat(ARISTAS.get("Flow_2").puntos()).containsExactly(new Punto(432, 172), new Punto(477.5, 172),
                new Punto(477.5, 292), new Punto(523, 292));
        assertThat(ARISTAS.get("Flow_3").puntos()).containsExactly(new Punto(573, 292), new Punto(710, 292));
        assertThat(ARISTAS.get("MessageFlow_200").puntos()).containsExactly(new Punto(188, 72), new Punto(188, 154));
        assertThat(ARISTAS.get("MessageFlow_200").tipo()).isEqualTo("MENSAJE");
    }

    @Test
    @DisplayName("Un proceso sin nada, y lo que apunta a un nodo dado de baja, no rompe la cuenta")
    void casosBorde() {
        assertThat(DisposicionBpmn.calcular(List.of(), List.of(), List.of(), List.of(), List.of()).formas()).isEmpty();
        Geometria huerfanos = DisposicionBpmn.calcular(List.of(new PoolPlano(1, "Participant_1", "EMPRESA", 0, false)),
                List.of(), List.of(), List.of(new ArcoPlano("Flow_9", 7, 8, null)), List.of());
        assertThat(huerfanos.aristas()).isEmpty();
        assertThat(huerfanos.formas()).extracting(Forma::limites).containsExactly(new Limites(16, 16, 720, 96));
    }

    @Test
    @DisplayName("Los puntos se guardan como [[x,y],...] y se leen igual")
    void puntosEnLaBase() {
        String guardado = AristaBpmn.escribir(ARISTAS.get("Flow_2").puntos());
        assertThat(guardado).isEqualTo("[[432,172],[477.5,172],[477.5,292],[523,292]]");
        AristaBpmn arista = AristaBpmn.builder().puntos(guardado).build();
        assertThat(arista.leerPuntos()).isEqualTo(ARISTAS.get("Flow_2").puntos());
    }
}
