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
import com.facimus.procesos.modelado.model.DisposicionBpmn.ArtefactoPlano;
import com.facimus.procesos.modelado.model.DisposicionBpmn.AsociacionPlana;
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

    private static final PoolPlano POOL = new PoolPlano(1, "Participant_1", "EMPRESA", 0, false);
    private static final LanePlana LANE = new LanePlana(10, "Lane_10", 1, 0);
    private static final NodoPlano TAREA = new NodoPlano(101, "Activity_101", 10, "TAREA", 0, 0, "Task");

    private static Map<String, Forma> formas(Geometria geometria) {
        return geometria.formas().stream().collect(Collectors.toMap(Forma::elemento, Function.identity()));
    }

    @Test
    @DisplayName("Un evento de borde va sobre el borde de abajo de su actividad, diga lo que diga su posicion")
    void eventoDeBorde() {
        NodoPlano primero = new NodoPlano(105, "Event_105", 10, "EVENTO", 999, 999, "Late", 101L);
        NodoPlano segundo = new NodoPlano(106, "Event_106", 10, "EVENTO", -50, -50, "Later", 101L);

        Map<String, Forma> con = formas(DisposicionBpmn.calcular(List.of(POOL), List.of(LANE),
                List.of(TAREA, primero, segundo), List.of(), List.of()));
        Map<String, Forma> sin = formas(DisposicionBpmn.calcular(List.of(POOL), List.of(LANE), List.of(TAREA),
                List.of(), List.of()));

        Limites tarea = con.get("Activity_101").limites();
        assertThat(tarea).isEqualTo(sin.get("Activity_101").limites());
        assertThat(con.get("Lane_10").limites()).isEqualTo(sin.get("Lane_10").limites());
        Limites borde = con.get("Event_105").limites();
        assertThat(borde.y() + borde.alto() / 2).isEqualTo(tarea.y() + tarea.alto());
        assertThat(borde.x()).isEqualTo(tarea.x() + 4);
        assertThat(con.get("Event_106").limites().x()).isEqualTo(borde.x() + 36);
    }

    @Test
    @DisplayName("Un evento pegado a algo que no esta dibujado no se dibuja")
    void eventoDeBorde_sinActividad() {
        NodoPlano huerfano = new NodoPlano(105, "Event_105", 10, "EVENTO", 0, 0, "Late", 777L);

        assertThat(formas(DisposicionBpmn.calcular(List.of(POOL), List.of(LANE), List.of(TAREA, huerfano), List.of(),
                List.of()))).doesNotContainKey("Event_105");
    }

    @Test
    @DisplayName("Un artefacto en una lane se alinea con los nodos y la agranda si hace falta")
    void artefactoEnLane() {
        ArtefactoPlano alto = new ArtefactoPlano("Group_1", "GRUPO", 10L, 0, 0, 200, 300, "Checks");

        Map<String, Forma> con = formas(DisposicionBpmn.calcular(List.of(POOL), List.of(LANE), List.of(TAREA),
                List.of(), List.of(), List.of(alto), List.of()));

        Limites grupo = con.get("Group_1").limites();
        assertThat(grupo.x()).isEqualTo(con.get("Activity_101").limites().x());
        assertThat(grupo.y()).isEqualTo(con.get("Activity_101").limites().y());
        assertThat(grupo.ancho()).isEqualTo(200);
        assertThat(con.get("Lane_10").limites().alto()).isEqualTo(24 + 300 + 40);
        assertThat(con.get("Group_1").tipo()).isEqualTo("ARTEFACTO");
        assertThat(con.get("Group_1").etiqueta()).isNotNull();
    }

    @Test
    @DisplayName("Los artefactos sueltos van debajo de los pools, como estan unos respecto de otros")
    void artefactosSueltos() {
        ArtefactoPlano uno = new ArtefactoPlano("TextAnnotation_1", "ANOTACION", null, 500, 500, 100, 30, "One");
        ArtefactoPlano dos = new ArtefactoPlano("TextAnnotation_2", "ANOTACION", null, 650, 540, 100, 30, "Two");

        Map<String, Forma> con = formas(DisposicionBpmn.calcular(List.of(POOL), List.of(LANE), List.of(TAREA),
                List.of(), List.of(), List.of(uno, dos), List.of()));

        Limites pool = con.get("Participant_1").limites();
        Limites primero = con.get("TextAnnotation_1").limites();
        assertThat(primero.y()).isGreaterThan(pool.y() + pool.alto());
        assertThat(primero.x()).isEqualTo(16);
        assertThat(con.get("TextAnnotation_2").limites().x()).isEqualTo(primero.x() + 150);
        assertThat(con.get("TextAnnotation_2").limites().y()).isEqualTo(primero.y() + 40);
        assertThat(con.get("TextAnnotation_1").etiqueta()).isNull();
    }

    @Test
    @DisplayName("Una asociacion va de borde a borde, y una con un extremo sin dibujar no se traza")
    void asociaciones() {
        ArtefactoPlano dato = new ArtefactoPlano("DataObjectReference_1", "OBJETO_DATOS", 10L, 300, 0, 36, 50, "Order");

        Geometria geometria = DisposicionBpmn.calcular(List.of(POOL), List.of(LANE), List.of(TAREA), List.of(),
                List.of(), List.of(dato), List.of(
                        new AsociacionPlana("DataAssociation_1", "ENTRADA_DATOS", "DataObjectReference_1",
                                "Activity_101"),
                        new AsociacionPlana("Association_2", "ASOCIACION", "DataObjectReference_1", "Nada_9")));

        assertThat(geometria.aristas()).singleElement().satisfies(arista -> {
            assertThat(arista.tipo()).isEqualTo("DATOS");
            Limites tarea = formas(geometria).get("Activity_101").limites();
            Limites objeto = formas(geometria).get("DataObjectReference_1").limites();
            assertThat(arista.puntos().getFirst().x()).isEqualTo(objeto.x());
            assertThat(arista.puntos().getLast().x()).isEqualTo(tarea.x() + tarea.ancho());
        });
    }

    @Test
    @DisplayName("Un subproceso con contenido es la caja de lo que lleva dentro, y uno dentro de otro tambien")
    void subprocesos() {
        NodoPlano fuera = new NodoPlano(200, "Activity_200", 10, "TAREA", 999, 999, "Outer", null, null);
        NodoPlano medio = new NodoPlano(201, "Activity_201", 10, "TAREA", 999, 999, "Middle", null, 200L);
        NodoPlano hijo = new NodoPlano(202, "Activity_202", 10, "TAREA", 0, 0, "Child", null, 201L);
        NodoPlano otro = new NodoPlano(203, "Activity_203", 10, "TAREA", 300, 40, "Other", null, 201L);

        Map<String, Forma> con = formas(DisposicionBpmn.calcular(List.of(POOL), List.of(LANE),
                List.of(fuera, medio, hijo, otro), List.of(), List.of()));

        Limites a = con.get("Activity_202").limites();
        Limites b = con.get("Activity_203").limites();
        Limites caja = con.get("Activity_201").limites();
        assertThat(caja.x()).isEqualTo(a.x() - 24);
        assertThat(caja.y()).isEqualTo(a.y() - 24);
        assertThat(caja.x() + caja.ancho()).isEqualTo(b.x() + b.ancho() + 24);
        assertThat(caja.y() + caja.alto()).isEqualTo(b.y() + b.alto() + 24);
        Limites exterior = con.get("Activity_200").limites();
        assertThat(exterior.x()).isEqualTo(caja.x() - 24);
        assertThat(exterior.ancho()).isEqualTo(caja.ancho() + 48);
    }

    @Test
    @DisplayName("Un contenedor en un ciclo de datos rotos no cuelga la cuenta: no se dibuja")
    void subprocesos_ciclo() {
        NodoPlano uno = new NodoPlano(200, "Activity_200", 10, "TAREA", 0, 0, "One", null, 201L);
        NodoPlano dos = new NodoPlano(201, "Activity_201", 10, "TAREA", 0, 0, "Two", null, 200L);

        assertThat(formas(DisposicionBpmn.calcular(List.of(POOL), List.of(LANE), List.of(uno, dos), List.of(),
                List.of()))).doesNotContainKeys("Activity_200", "Activity_201");
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
