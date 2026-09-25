package com.facimus.procesos.ejecucion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.ejecucion.dto.response.CicloDeCasoResponse;

/**
 * El promedio y el p95 sobre listas que se pueden comprobar a mano. El p95 es el numero que una persona mira para
 * decir "casi todos los pedidos salen en menos de tanto", asi que tiene que ser un tiempo que un pedido tardo de
 * verdad y no un promedio con otro nombre.
 */
class TiempoDeCicloTest {

    @Test
    @DisplayName("Sin pedidos terminados no hay tiempo de ciclo: cero no seria rapido, seria mentira")
    void sinTerminados_noHayTiempo() {
        CicloDeCaso vacio = new CicloDeCaso(TiempoDeCiclo.de(List.of()));

        assertThat(vacio.ciclo().terminados()).isZero();
        assertThat(vacio.ciclo().medio()).isZero();
        assertThat(vacio.ciclo().p95()).isZero();
    }

    @Test
    @DisplayName("De veinte pedidos, uno lento no mueve el p95: no es el maximo")
    void unoLentoDeVeinte_noMueveElP95() {
        List<Integer> ticks = lentos(1, 19);

        CicloDeCasoResponse ciclo = TiempoDeCiclo.de(ticks);

        assertThat(ciclo.terminados()).isEqualTo(20);
        assertThat(ciclo.p95()).isEqualTo(1);
        assertThat(ciclo.medio()).isEqualTo(5.95);
    }

    @Test
    @DisplayName("Dos lentos de veinte si lo mueven, y ahi el p95 deja de parecerse al promedio")
    void dosLentosDeVeinte_muevenElP95() {
        List<Integer> ticks = lentos(2, 18);

        CicloDeCasoResponse ciclo = TiempoDeCiclo.de(ticks);

        assertThat(ciclo.p95()).isEqualTo(100);
        assertThat(ciclo.medio()).isEqualTo(10.9);
    }

    @Test
    @DisplayName("Con diez pedidos el p95 es el decimo: se redondea hacia arriba, no hacia abajo")
    void conDiez_elP95EsElDecimo() {
        List<Integer> ticks = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 50);

        CicloDeCasoResponse ciclo = TiempoDeCiclo.de(ticks);

        assertThat(ciclo.p95()).isEqualTo(50);
        assertThat(ciclo.medio()).isEqualTo(9.5);
    }

    @Test
    @DisplayName("Con un solo pedido, el p95 es ese pedido")
    void conUno_elP95EsEsePedido() {
        CicloDeCasoResponse ciclo = TiempoDeCiclo.de(List.of(7));

        assertThat(ciclo.terminados()).isEqualTo(1);
        assertThat(ciclo.medio()).isEqualTo(7.0);
        assertThat(ciclo.p95()).isEqualTo(7);
    }

    @Test
    @DisplayName("El promedio se publica con dos decimales, no con los que salgan")
    void elPromedio_vaConDosDecimales() {
        assertThat(TiempoDeCiclo.de(List.of(1, 2, 2)).medio()).isEqualTo(1.67);
    }

    /** Tantos pedidos rapidos y tantos lentos, ya ordenados como los ordena la consulta. */
    private static List<Integer> lentos(int cuantosLentos, int cuantosRapidos) {
        return IntStream.concat(IntStream.generate(() -> 1).limit(cuantosRapidos),
                IntStream.generate(() -> 100).limit(cuantosLentos)).boxed().toList();
    }

    /** Un envoltorio para leer mejor la prueba de la lista vacia. */
    private record CicloDeCaso(CicloDeCasoResponse ciclo) {
    }
}
