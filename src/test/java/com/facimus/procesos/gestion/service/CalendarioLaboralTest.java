package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.gestion.model.OrigenFestivo;
import com.facimus.procesos.gestion.model.Pais;

/**
 * PR 68: el tiempo habil. Cada prueba escribe a mano el momento de partida y el de llegada en la zona de la tienda, asi
 * que un fin de semana, un festivo o un cambio de hora que se cuente mal cambia la llegada.
 */
class CalendarioLaboralTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final ZoneId NUEVA_YORK = ZoneId.of("America/New_York");
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final ZoneId SIDNEY = ZoneId.of("Australia/Sydney");
    private static final Set<DayOfWeek> ENTRE_SEMANA = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

    /** Colombia, de 8 a 17, de lunes a viernes: la de fabrica. */
    private static CalendarioLaboral colombia() {
        return new CalendarioLaboral(Pais.CO, BOGOTA, LocalTime.of(8, 0), LocalTime.of(17, 0), ENTRE_SEMANA, Map.of(),
                Set.of());
    }

    private static Instant en(ZoneId zona, String cuando) {
        return LocalDateTime.parse(cuando).atZone(zona).toInstant();
    }

    @Nested
    @DisplayName("Que dia y que hora es habil")
    class Habil {

        @Test
        @DisplayName("La jornada dura lo que va de la apertura al cierre")
        void jornada() {
            assertThat(colombia().jornada()).isEqualTo(Duration.ofHours(9));
            assertThat(colombia().pais()).isEqualTo(Pais.CO);
            assertThat(colombia().zona()).isEqualTo(BOGOTA);
        }

        @Test
        @DisplayName("Un dia entre semana es habil; un sabado o un festivo, no")
        void dias() {
            CalendarioLaboral calendario = colombia();

            assertThat(calendario.esDiaHabil(LocalDate.of(2026, 1, 13))).isTrue();
            assertThat(calendario.esDiaHabil(LocalDate.of(2026, 1, 10))).isFalse();
            assertThat(calendario.esDiaHabil(LocalDate.of(2026, 1, 12))).isFalse();
            assertThat(calendario.esFestivo(LocalDate.of(2026, 1, 12))).isTrue();
            assertThat(calendario.esFestivo(LocalDate.of(2026, 1, 13))).isFalse();
        }

        @Test
        @DisplayName("Se trabaja desde la apertura, incluida, hasta el cierre, excluido")
        void horas() {
            CalendarioLaboral calendario = colombia();

            assertThat(calendario.esHabil(en(BOGOTA, "2026-01-13T07:59:59"))).isFalse();
            assertThat(calendario.esHabil(en(BOGOTA, "2026-01-13T08:00:00"))).isTrue();
            assertThat(calendario.esHabil(en(BOGOTA, "2026-01-13T16:59:59"))).isTrue();
            assertThat(calendario.esHabil(en(BOGOTA, "2026-01-13T17:00:00"))).isFalse();
            assertThat(calendario.esHabil(en(BOGOTA, "2026-01-12T10:00:00"))).isFalse();
        }

        @Test
        @DisplayName("El siguiente momento habil es el mismo si ya lo es; si no, la apertura del siguiente dia habil")
        void siguienteMomentoHabil() {
            CalendarioLaboral calendario = colombia();

            assertThat(calendario.siguienteMomentoHabil(en(BOGOTA, "2026-01-13T10:00:00")))
                    .isEqualTo(en(BOGOTA, "2026-01-13T10:00:00"));
            // El viernes al salir; el lunes 12 es festivo (Reyes Magos)
            assertThat(calendario.siguienteMomentoHabil(en(BOGOTA, "2026-01-09T18:00:00")))
                    .isEqualTo(en(BOGOTA, "2026-01-13T08:00:00"));
            assertThat(calendario.siguienteMomentoHabil(en(BOGOTA, "2026-01-13T06:30:00")))
                    .isEqualTo(en(BOGOTA, "2026-01-13T08:00:00"));
        }
    }

    @Nested
    @DisplayName("Sumar tiempo habil")
    class Sumar {

        @Test
        @DisplayName("Lo que no cabe el viernes sigue el siguiente dia habil, saltando el fin de semana y el festivo")
        void horas_saltanFinDeSemanaYFestivo() {
            assertThat(colombia().sumarHoras(en(BOGOTA, "2026-01-09T15:00:00"), Duration.ofHours(4)))
                    .isEqualTo(en(BOGOTA, "2026-01-13T10:00:00"));
        }

        @Test
        @DisplayName("Lo que se cumple justo al cerrar es el cierre, no la apertura del dia siguiente")
        void horas_justoAlCerrar() {
            assertThat(colombia().sumarHoras(en(BOGOTA, "2026-01-09T15:00:00"), Duration.ofHours(2)))
                    .isEqualTo(en(BOGOTA, "2026-01-09T17:00:00"));
        }

        @Test
        @DisplayName("Lo que empieza antes de abrir cuenta desde la apertura")
        void horas_antesDeAbrir() {
            assertThat(colombia().sumarHoras(en(BOGOTA, "2026-01-13T05:00:00"), Duration.ofMinutes(30)))
                    .isEqualTo(en(BOGOTA, "2026-01-13T08:30:00"));
        }

        @Test
        @DisplayName("Dos dias habiles desde un viernes a las tres son el martes a las tres")
        void dias_deViernesAMartes() {
            assertThat(colombia().sumarDias(en(BOGOTA, "2026-01-16T15:00:00"), 2))
                    .isEqualTo(en(BOGOTA, "2026-01-20T15:00:00"));
        }

        @Test
        @DisplayName("Un dia habil desde un sabado es el lunes al cerrar")
        void dias_desdeUnSabado() {
            assertThat(colombia().sumarDias(en(BOGOTA, "2026-01-17T10:00:00"), 1))
                    .isEqualTo(en(BOGOTA, "2026-01-19T17:00:00"));
        }

        @Test
        @DisplayName("Cero dias desde un momento habil es ese momento")
        void dias_cero() {
            assertThat(colombia().sumarDias(en(BOGOTA, "2026-01-13T10:00:00"), 0))
                    .isEqualTo(en(BOGOTA, "2026-01-13T10:00:00"));
        }

        @Test
        @DisplayName("El tiempo habil se suma hacia adelante")
        void haciaAtras() {
            CalendarioLaboral calendario = colombia();
            Instant ahora = en(BOGOTA, "2026-01-13T10:00:00");
            Duration haciaAtras = Duration.ofHours(-1);

            assertThatThrownBy(() -> calendario.sumarHoras(ahora, haciaAtras))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> calendario.sumarDias(ahora, -1)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Estados Unidos: el cambio de hora de marzo no se cuenta como tiempo habil")
        void estadosUnidos_cambioDeHora() {
            CalendarioLaboral nuevaYork = new CalendarioLaboral(Pais.US, NUEVA_YORK, LocalTime.of(8, 0),
                    LocalTime.of(17, 0), ENTRE_SEMANA, Map.of(), Set.of());

            // El domingo 8 de marzo de 2026 el reloj se adelanta: el viernes cierra en EST y el lunes abre en EDT
            Instant llegada = nuevaYork.sumarHoras(en(NUEVA_YORK, "2026-03-06T16:00:00"), Duration.ofHours(2));

            assertThat(llegada).isEqualTo(en(NUEVA_YORK, "2026-03-09T09:00:00"))
                    .isEqualTo(Instant.parse("2026-03-09T13:00:00Z"));
        }

        @Test
        @DisplayName("Una jornada nocturna el dia del cambio de hora dura lo que dura de verdad")
        void jornadaDelCambioDeHora() {
            CalendarioLaboral nocturno = new CalendarioLaboral(Pais.US, NUEVA_YORK, LocalTime.of(0, 0),
                    LocalTime.of(6, 0), EnumSet.allOf(DayOfWeek.class), Map.of(), Set.of());

            assertThat(nocturno.entre(en(NUEVA_YORK, "2026-03-08T00:00:00"), en(NUEVA_YORK, "2026-03-08T06:00:00")))
                    .isEqualTo(Duration.ofHours(5));
            assertThat(nocturno.entre(en(NUEVA_YORK, "2026-11-01T00:00:00"), en(NUEVA_YORK, "2026-11-01T06:00:00")))
                    .isEqualTo(Duration.ofHours(7));
        }

        @Test
        @DisplayName("Francia: el cambio de hora de octubre tampoco")
        void francia_cambioDeHora() {
            CalendarioLaboral paris = new CalendarioLaboral(Pais.FR, PARIS, LocalTime.of(9, 0), LocalTime.of(18, 0),
                    ENTRE_SEMANA, Map.of(), Set.of());

            // El domingo 25 de octubre de 2026 el reloj se atrasa: el viernes es CEST (+2) y el lunes CET (+1)
            assertThat(paris.sumarHoras(en(PARIS, "2026-10-23T17:00:00"), Duration.ofHours(2)))
                    .isEqualTo(Instant.parse("2026-10-26T09:00:00Z"));
        }

        @Test
        @DisplayName("Francia: el 11 de noviembre, un miercoles festivo, no cuenta")
        void francia_festivo() {
            CalendarioLaboral paris = new CalendarioLaboral(Pais.FR, PARIS, LocalTime.of(9, 0), LocalTime.of(18, 0),
                    ENTRE_SEMANA, Map.of(), Set.of());

            assertThat(paris.sumarDias(en(PARIS, "2026-11-10T12:00:00"), 1))
                    .isEqualTo(en(PARIS, "2026-11-12T12:00:00"));
        }

        @Test
        @DisplayName("Australia: el cambio de hora de octubre en Sidney, y el lunes festivo de un estado solo si se carga")
        void australia_cambioDeHoraYFestivoDelEstado() {
            CalendarioLaboral sidney = new CalendarioLaboral(Pais.AU, SIDNEY, LocalTime.of(9, 0), LocalTime.of(17, 0),
                    ENTRE_SEMANA, Map.of(), Set.of());
            CalendarioLaboral conLabourDay = new CalendarioLaboral(Pais.AU, SIDNEY, LocalTime.of(9, 0),
                    LocalTime.of(17, 0), ENTRE_SEMANA, Map.of(LocalDate.of(2026, 10, 5), "Labour Day (NSW)"),
                    Set.of());
            Instant viernes = en(SIDNEY, "2026-10-02T16:00:00");

            // El domingo 4 de octubre de 2026 Sidney pasa de AEST (+10) a AEDT (+11)
            assertThat(sidney.sumarHoras(viernes, Duration.ofHours(2)))
                    .isEqualTo(Instant.parse("2026-10-04T23:00:00Z"));
            assertThat(conLabourDay.sumarHoras(viernes, Duration.ofHours(2)))
                    .isEqualTo(en(SIDNEY, "2026-10-06T10:00:00"));
        }

        @Test
        @DisplayName("Un calendario sin tiempo habil en diez anos no se cuenta para siempre")
        void sinTiempoHabil() {
            Map<LocalDate, String> todosLosLunes = new HashMap<>();
            for (LocalDate lunes = LocalDate.of(2026, 1, 5); lunes.getYear() < 2037; lunes = lunes.plusWeeks(1)) {
                todosLosLunes.put(lunes, "Cerrado");
            }
            CalendarioLaboral cerrado = new CalendarioLaboral(Pais.CO, BOGOTA, LocalTime.of(8, 0),
                    LocalTime.of(17, 0), EnumSet.of(DayOfWeek.MONDAY), todosLosLunes, Set.of());
            Instant ahora = en(BOGOTA, "2026-01-01T08:00:00");
            Duration unaHora = Duration.ofHours(1);

            assertThatThrownBy(() -> cerrado.sumarHoras(ahora, unaHora)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("El tiempo habil entre dos momentos")
    class Entre {

        @Test
        @DisplayName("Solo cuenta lo que cae dentro de la jornada de los dias habiles")
        void cuentaLaJornada() {
            CalendarioLaboral calendario = colombia();

            assertThat(calendario.entre(en(BOGOTA, "2026-01-09T15:00:00"), en(BOGOTA, "2026-01-13T10:00:00")))
                    .isEqualTo(Duration.ofHours(4));
            assertThat(calendario.entre(en(BOGOTA, "2026-01-13T09:00:00"), en(BOGOTA, "2026-01-13T09:45:00")))
                    .isEqualTo(Duration.ofMinutes(45));
            assertThat(calendario.entre(en(BOGOTA, "2026-01-13T18:00:00"), en(BOGOTA, "2026-01-14T07:00:00")))
                    .isZero();
        }

        @Test
        @DisplayName("Al reves es lo mismo con signo negativo")
        void alReves() {
            assertThat(colombia().entre(en(BOGOTA, "2026-01-13T10:00:00"), en(BOGOTA, "2026-01-09T15:00:00")))
                    .isEqualTo(Duration.ofHours(-4));
        }

        @Test
        @DisplayName("Sumar y medir son la misma cuenta")
        void sumarYMedir() {
            CalendarioLaboral calendario = colombia();
            Instant desde = en(BOGOTA, "2026-03-20T16:15:00");

            Instant llegada = calendario.sumarDias(desde, 3);

            assertThat(calendario.entre(desde, llegada)).isEqualTo(Duration.ofHours(27));
        }
    }

    @Nested
    @DisplayName("Lo que la tienda cambia")
    class Ediciones {

        @Test
        @DisplayName("Un dia agregado o renombrado queda festivo con su nombre; uno quitado deja de serlo")
        void agregaRenombraYQuita() {
            CalendarioLaboral calendario = new CalendarioLaboral(Pais.AU, SIDNEY, LocalTime.of(9, 0),
                    LocalTime.of(17, 0), ENTRE_SEMANA,
                    Map.of(LocalDate.of(2026, 6, 8), "King's Birthday", LocalDate.of(2026, 1, 26), "National Day",
                            LocalDate.of(2027, 6, 14), "King's Birthday"),
                    Set.of(LocalDate.of(2026, 12, 28)));

            assertThat(calendario.festivos(2026)).extracting(Festivo::fecha).containsExactly(
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 26), LocalDate.of(2026, 4, 3),
                    LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 25), LocalDate.of(2026, 6, 8),
                    LocalDate.of(2026, 12, 25), LocalDate.of(2026, 12, 26));
            assertThat(calendario.festivos(2026)).contains(
                    new Festivo(LocalDate.of(2026, 1, 26), "National Day", OrigenFestivo.EDITADO),
                    new Festivo(LocalDate.of(2026, 6, 8), "King's Birthday", OrigenFestivo.EDITADO),
                    new Festivo(LocalDate.of(2026, 1, 1), "New Year's Day", OrigenFestivo.CALCULADO));
            assertThat(calendario.esDiaHabil(LocalDate.of(2026, 12, 28))).isTrue();
            assertThat(calendario.esDiaHabil(LocalDate.of(2026, 6, 8))).isFalse();
            assertThat(calendario.festivos(2027)).extracting(Festivo::fecha).contains(LocalDate.of(2027, 6, 14));
        }

        @Test
        @DisplayName("La jornada empieza antes de terminar, y hay al menos un dia habil")
        void reglasDelCalendario() {
            LocalTime ocho = LocalTime.of(8, 0);
            Set<DayOfWeek> ninguno = Set.of();
            Map<LocalDate, String> sinRenombrados = Map.of();
            Set<LocalDate> sinQuitados = Set.of();

            assertThatThrownBy(() -> new CalendarioLaboral(Pais.CO, BOGOTA, ocho, ocho, ENTRE_SEMANA, sinRenombrados,
                    sinQuitados)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new CalendarioLaboral(Pais.CO, BOGOTA, ocho, LocalTime.of(17, 0), ninguno,
                    sinRenombrados, sinQuitados)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
