package com.facimus.procesos.integracion.simulado;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D7: la misma semilla decide siempre lo mismo, semillas distintas deciden distinto, y las tasas de los extremos
 * mandan sobre el hash. Sin esto una demo no se podria repetir y una prueba de socios seria un volado.
 */
class SemillaDeterministaTest {

    private static final String MENSAJE = "Payment authorization request";

    @Test
    @DisplayName("La misma semilla, el mismo caso y el mismo mensaje deciden siempre igual")
    void mismaSemilla_decideSiempreIgual() {
        boolean primera = SemillaDeterminista.leToca(42L, 7L, MENSAJE, 50);

        assertThat(IntStream.range(0, 100)
                .mapToObj(vuelta -> SemillaDeterminista.leToca(42L, 7L, MENSAJE, 50))
                .distinct()).containsExactly(primera);
    }

    @Test
    @DisplayName("Tasa cero no le toca a nadie y tasa cien le toca a todos, sin mirar el hash")
    void lasTasasDeLosExtremos_mandanSobreElHash() {
        assertThat(IntStream.range(0, 50)
                .mapToObj(caso -> SemillaDeterminista.leToca(42L, (long) caso, MENSAJE, 0))
                .distinct()).containsExactly(false);
        assertThat(IntStream.range(0, 50)
                .mapToObj(caso -> SemillaDeterminista.leToca(42L, (long) caso, MENSAJE, 100))
                .distinct()).containsExactly(true);
    }

    @Test
    @DisplayName("Con una tasa del cincuenta por ciento, cien pedidos no corren todos la misma suerte")
    void casosDistintos_noCorrenLaMismaSuerte() {
        long tocados = IntStream.range(0, 100)
                .filter(caso -> SemillaDeterminista.leToca(42L, (long) caso, MENSAJE, 50))
                .count();

        assertThat(tocados).isBetween(25L, 75L);
    }

    @Test
    @DisplayName("Cambiar la semilla cambia la simulacion; cambiar el mensaje del mismo caso, tambien")
    void otraSemillaUOtroMensaje_decidenDistinto() {
        // Se comparan los cien resultados y no cuantos toca: dos repartos distintos pueden sumar lo mismo, y
        // contarlos dejaria pasar una semilla que no se usa.
        List<Boolean> conUna = decisiones(42L, MENSAJE);
        List<Boolean> conOtra = decisiones(99L, MENSAJE);
        List<Boolean> conOtroMensaje = decisiones(42L, "Shipment request");

        assertThat(conUna).isNotEqualTo(conOtra).isNotEqualTo(conOtroMensaje);
        assertThat(conUna).isEqualTo(decisiones(42L, MENSAJE));
    }

    private static List<Boolean> decisiones(long semilla, String mensaje) {
        return IntStream.range(0, 100)
                .mapToObj(caso -> SemillaDeterminista.leToca(semilla, (long) caso, mensaje, 50))
                .toList();
    }

    @Test
    @DisplayName("Un caso sin id no rompe la cuenta: el mensaje ya distingue")
    void sinCaso_decideIgual() {
        assertThat(SemillaDeterminista.leToca(42L, null, MENSAJE, 50))
                .isEqualTo(SemillaDeterminista.leToca(42L, null, MENSAJE, 50));
    }
}
