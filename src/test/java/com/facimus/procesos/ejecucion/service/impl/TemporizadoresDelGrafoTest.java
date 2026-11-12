package com.facimus.procesos.ejecucion.service.impl;

import static com.facimus.procesos.modelado.service.DiagramaArmado.TIENDA;
import static com.facimus.procesos.modelado.service.DiagramaArmado.VENTAS;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.ejecucion.model.ClaseTemporizador;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoTemporizador;
import com.facimus.procesos.modelado.service.DiagramaArmado;

/** PR 68: los eventos de temporizador del grafo de una version, con lo que dicen para armarse. */
class TemporizadoresDelGrafoTest {

    @Test
    @DisplayName("Los bordes de una actividad son los suyos y no los de otra, y solo si dicen cuando saltan")
    void bordesDe() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(TIENDA, VENTAS, 3L);
        Long empacar = armado.actividad(VENTAS, "Pick items", TipoActividad.USUARIO);
        Long cobrar = armado.actividad(VENTAS, "Charge", TipoActividad.USUARIO);
        Long tarde = armado.temporizador(VENTAS, "Late", TipoEvento.TEMPORIZADOR_BORDE, TipoTemporizador.DURACION,
                "PT2H", "Pick items");
        Long aviso = armado.bordeQueNoInterrumpe(VENTAS, "Remind", TipoTemporizador.CICLO, "R3/PT1H", "Pick items");
        armado.temporizador(VENTAS, "Sin hora", TipoEvento.TEMPORIZADOR_BORDE, TipoTemporizador.DURACION, " ",
                "Pick items");
        armado.temporizador(VENTAS, "Sin tipo", TipoEvento.TEMPORIZADOR_BORDE, null, "PT1H", "Pick items");
        Long deCobrar = armado.temporizador(VENTAS, "Too slow", TipoEvento.TEMPORIZADOR_BORDE,
                TipoTemporizador.DURACION, "PT1H", "Charge");

        GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());

        assertThat(grafo.bordesDe(empacar)).extracting(TemporizadorDeLaVersion::nodoId).containsExactly(tarde, aviso);
        assertThat(grafo.bordesDe(cobrar)).extracting(TemporizadorDeLaVersion::nodoId).containsExactly(deCobrar);
        assertThat(grafo.bordesDe(tarde)).isEmpty();
        TemporizadorDeLaVersion interrumpe = grafo.bordesDe(empacar).getFirst();
        assertThat(interrumpe.interrumpe()).isTrue();
        assertThat(interrumpe.clase()).isEqualTo(ClaseTemporizador.BORDE);
        assertThat(interrumpe.seRepite()).isFalse();
        assertThat(interrumpe.adjuntoAId()).isEqualTo(empacar);
        assertThat(grafo.bordesDe(empacar).get(1).interrumpe()).isFalse();
        assertThat(grafo.bordesDe(empacar).get(1).seRepite()).isTrue();
    }

    @Test
    @DisplayName("El temporizador de un evento es el suyo, si lo es y dice cuando; uno de otro tipo de evento no lo tiene")
    void temporizadorDeUnEvento() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(TIENDA, VENTAS, 3L);
        Long inicio = armado.evento(VENTAS, "Start", TipoEvento.INICIO);
        Long espera = armado.temporizador(VENTAS, "Wait", TipoEvento.TEMPORIZADOR_INTERMEDIO,
                TipoTemporizador.DURACION_HABIL, "P2D", null);
        Long sinHora = armado.temporizador(VENTAS, "No time", TipoEvento.TEMPORIZADOR_INTERMEDIO,
                TipoTemporizador.DURACION, null, null);

        GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());

        assertThat(grafo.temporizador(espera)).get().satisfies(temporizador -> {
            assertThat(temporizador.nombre()).isEqualTo("Wait");
            assertThat(temporizador.tipo()).isEqualTo(TipoTemporizador.DURACION_HABIL);
            assertThat(temporizador.expresion()).isEqualTo("P2D");
            assertThat(temporizador.clase()).isEqualTo(ClaseTemporizador.INTERMEDIO);
            assertThat(temporizador.seRepite()).isFalse();
        });
        assertThat(grafo.temporizador(sinHora)).isEmpty();
        assertThat(grafo.temporizador(inicio)).isEmpty();
    }

    @Test
    @DisplayName("Los inicios temporizados son los que abren casos solos; un ciclo de inicio se repite")
    void iniciosTemporizados() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(TIENDA, VENTAS, 3L);
        Long cadaHora = armado.temporizador(VENTAS, "Every hour", TipoEvento.TEMPORIZADOR_INICIO,
                TipoTemporizador.CICLO, "R/PT1H", null);
        Long unaVez = armado.temporizador(VENTAS, "Once", TipoEvento.TEMPORIZADOR_INICIO, TipoTemporizador.FECHA,
                "2026-12-24T09:00:00Z", null);
        armado.temporizador(VENTAS, "Wait", TipoEvento.TEMPORIZADOR_INTERMEDIO, TipoTemporizador.DURACION, "PT1H",
                null);

        GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());

        assertThat(grafo.iniciosTemporizados()).extracting(TemporizadorDeLaVersion::nodoId)
                .containsExactly(cadaHora, unaVez);
        assertThat(grafo.iniciosTemporizados().getFirst().clase()).isEqualTo(ClaseTemporizador.INICIO);
        assertThat(grafo.iniciosTemporizados().getFirst().seRepite()).isTrue();
        assertThat(grafo.iniciosTemporizados().get(1).seRepite()).isFalse();
        assertThat(grafo.inicioAMano()).isEmpty();
        assertThat(grafo.inicioDeUnaPrueba()).get().extracting(NodoDeLaVersion::id).isEqualTo(cadaHora);
    }

    @Test
    @DisplayName("Un temporizador fuera del pool de la tienda no corre")
    void fueraDelPool() {
        DiagramaArmado armado = DiagramaArmado.demo();
        armado.dejaDeSerCajaNegra("Carrier");
        armado.lane("Carrier", "Dispatch");
        Long ajeno = armado.temporizador("Dispatch", "Their wait", TipoEvento.TEMPORIZADOR_INTERMEDIO,
                TipoTemporizador.DURACION, "PT1H", null);

        assertThat(GrafoDeVersion.de(armado.diagrama()).temporizador(ajeno)).isEmpty();
    }
}
