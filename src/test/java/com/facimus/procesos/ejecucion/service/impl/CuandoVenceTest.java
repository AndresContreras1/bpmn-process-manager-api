package com.facimus.procesos.ejecucion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.facimus.procesos.common.tiempo.ExpresionDeTiempo;
import com.facimus.procesos.common.tiempo.ExpresionMalEscrita;
import com.facimus.procesos.gestion.model.Pais;
import com.facimus.procesos.gestion.service.CalendarioLaboral;
import com.facimus.procesos.modelado.model.TipoTemporizador;

/**
 * PR 68: cuando vence un temporizador. Las fechas de partida y de llegada estan escritas a mano en la zona de la
 * tienda, que es como se leeria el plazo en la pantalla.
 */
class CuandoVenceTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private static final CalendarioLaboral COLOMBIA = new CalendarioLaboral(Pais.CO, BOGOTA, LocalTime.of(8, 0),
            LocalTime.of(17, 0), EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), Map.of(), Set.of());
    private static final CalendarioLaboral FRANCIA = new CalendarioLaboral(Pais.FR, PARIS, LocalTime.of(9, 0),
            LocalTime.of(18, 0), EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), Map.of(), Set.of());

    private static Instant en(ZoneId zona, String cuando) {
        return LocalDateTime.parse(cuando).atZone(zona).toInstant();
    }

    @Nested
    @DisplayName("En produccion")
    class EnProduccion {

        @Test
        @DisplayName("Una duracion de calendario se suma en la zona de la tienda: P1D cruza el cambio de hora igual")
        void duracion() {
            assertThat(CuandoVence.primero(TipoTemporizador.DURACION, "PT2H", en(BOGOTA, "2026-01-09T16:00:00"),
                    COLOMBIA)).contains(en(BOGOTA, "2026-01-09T18:00:00"));
            // El 25 de octubre de 2026 Paris atrasa el reloj: un dia de calendario son 25 horas
            assertThat(CuandoVence.primero(TipoTemporizador.DURACION, "P1D", en(PARIS, "2026-10-24T10:00:00"),
                    FRANCIA)).contains(en(PARIS, "2026-10-25T10:00:00"));
            assertThat(CuandoVence.primero(TipoTemporizador.DURACION, "P1DT1H", en(PARIS, "2026-10-24T10:00:00"),
                    FRANCIA)).contains(en(PARIS, "2026-10-25T11:00:00"));
        }

        @Test
        @DisplayName("Una duracion habil cuenta jornadas con los festivos de la tienda")
        void duracionHabil() {
            // Viernes 9 de enero de 2026 a las tres; el lunes 12 es festivo (Reyes Magos)
            assertThat(CuandoVence.primero(TipoTemporizador.DURACION_HABIL, "P2D", en(BOGOTA, "2026-01-09T15:00:00"),
                    COLOMBIA)).contains(en(BOGOTA, "2026-01-14T15:00:00"));
            assertThat(CuandoVence.primero(TipoTemporizador.DURACION_HABIL, "PT3H",
                    en(BOGOTA, "2026-01-09T15:00:00"), COLOMBIA)).contains(en(BOGOTA, "2026-01-13T09:00:00"));
            assertThat(CuandoVence.primero(TipoTemporizador.DURACION_HABIL, "P1DT1H",
                    en(BOGOTA, "2026-01-13T08:00:00"), COLOMBIA)).contains(en(BOGOTA, "2026-01-14T09:00:00"));
        }

        @Test
        @DisplayName("Una fecha con zona es ese instante; sin zona, se lee en la de la tienda")
        void fecha() {
            Instant desde = en(BOGOTA, "2026-01-09T15:00:00");

            assertThat(CuandoVence.primero(TipoTemporizador.FECHA, "2026-12-24T09:00:00Z", desde, COLOMBIA))
                    .contains(Instant.parse("2026-12-24T09:00:00Z"));
            assertThat(CuandoVence.primero(TipoTemporizador.FECHA, "2026-12-24T09:00:00", desde, COLOMBIA))
                    .contains(en(BOGOTA, "2026-12-24T09:00:00"));
        }

        @Test
        @DisplayName("Un ciclo vence en su primera vuelta despues de armarse")
        void ciclo() {
            Instant desde = en(BOGOTA, "2026-01-09T15:20:00");

            assertThat(CuandoVence.primero(TipoTemporizador.CICLO, "R3/PT1H", desde, COLOMBIA))
                    .contains(en(BOGOTA, "2026-01-09T16:20:00"));
            assertThat(CuandoVence.primero(TipoTemporizador.CICLO, "0 0 9 * * MON-FRI", desde, COLOMBIA))
                    .contains(en(BOGOTA, "2026-01-12T09:00:00"));
            assertThat(CuandoVence.primero(TipoTemporizador.CICLO, "R/2026-01-01T08:00:00/P1D", desde, COLOMBIA))
                    .contains(en(BOGOTA, "2026-01-10T08:00:00"));
            assertThat(CuandoVence.primero(TipoTemporizador.CICLO, "R/2026-03-01T08:00:00/P1D", desde, COLOMBIA))
                    .contains(en(BOGOTA, "2026-03-01T08:00:00"));
        }

        @Test
        @DisplayName("Un ciclo cuyas vueltas ya pasaron, o con fin vencido, no tiene vencimiento")
        void ciclo_sinVueltas() {
            Instant desde = en(BOGOTA, "2026-01-09T15:20:00");

            assertThat(CuandoVence.primero(TipoTemporizador.CICLO, "R3/2026-01-01T08:00:00/P1D", desde, COLOMBIA))
                    .isEmpty();
            assertThat(CuandoVence.primero(TipoTemporizador.CICLO, "R/PT1H/2026-01-09T16:00:00", desde, COLOMBIA))
                    .isEmpty();
            assertThat(CuandoVence.primero(TipoTemporizador.CICLO, "R/PT1H/2026-01-09T16:20:00", desde, COLOMBIA))
                    .contains(en(BOGOTA, "2026-01-09T16:20:00"));
        }
    }

    @Nested
    @DisplayName("La siguiente vuelta de un ciclo")
    class Siguiente {

        private final Instant ancla = en(BOGOTA, "2026-01-09T08:00:00");

        @Test
        @DisplayName("Despues de una vuelta viene la siguiente, y despues de la ultima no viene ninguna")
        void vueltaPorVuelta() {
            var tres = ExpresionDeTiempo.ciclo("R3/PT1H");

            assertThat(CuandoVence.siguiente(tres, ancla, ancla, BOGOTA)).contains(en(BOGOTA, "2026-01-09T09:00:00"));
            assertThat(CuandoVence.siguiente(tres, ancla, en(BOGOTA, "2026-01-09T09:00:00"), BOGOTA))
                    .contains(en(BOGOTA, "2026-01-09T10:00:00"));
            assertThat(CuandoVence.siguiente(tres, ancla, en(BOGOTA, "2026-01-09T10:00:00"), BOGOTA))
                    .contains(en(BOGOTA, "2026-01-09T11:00:00"));
            assertThat(CuandoVence.siguiente(tres, ancla, en(BOGOTA, "2026-01-09T11:00:00"), BOGOTA)).isEmpty();
        }

        @Test
        @DisplayName("Las vueltas que pasaron con la API caida no se recuperan: sigue la proxima")
        void conLaApiCaida() {
            var cadaHora = ExpresionDeTiempo.ciclo("R/PT1H");

            assertThat(CuandoVence.siguiente(cadaHora, ancla, en(BOGOTA, "2026-01-09T13:30:00"), BOGOTA))
                    .contains(en(BOGOTA, "2026-01-09T14:00:00"));
            // Un ano parado, minuto a minuto, no se recorre vuelta por vuelta
            var cadaMinuto = ExpresionDeTiempo.ciclo("R/PT1M");
            assertThat(CuandoVence.siguiente(cadaMinuto, ancla, en(BOGOTA, "2027-01-09T08:00:30"), BOGOTA))
                    .contains(en(BOGOTA, "2027-01-09T08:01:00"));
        }

        @Test
        @DisplayName("Un ciclo con inicio cuenta desde el, y su inicio es la primera vuelta")
        void conInicio() {
            var diario = ExpresionDeTiempo.ciclo("R2/2026-01-10T08:00:00/P1D");

            assertThat(CuandoVence.siguiente(diario, ancla, ancla, BOGOTA)).contains(en(BOGOTA, "2026-01-10T08:00:00"));
            assertThat(CuandoVence.siguiente(diario, ancla, en(BOGOTA, "2026-01-10T08:00:00"), BOGOTA))
                    .contains(en(BOGOTA, "2026-01-11T08:00:00"));
            assertThat(CuandoVence.siguiente(diario, ancla, en(BOGOTA, "2026-01-11T08:00:00"), BOGOTA)).isEmpty();
        }

        @Test
        @DisplayName("Un ciclo mensual no se salta el mes que tocaba, aunque los meses no midan lo mismo")
        void mensual() {
            var mensual = ExpresionDeTiempo.ciclo("R/2026-01-31T09:00:00/P1M");

            assertThat(CuandoVence.siguiente(mensual, ancla, en(BOGOTA, "2026-02-27T00:00:00"), BOGOTA))
                    .contains(en(BOGOTA, "2026-02-28T09:00:00"));
            assertThat(CuandoVence.siguiente(mensual, ancla, en(BOGOTA, "2027-01-30T00:00:00"), BOGOTA))
                    .contains(en(BOGOTA, "2027-01-31T09:00:00"));
        }

        @Test
        @DisplayName("Un ciclo diario de Paris vuelve a la misma hora de pared despues del cambio de hora")
        void diarioConCambioDeHora() {
            var diario = ExpresionDeTiempo.ciclo("R/2026-10-20T09:00:00/P1D");

            assertThat(CuandoVence.siguiente(diario, ancla, en(PARIS, "2026-10-25T09:30:00"), PARIS))
                    .contains(en(PARIS, "2026-10-26T09:00:00"));
        }

        @Test
        @DisplayName("Un cron se lee en la zona de la tienda")
        void cron() {
            var nueve = ExpresionDeTiempo.ciclo("0 0 9 * * *");

            assertThat(CuandoVence.siguiente(nueve, ancla, en(PARIS, "2026-10-25T09:30:00"), PARIS))
                    .contains(en(PARIS, "2026-10-26T09:00:00"));
        }
    }

    @Nested
    @DisplayName("En prueba, en ticks de una hora")
    class EnPrueba {

        @ParameterizedTest
        @CsvSource({"DURACION,PT2H,2", "DURACION,PT90M,2", "DURACION,PT1M,1", "DURACION,PT0S,1", "DURACION,P1D,24",
                "DURACION,P1DT1H,25", "DURACION_HABIL,P2D,18", "DURACION_HABIL,PT4H,4", "DURACION_HABIL,P1DT30M,10",
                "FECHA,2026-12-24T09:00:00Z,1", "CICLO,R3/PT2H,2", "CICLO,0 0 9 * * *,1", "CICLO,R/PT30M,1"})
        @DisplayName("Lo que dura, en horas redondeadas hacia arriba; lo que no dura, el tick siguiente")
        void ticks(TipoTemporizador tipo, String expresion, int ticks) {
            assertThat(CuandoVence.ticks(tipo, expresion, COLOMBIA)).isEqualTo(ticks);
        }

        @Test
        @DisplayName("Una hora es un tick; una hora y un nanosegundo, dos; nunca menos de uno ni mas de los que caben")
        void enTicks() {
            assertThat(CuandoVence.enTicks(Duration.ofHours(1))).isOne();
            assertThat(CuandoVence.enTicks(Duration.ofHours(1).plusNanos(1))).isEqualTo(2);
            assertThat(CuandoVence.enTicks(Duration.ZERO)).isOne();
            assertThat(CuandoVence.enTicks(Duration.ofHours(Integer.MAX_VALUE + 5L))).isEqualTo(Integer.MAX_VALUE);
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"DURACION|P999999999Y", "DURACION|P300000Y", "DURACION|PT2562047788015215H",
            "DURACION_HABIL|P3000D", "CICLO|R/P999999999Y", "CICLO|* * * * * *",
            "CICLO|R/PT0.000001S"})
    @DisplayName("Lo que pasa los topes no se cuenta ni en produccion ni en prueba: es una expresion mal escrita")
    void fueraDeLosTopes(TipoTemporizador tipo, String expresion) {
        Instant desde = en(BOGOTA, "2026-01-09T15:00:00");

        assertThatThrownBy(() -> CuandoVence.primero(tipo, expresion, desde, COLOMBIA))
                .isInstanceOf(ExpresionMalEscrita.class);
        assertThatThrownBy(() -> CuandoVence.ticks(tipo, expresion, COLOMBIA)).isInstanceOf(ExpresionMalEscrita.class);
    }

    @Test
    @DisplayName("Una fecha fuera de los anos que se cuentan no vence nunca en produccion: es una expresion mal escrita")
    void fechaFueraDeLosTopes() {
        Instant desde = en(BOGOTA, "2026-01-09T15:00:00");

        assertThatThrownBy(() -> CuandoVence.primero(TipoTemporizador.FECHA, "+999999-01-01T00:00:00", desde,
                COLOMBIA)).isInstanceOf(ExpresionMalEscrita.class);
    }
}
