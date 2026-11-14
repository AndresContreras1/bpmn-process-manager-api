package com.facimus.procesos.common.tiempo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.facimus.procesos.common.tiempo.ExpresionDeTiempo.Ciclo;
import com.facimus.procesos.common.tiempo.ExpresionDeTiempo.Duracion;
import com.facimus.procesos.common.tiempo.ExpresionDeTiempo.Fecha;

/** PR 68: la gramatica de los temporizadores, la misma para el diagnostico y para el motor. */
class ExpresionDeTiempoTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Test
    @DisplayName("Una duracion parte por la T: dias de calendario por un lado, horas por el otro")
    void duracion_partePorLaT() {
        assertThat(ExpresionDeTiempo.duracion("PT2H")).isEqualTo(new Duracion(Period.ZERO, Duration.ofHours(2),
                false));
        assertThat(ExpresionDeTiempo.duracion("P3D")).isEqualTo(new Duracion(Period.ofDays(3), Duration.ZERO, false));
        assertThat(ExpresionDeTiempo.duracion(" P1DT2H30M ")).isEqualTo(new Duracion(Period.ofDays(1),
                Duration.ofMinutes(150), false));
        assertThat(ExpresionDeTiempo.duracion("P1Y2M")).isEqualTo(new Duracion(Period.of(1, 2, 0), Duration.ZERO,
                false));
        assertThat(ExpresionDeTiempo.duracion("P1W").dias()).isEqualTo(Period.ofDays(7));
    }

    @Test
    @DisplayName("Una duracion cero no espera nada; una que espera algo no es cero")
    void duracion_cero() {
        assertThat(ExpresionDeTiempo.duracion("PT0S").esCero()).isTrue();
        assertThat(ExpresionDeTiempo.duracion("P1D").esCero()).isFalse();
        assertThat(ExpresionDeTiempo.duracion("PT1S").esCero()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "P", "PT", "P1DT", "2H", "two hours", "PX", "PT2X", "2026-12-24T09:00:00Z"})
    @DisplayName("Lo que no es una duracion lo dice, en ISO 8601")
    void duracion_mal(String texto) {
        assertThatThrownBy(() -> ExpresionDeTiempo.duracion(texto)).isInstanceOf(ExpresionMalEscrita.class)
                .hasMessage("\"" + texto + "\" no es una duracion en ISO 8601.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"P-1D", "PT-2H"})
    @DisplayName("Una duracion hacia atras no se espera")
    void duracion_negativa(String texto) {
        assertThatThrownBy(() -> ExpresionDeTiempo.duracion(texto)).isInstanceOf(ExpresionMalEscrita.class)
                .hasMessageContaining("hacia atras");
    }

    @Test
    @DisplayName("Sin texto no hay duracion")
    void duracion_nula() {
        assertThatThrownBy(() -> ExpresionDeTiempo.duracion(null)).isInstanceOf(ExpresionMalEscrita.class)
                .hasMessage("\"\" no es una duracion en ISO 8601.");
    }

    @Test
    @DisplayName("Una duracion habil cuenta dias y horas de jornada")
    void duracionHabil() {
        assertThat(ExpresionDeTiempo.duracionHabil("P2D")).isEqualTo(new Duracion(Period.ofDays(2), Duration.ZERO,
                true));
        assertThat(ExpresionDeTiempo.duracionHabil("P1DT4H")).isEqualTo(new Duracion(Period.ofDays(1),
                Duration.ofHours(4), true));
        assertThat(ExpresionDeTiempo.duracionHabil("PT90M").tiempo()).isEqualTo(Duration.ofMinutes(90));
    }

    @ParameterizedTest
    @ValueSource(strings = {"P1Y", "P1M", "P1W", "P1M2D"})
    @DisplayName("Un ano, un mes o una semana habil no son una cantidad de jornadas")
    void duracionHabil_sinMesesNiSemanas(String texto) {
        assertThatThrownBy(() -> ExpresionDeTiempo.duracionHabil(texto)).isInstanceOf(ExpresionMalEscrita.class)
                .hasMessageContaining("no es una duracion habil").hasMessageContaining("no anos, meses ni semanas");
    }

    @Test
    @DisplayName("Una duracion habil mal escrita lo dice como habil")
    void duracionHabil_mal() {
        assertThatThrownBy(() -> ExpresionDeTiempo.duracionHabil("dos dias")).isInstanceOf(ExpresionMalEscrita.class)
                .hasMessage("\"dos dias\" no es una duracion habil en ISO 8601.");
    }

    @Test
    @DisplayName("Una fecha con zona es ese instante; sin zona, el de la tienda")
    void fecha() {
        Fecha conZona = ExpresionDeTiempo.fecha("2026-12-24T09:00:00Z");
        Fecha conDesfase = ExpresionDeTiempo.fecha("2026-12-24T09:00:00-05:00");
        Fecha sinZona = ExpresionDeTiempo.fecha("2026-12-24T09:00:00");

        assertThat(conZona.en(BOGOTA)).isEqualTo(Instant.parse("2026-12-24T09:00:00Z"));
        assertThat(conDesfase.zona()).isEqualTo(ZoneOffset.ofHours(-5));
        assertThat(conDesfase.en(ZoneOffset.UTC)).isEqualTo(Instant.parse("2026-12-24T14:00:00Z"));
        assertThat(sinZona.zona()).isNull();
        assertThat(sinZona.local()).isEqualTo(LocalDateTime.of(2026, 12, 24, 9, 0));
        assertThat(sinZona.en(BOGOTA)).isEqualTo(Instant.parse("2026-12-24T14:00:00Z"));
        assertThat(ExpresionDeTiempo.fecha("2026-12-24T09:00:00+01:00[Europe/Paris]").en(BOGOTA))
                .isEqualTo(Instant.parse("2026-12-24T08:00:00Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-12-24", "PT2H", "manana", "2026-13-01T09:00:00"})
    @DisplayName("Lo que no es una fecha y hora lo dice")
    void fecha_mal(String texto) {
        assertThatThrownBy(() -> ExpresionDeTiempo.fecha(texto)).isInstanceOf(ExpresionMalEscrita.class)
                .hasMessage("\"" + texto + "\" no es una fecha y hora en ISO 8601.");
    }

    @Test
    @DisplayName("Un ciclo ISO: R sin numero no termina; R3 salta tres veces; con inicio o con fin")
    void ciclo_iso() {
        Ciclo cadaHora = ExpresionDeTiempo.ciclo("R/PT1H");
        Ciclo tres = ExpresionDeTiempo.ciclo("R3/PT1H");
        Ciclo conInicio = ExpresionDeTiempo.ciclo("R/2026-01-01T08:00:00Z/P1D");
        Ciclo conFin = ExpresionDeTiempo.ciclo("R2/PT1H/2026-01-01T08:00:00Z");
        Ciclo doce = ExpresionDeTiempo.ciclo("R12/P1M");

        assertThat(cadaHora.repeticiones()).isNull();
        assertThat(cadaHora.esCron()).isFalse();
        assertThat(cadaHora.periodo().tiempo()).isEqualTo(Duration.ofHours(1));
        assertThat(tres.repeticiones()).isEqualTo(3);
        assertThat(tres.inicio()).isNull();
        assertThat(tres.fin()).isNull();
        assertThat(conInicio.inicio().en(BOGOTA)).isEqualTo(Instant.parse("2026-01-01T08:00:00Z"));
        assertThat(conInicio.periodo().dias()).isEqualTo(Period.ofDays(1));
        assertThat(conFin.inicio()).isNull();
        assertThat(conFin.fin().en(BOGOTA)).isEqualTo(Instant.parse("2026-01-01T08:00:00Z"));
        assertThat(conFin.repeticiones()).isEqualTo(2);
        assertThat(doce.repeticiones()).isEqualTo(12);
    }

    @Test
    @DisplayName("Un ciclo en cron, de seis campos")
    void ciclo_cron() {
        Ciclo lunes = ExpresionDeTiempo.ciclo("0 0 9 * * MON-FRI");

        assertThat(lunes.esCron()).isTrue();
        assertThat(lunes.repeticiones()).isNull();
        assertThat(lunes.periodo()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"R3", "Rx/PT1H", "R0/PT1H", "R3/nada", "R/2026-01-01T08:00:00Z", "R/PT1H/PT1H/PT1H",
            "R/2026-01-01T08:00:00Z/2026-02-01T08:00:00Z", "R/P1D/manana", "R-1/PT1H"})
    @DisplayName("Un intervalo mal escrito no es un ciclo")
    void ciclo_isoMal(String texto) {
        assertThatThrownBy(() -> ExpresionDeTiempo.ciclo(texto)).isInstanceOf(ExpresionMalEscrita.class)
                .hasMessage("\"" + texto + "\" no es un ciclo en ISO 8601.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT1H", "cada lunes", "0 0 9 * *", ""})
    @DisplayName("Lo que no es un intervalo ni un cron no es un ciclo")
    void ciclo_ni_cron(String texto) {
        assertThatThrownBy(() -> ExpresionDeTiempo.ciclo(texto)).isInstanceOf(ExpresionMalEscrita.class)
                .hasMessage("\"" + texto + "\" no es un ciclo en ISO 8601 ni una expresion cron.");
    }

    @Test
    @DisplayName("Un ciclo que se repite sin esperar nada no se puede armar")
    void ciclo_periodoCero() {
        assertThatThrownBy(() -> ExpresionDeTiempo.ciclo("R/PT0S")).isInstanceOf(ExpresionMalEscrita.class)
                .hasMessageContaining("el periodo no puede ser cero");
    }

    @Test
    @DisplayName("Una duracion llega hasta cien anos contados por lo largo, y ni un dia mas")
    void duracion_topeDeCienAnos() {
        assertThat(ExpresionDeTiempo.duracion("P100Y").dias()).isEqualTo(Period.ofYears(100));
        assertThat(ExpresionDeTiempo.duracion("P1200M").dias()).isEqualTo(Period.ofMonths(1200));
        assertThat(ExpresionDeTiempo.duracion("P37200D").dias()).isEqualTo(Period.ofDays(37_200));
        assertThat(ExpresionDeTiempo.duracion("PT892800H").tiempo()).isEqualTo(Duration.ofDays(37_200));
        assertThatThrownBy(() -> ExpresionDeTiempo.duracion("P100Y1D")).isInstanceOf(ExpresionMalEscrita.class)
                .hasMessage("\"P100Y1D\" pasa de cien anos: es el tope de una duracion.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"P101Y", "P999999999Y", "P300000Y", "P37201D", "PT892801H", "PT2562047788015215H",
            "P1200MT24H", "P5315W1D"})
    @DisplayName("Lo que pasa de cien anos no se espera, aunque se lea: el motor no podria contarlo")
    void duracion_masDeCienAnos(String texto) {
        assertThatThrownBy(() -> ExpresionDeTiempo.duracion(texto)).isInstanceOf(ExpresionMalEscrita.class)
                .hasMessageContaining("pasa de cien anos");
    }

    @Test
    @DisplayName("Una duracion habil llega hasta 366 jornadas, o 366 dias de horas de jornada")
    void duracionHabil_tope() {
        assertThat(ExpresionDeTiempo.duracionHabil("P366D").dias()).isEqualTo(Period.ofDays(366));
        assertThat(ExpresionDeTiempo.duracionHabil("PT8784H").tiempo()).isEqualTo(Duration.ofDays(366));
        assertThatThrownBy(() -> ExpresionDeTiempo.duracionHabil("P367D")).isInstanceOf(ExpresionMalEscrita.class)
                .hasMessage("\"P367D\" pasa de 366 jornadas: es el tope de una duracion habil.");
        assertThatThrownBy(() -> ExpresionDeTiempo.duracionHabil("PT8785H")).isInstanceOf(ExpresionMalEscrita.class)
                .hasMessageContaining("pasa de 366 jornadas");
        assertThatThrownBy(() -> ExpresionDeTiempo.duracionHabil("P3000D")).isInstanceOf(ExpresionMalEscrita.class)
                .hasMessageContaining("pasa de 366 jornadas");
    }

    @Test
    @DisplayName("Una fecha va de 1900 a 9999; fuera de ahi no se arma ni se guarda")
    void fecha_tope() {
        assertThat(ExpresionDeTiempo.fecha("1900-01-01T00:00:00").local().getYear()).isEqualTo(1900);
        assertThat(ExpresionDeTiempo.fecha("9999-12-31T23:59:59Z").local().getYear()).isEqualTo(9999);
        assertThatThrownBy(() -> ExpresionDeTiempo.fecha("+999999-01-01T00:00:00"))
                .isInstanceOf(ExpresionMalEscrita.class)
                .hasMessage("\"+999999-01-01T00:00:00\" cae fuera de los anos que se cuentan: de 1900 a 9999.");
        assertThatThrownBy(() -> ExpresionDeTiempo.fecha("1899-12-31T23:59:59Z"))
                .isInstanceOf(ExpresionMalEscrita.class).hasMessageContaining("de 1900 a 9999");
        assertThatThrownBy(() -> ExpresionDeTiempo.fecha("+10000-01-01T00:00:00"))
                .isInstanceOf(ExpresionMalEscrita.class).hasMessageContaining("de 1900 a 9999");
    }

    @Test
    @DisplayName("Un ciclo salta a lo sumo una vez por minuto: un minuto vale, menos no")
    void ciclo_unaVezPorMinuto() {
        assertThat(ExpresionDeTiempo.ciclo("R/PT1M").periodo().tiempo()).isEqualTo(Duration.ofMinutes(1));
        assertThat(ExpresionDeTiempo.ciclo("R/P1D").periodo().dias()).isEqualTo(Period.ofDays(1));
        assertThatThrownBy(() -> ExpresionDeTiempo.ciclo("R/PT59S")).isInstanceOf(ExpresionMalEscrita.class)
                .hasMessage("\"R/PT59S\" saltaria mas de una vez por minuto: el periodo es de un minuto o mas.");
        assertThatThrownBy(() -> ExpresionDeTiempo.ciclo("R/PT0.000001S")).isInstanceOf(ExpresionMalEscrita.class)
                .hasMessageContaining("mas de una vez por minuto");
    }

    @ParameterizedTest
    @ValueSource(strings = {"R/P999999999Y", "R3/P101Y", "R/+999999-01-01T00:00:00/P1D", "R/PT1H/1800-01-01T00:00:00Z"})
    @DisplayName("Un ciclo cuyo periodo o cuyas fechas pasan los topes tampoco se arma")
    void ciclo_fueraDeLosTopes(String texto) {
        assertThatThrownBy(() -> ExpresionDeTiempo.ciclo(texto)).isInstanceOf(ExpresionMalEscrita.class)
                .hasMessageStartingWith("\"" + texto + "\"")
                .hasMessageNotContaining("no es un ciclo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"* * * * * *", "*/30 * * * * *", "0,30 * * * * *", "0-5 0 9 * * *"})
    @DisplayName("Un cron que salta mas de una vez por minuto no es un ciclo: los segundos van fijos")
    void ciclo_cronDemasiadoSeguido(String texto) {
        assertThatThrownBy(() -> ExpresionDeTiempo.ciclo(texto)).isInstanceOf(ExpresionMalEscrita.class)
                .hasMessage("\"" + texto + "\" saltaria mas de una vez por minuto: los segundos van fijos, como en "
                        + "0 0 9 * * MON-FRI.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0 * * * * *", "30 0 9 * * MON-FRI", "@hourly", "@daily"})
    @DisplayName("Uno que salta cada minuto, o una de las macros, si es un ciclo")
    void ciclo_cronPorMinuto(String texto) {
        assertThat(ExpresionDeTiempo.ciclo(texto).esCron()).isTrue();
    }

    @Test
    @DisplayName("Las repeticiones se leen enteras, cifra por cifra, y un numero absurdo no pasa")
    void ciclo_repeticiones() {
        assertThat(ExpresionDeTiempo.ciclo("R105/PT1H").repeticiones()).isEqualTo(105);
        assertThat(ExpresionDeTiempo.ciclo("R9/PT1H").repeticiones()).isEqualTo(9);
        assertThatThrownBy(() -> ExpresionDeTiempo.ciclo("R99999999999/PT1H"))
                .isInstanceOf(ExpresionMalEscrita.class);
    }
}
