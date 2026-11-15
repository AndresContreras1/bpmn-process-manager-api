package com.facimus.procesos.ejecucion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.ejecucion.model.EstadoPlazo;
import com.facimus.procesos.ejecucion.model.Vencimiento;
import com.facimus.procesos.gestion.model.Pais;
import com.facimus.procesos.gestion.service.CalendarioLaboral;
import com.facimus.procesos.modelado.dto.response.PlazoResponse;
import com.facimus.procesos.modelado.model.UnidadDePlazo;

/**
 * PR 71: cuando vence un plazo, contado a mano. Fines de semana, festivos de cada pais y cambios de hora: cada fecha
 * esperada esta escrita en la hora de la tienda y en UTC, y el comentario dice por que.
 */
class CuandoVenceElPlazoTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final ZoneId NUEVA_YORK = ZoneId.of("America/New_York");
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final ZoneId SIDNEY = ZoneId.of("Australia/Sydney");
    private static final EnumSet<DayOfWeek> LUNES_A_VIERNES = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

    private static final CalendarioLaboral COLOMBIA = calendario(Pais.CO, BOGOTA, 8, 17);
    private static final CalendarioLaboral ESTADOS_UNIDOS = calendario(Pais.US, NUEVA_YORK, 9, 17);
    private static final CalendarioLaboral FRANCIA = calendario(Pais.FR, PARIS, 9, 18);
    private static final CalendarioLaboral AUSTRALIA = calendario(Pais.AU, SIDNEY, 9, 17);

    @Nested
    @DisplayName("En produccion")
    class EnProduccion {

        @Test
        @DisplayName("Dos dias habiles desde un viernes saltan el fin de semana y el lunes festivo de Reyes")
        void diasHabiles_finDeSemanaYFestivo() {
            Vencimiento vence = CuandoVenceElPlazo.enProduccion(plazo(2, UnidadDePlazo.DIAS_HABILES),
                    en("2026-01-09T15:00", BOGOTA), COLOMBIA);

            // 18 horas habiles: 2 el viernes, ninguna el sabado, el domingo ni el lunes 12, 9 el martes y 7 el
            // miercoles: el miercoles 14 a las tres de la tarde en Bogota
            assertThat(vence.getVenceEn()).isEqualTo(LocalDateTime.parse("2026-01-14T20:00:00"));
            // Tres cuartos son 13,5 horas: 2 + 9 + 2,5, el miercoles a las diez y media
            assertThat(vence.getAvisoEn()).isEqualTo(LocalDateTime.parse("2026-01-14T15:30:00"));
            assertThat(vence.getEstado()).isEqualTo(EstadoPlazo.A_TIEMPO);
            assertThat(vence.enTicks()).isFalse();
            assertThat(vence.getVenceTick()).isNull();
        }

        @Test
        @DisplayName("Ocho horas son ocho horas de reloj aunque esa noche Nueva York adelante la hora")
        void horas_cambioDeHora() {
            // El sabado 7 de marzo de 2026 a las diez de la noche (EST); el domingo 8 a las dos pasan a las tres
            Vencimiento vence = CuandoVenceElPlazo.enProduccion(plazo(8, UnidadDePlazo.HORAS),
                    en("2026-03-07T22:00", NUEVA_YORK), ESTADOS_UNIDOS);

            // Las siete de la manana del domingo (EDT): nueve horas en el reloj de pared, ocho de verdad
            assertThat(vence.getVenceEn()).isEqualTo(LocalDateTime.parse("2026-03-08T11:00:00"));
            assertThat(vence.getVenceEn().atZone(ZoneOffset.UTC).withZoneSameInstant(NUEVA_YORK)
                    .toLocalTime()).isEqualTo(LocalTime.of(7, 0));
            assertThat(vence.getAvisoEn()).isEqualTo(LocalDateTime.parse("2026-03-08T09:00:00"));
        }

        @Test
        @DisplayName("Las horas de reloj no miran el calendario: corren de noche, en fin de semana y en festivo")
        void horas_noMiranElCalendario() {
            Vencimiento vence = CuandoVenceElPlazo.enProduccion(plazo(72, UnidadDePlazo.HORAS),
                    en("2026-01-09T15:00", BOGOTA), COLOMBIA);

            assertThat(vence.getVenceEn()).isEqualTo(LocalDateTime.parse("2026-01-12T20:00:00"));
            assertThat(vence.getAvisoEn()).isEqualTo(LocalDateTime.parse("2026-01-12T02:00:00"));
        }

        @Test
        @DisplayName("Un dia habil en Paris cruza el cambio de hora de marzo: el lunes a las cuatro, ya en verano")
        void diasHabiles_cambioDeHoraEnParis() {
            // Viernes 27 de marzo de 2026 a las cuatro de la tarde (CET); el domingo 29 se adelanta la hora
            Vencimiento vence = CuandoVenceElPlazo.enProduccion(plazo(1, UnidadDePlazo.DIAS_HABILES),
                    en("2026-03-27T16:00", PARIS), FRANCIA);

            // 9 horas habiles: 2 el viernes y 7 el lunes, hasta las cuatro de la tarde (CEST, UTC+2)
            assertThat(vence.getVenceEn()).isEqualTo(LocalDateTime.parse("2026-03-30T14:00:00"));
            // Tres cuartos son 6,75 horas: 2 el viernes y 4,75 el lunes, a las dos menos cuarto
            assertThat(vence.getAvisoEn()).isEqualTo(LocalDateTime.parse("2026-03-30T11:45:00"));
        }

        @Test
        @DisplayName("En Sidney, Viernes Santo, el atraso de la hora y el Lunes de Pascua: el miercoles a las tres")
        void diasHabiles_pascuaYCambioDeHoraEnSidney() {
            // Jueves 2 de abril de 2026 a las tres (AEDT, UTC+11); el domingo 5 se atrasa la hora
            Vencimiento vence = CuandoVenceElPlazo.enProduccion(plazo(2, UnidadDePlazo.DIAS_HABILES),
                    en("2026-04-02T15:00", SIDNEY), AUSTRALIA);

            // 16 horas habiles: 2 el jueves, ninguna del viernes 3 al lunes 6, 8 el martes y 6 el miercoles (AEST)
            assertThat(vence.getVenceEn()).isEqualTo(LocalDateTime.parse("2026-04-08T05:00:00"));
            // Tres cuartos son 12: 2 + 8 + 2, el miercoles a las once
            assertThat(vence.getAvisoEn()).isEqualTo(LocalDateTime.parse("2026-04-08T01:00:00"));
        }

        @Test
        @DisplayName("Fuera de la jornada, el plazo en dias habiles empieza a contar cuando abre")
        void diasHabiles_desdeUnSabado() {
            Vencimiento vence = CuandoVenceElPlazo.enProduccion(plazo(1, UnidadDePlazo.DIAS_HABILES),
                    en("2026-01-17T10:00", BOGOTA), COLOMBIA);

            // El lunes 19 de ocho a cinco
            assertThat(vence.getVenceEn()).isEqualTo(LocalDateTime.parse("2026-01-19T22:00:00"));
            assertThat(vence.getAvisoEn()).isEqualTo(LocalDateTime.parse("2026-01-19T19:45:00"));
        }

        @Test
        @DisplayName("Una hora tiene su aviso a los 45 minutos")
        void horas_unaHora() {
            Vencimiento vence = CuandoVenceElPlazo.enProduccion(plazo(1, UnidadDePlazo.HORAS),
                    Instant.parse("2026-01-09T20:00:00Z"), COLOMBIA);

            assertThat(vence.getVenceEn()).isEqualTo(LocalDateTime.parse("2026-01-09T21:00:00"));
            assertThat(vence.getAvisoEn()).isEqualTo(LocalDateTime.parse("2026-01-09T20:45:00"));
        }
    }

    @Nested
    @DisplayName("En prueba")
    class EnPrueba {

        @Test
        @DisplayName("Cada hora es un tick, y el aviso llega con el ultimo cuarto")
        void horas() {
            Vencimiento vence = CuandoVenceElPlazo.enPrueba(plazo(8, UnidadDePlazo.HORAS), 3, COLOMBIA);

            assertThat(vence.getVenceTick()).isEqualTo(11);
            assertThat(vence.getAvisoTick()).isEqualTo(9);
            assertThat(vence.enTicks()).isTrue();
            assertThat(vence.getVenceEn()).isNull();
            assertThat(vence.getAvisoEn()).isNull();
            assertThat(vence.getEstado()).isEqualTo(EstadoPlazo.A_TIEMPO);
        }

        @Test
        @DisplayName("Un dia habil son tantos ticks como horas tiene la jornada: dos de ocho a cinco son 18")
        void diasHabiles() {
            Vencimiento vence = CuandoVenceElPlazo.enPrueba(plazo(2, UnidadDePlazo.DIAS_HABILES), 0, COLOMBIA);

            assertThat(vence.getVenceTick()).isEqualTo(18);
            assertThat(vence.getAvisoTick()).isEqualTo(14);
        }

        @Test
        @DisplayName("Un plazo de un tick vence en el siguiente, y su aviso llega con el")
        void unTick() {
            Vencimiento vence = CuandoVenceElPlazo.enPrueba(plazo(1, UnidadDePlazo.HORAS), 5, COLOMBIA);

            assertThat(vence.getVenceTick()).isEqualTo(6);
            assertThat(vence.getAvisoTick()).isEqualTo(6);
        }

        @Test
        @DisplayName("Con cuatro ticks, el aviso es en el tercero")
        void cuatroTicks() {
            Vencimiento vence = CuandoVenceElPlazo.enPrueba(plazo(4, UnidadDePlazo.HORAS), 0, COLOMBIA);

            assertThat(vence.getAvisoTick()).isEqualTo(3);
            assertThat(vence.getVenceTick()).isEqualTo(4);
        }
    }

    private static PlazoResponse plazo(int cantidad, UnidadDePlazo unidad) {
        return new PlazoResponse(cantidad, unidad);
    }

    private static Instant en(String hora, ZoneId zona) {
        return LocalDateTime.parse(hora).atZone(zona).toInstant();
    }

    private static CalendarioLaboral calendario(Pais pais, ZoneId zona, int abre, int cierra) {
        return new CalendarioLaboral(pais, zona, LocalTime.of(abre, 0), LocalTime.of(cierra, 0), LUNES_A_VIERNES,
                Map.of(), Set.of());
    }
}
