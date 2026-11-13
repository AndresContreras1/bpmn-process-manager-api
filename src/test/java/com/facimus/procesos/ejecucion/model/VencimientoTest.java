package com.facimus.procesos.ejecucion.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** PR 71: el estado que le toca a un plazo en cada momento, justo en sus bordes, y que nunca retrocede. */
class VencimientoTest {

    private static final LocalDateTime AVISO = LocalDateTime.parse("2026-01-14T15:30:00");
    private static final LocalDateTime VENCE = LocalDateTime.parse("2026-01-14T20:00:00");

    @ParameterizedTest(name = "a las {0} esta {1}")
    @CsvSource({"2026-01-09T20:00:00Z, A_TIEMPO", "2026-01-14T15:29:59Z, A_TIEMPO", "2026-01-14T15:30:00Z, POR_VENCER",
            "2026-01-14T19:59:59Z, POR_VENCER", "2026-01-14T20:00:00Z, VENCIDA", "2026-02-01T00:00:00Z, VENCIDA"})
    @DisplayName("En produccion: por vencer desde la hora del aviso, vencida desde la de vencer, las dos incluidas")
    void enProduccion(String ahora, EstadoPlazo esperado) {
        Vencimiento vencimiento = Vencimiento.enProduccion(AVISO, VENCE);

        assertThat(vencimiento.estadoEn(Instant.parse(ahora), 0)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "en el tick {0} esta {1}")
    @CsvSource({"3, A_TIEMPO", "8, A_TIEMPO", "9, POR_VENCER", "10, POR_VENCER", "11, VENCIDA", "40, VENCIDA"})
    @DisplayName("En prueba: lo mismo con los ticks, y el reloj de pared no cuenta")
    void enPrueba(int tick, EstadoPlazo esperado) {
        Vencimiento vencimiento = Vencimiento.enPrueba(9, 11);

        assertThat(vencimiento.estadoEn(Instant.parse("2099-01-01T00:00:00Z"), tick)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("Un plazo no retrocede: el que ya esta por vencer o vencido sigue asi aunque el reloj diga menos")
    void noRetrocede() {
        Vencimiento vencimiento = Vencimiento.enPrueba(9, 11);
        vencimiento.setEstado(EstadoPlazo.POR_VENCER);
        assertThat(vencimiento.estadoEn(Instant.EPOCH, 0)).isEqualTo(EstadoPlazo.POR_VENCER);

        vencimiento.setEstado(EstadoPlazo.VENCIDA);
        assertThat(vencimiento.estadoEn(Instant.EPOCH, 10)).isEqualTo(EstadoPlazo.VENCIDA);

        Vencimiento deProduccion = Vencimiento.enProduccion(AVISO, VENCE);
        deProduccion.setEstado(EstadoPlazo.VENCIDA);
        assertThat(deProduccion.estadoEn(Instant.parse("2026-01-09T20:00:00Z"), 0)).isEqualTo(EstadoPlazo.VENCIDA);
    }

    @Test
    @DisplayName("Recien armado esta a tiempo, y dice en que se cuenta")
    void recienArmado() {
        assertThat(Vencimiento.enProduccion(AVISO, VENCE).getEstado()).isEqualTo(EstadoPlazo.A_TIEMPO);
        assertThat(Vencimiento.enProduccion(AVISO, VENCE).enTicks()).isFalse();
        assertThat(Vencimiento.enPrueba(1, 2).getEstado()).isEqualTo(EstadoPlazo.A_TIEMPO);
        assertThat(Vencimiento.enPrueba(1, 2).enTicks()).isTrue();
    }

    @Test
    @DisplayName("El mas avanzado de dos estados es el que queda")
    void elMasAvanzado() {
        assertThat(EstadoPlazo.A_TIEMPO.yA(EstadoPlazo.POR_VENCER)).isEqualTo(EstadoPlazo.POR_VENCER);
        assertThat(EstadoPlazo.POR_VENCER.yA(EstadoPlazo.A_TIEMPO)).isEqualTo(EstadoPlazo.POR_VENCER);
        assertThat(EstadoPlazo.VENCIDA.yA(EstadoPlazo.POR_VENCER)).isEqualTo(EstadoPlazo.VENCIDA);
        assertThat(EstadoPlazo.POR_VENCER.yA(EstadoPlazo.VENCIDA)).isEqualTo(EstadoPlazo.VENCIDA);
        assertThat(EstadoPlazo.A_TIEMPO.yA(EstadoPlazo.A_TIEMPO)).isEqualTo(EstadoPlazo.A_TIEMPO);
    }
}
