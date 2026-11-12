package com.facimus.procesos.ejecucion.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import com.facimus.procesos.common.tiempo.ExpresionDeTiempo;
import com.facimus.procesos.common.tiempo.ExpresionDeTiempo.Ciclo;
import com.facimus.procesos.common.tiempo.ExpresionDeTiempo.Duracion;
import com.facimus.procesos.gestion.service.CalendarioLaboral;
import com.facimus.procesos.modelado.model.TipoTemporizador;

/**
 * PR 68 (D35): cuando vence un temporizador. Son cuentas puras, sin reloj ni base: reciben el momento desde el que se
 * cuenta y el calendario de la tienda, y una prueba las ejerce con fechas escritas a mano.
 *
 * <p>En produccion se cuenta en el tiempo de la tienda: una duracion de calendario en su zona (P1D es el mismo reloj
 * de pared al dia siguiente, aunque cambie la hora), una habil con su jornada y sus festivos, una fecha sin zona en su
 * zona y un ciclo vuelta por vuelta.
 *
 * <p>En prueba (D8) no hay horas sino ticks, y un tick vale una hora: PT2H son dos ticks, P2D habil son dos jornadas
 * (18 ticks con la de 8 a 17) y lo que no tiene largo, una fecha o un cron, vence en el tick siguiente. Nunca menos de
 * uno: un temporizador no salta en el mismo tick en que se arma, por lo mismo que un mensaje no llega en el que sale.
 */
final class CuandoVence {

    /** Lo que vale un tick del reloj de la tienda cuando un temporizador de prueba cuenta su espera. */
    static final Duration UN_TICK = Duration.ofHours(1);

    /** Desde donde se mide el largo de una duracion de calendario en prueba: un dia sin cambio de hora. */
    private static final ZonedDateTime REFERENCIA = ZonedDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    /** Lo que mas puede durar un dia de calendario: el del cambio de hora que atrasa el reloj. */
    private static final Duration DIA_MAS_LARGO = Duration.ofHours(25);

    private CuandoVence() {
    }

    /**
     * El primer vencimiento en produccion, contado desde que se arma. Vacio solo en un ciclo que ya no tiene vueltas:
     * uno de tres que empezo hace una semana, o uno con fin que ya paso.
     */
    static Optional<Instant> primero(TipoTemporizador tipo, String expresion, Instant desde,
            CalendarioLaboral calendario) {
        return switch (tipo) {
            case DURACION -> Optional.of(sumar(ExpresionDeTiempo.duracion(expresion), desde, calendario.zona()));
            case DURACION_HABIL -> Optional.of(sumarHabil(ExpresionDeTiempo.duracionHabil(expresion), desde,
                    calendario));
            case FECHA -> Optional.of(ExpresionDeTiempo.fecha(expresion).en(calendario.zona()));
            case CICLO -> siguiente(ExpresionDeTiempo.ciclo(expresion), desde, desde, calendario.zona());
        };
    }

    /**
     * La primera vuelta de un ciclo despues de ese momento. Las que pasaron mientras nadie miraba no se recuperan: un
     * ciclo que vencio tres veces con la API caida salta una sola vez al volver, y sigue con la siguiente.
     *
     * @param ancla desde cuando cuenta un ciclo que no dice su inicio: el momento en que se armo
     */
    static Optional<Instant> siguiente(Ciclo ciclo, Instant ancla, Instant despuesDe, ZoneId zona) {
        if (ciclo.esCron()) {
            return Optional.ofNullable(ciclo.cron().next(despuesDe.atZone(zona))).map(ZonedDateTime::toInstant);
        }
        ZonedDateTime base = (ciclo.inicio() == null ? ancla : ciclo.inicio().en(zona)).atZone(zona);
        long primera = ciclo.inicio() == null ? 1 : 0;
        long ultima = ciclo.repeticiones() == null ? Long.MAX_VALUE : primera + ciclo.repeticiones() - 1;
        Instant fin = ciclo.fin() == null ? null : ciclo.fin().en(zona);
        for (long vuelta = Math.max(primera, saltables(base.toInstant(), despuesDe, ciclo.periodo()));
                vuelta <= ultima; vuelta++) {
            Instant vez = vuelta(base, ciclo.periodo(), vuelta);
            if (fin != null && vez.isAfter(fin)) {
                return Optional.empty();
            }
            if (vez.isAfter(despuesDe)) {
                return Optional.of(vez);
            }
        }
        return Optional.empty();
    }

    /** En prueba: en cuantos ticks vence, contados desde el tick en que se arma. */
    static int ticks(TipoTemporizador tipo, String expresion, CalendarioLaboral calendario) {
        return switch (tipo) {
            case DURACION -> enTicks(largo(ExpresionDeTiempo.duracion(expresion)));
            case DURACION_HABIL -> enTicks(largoHabil(ExpresionDeTiempo.duracionHabil(expresion), calendario));
            case FECHA -> 1;
            case CICLO -> periodoEnTicks(ExpresionDeTiempo.ciclo(expresion));
        };
    }

    /** En prueba: cada cuantos ticks vuelve a saltar un ciclo; un cron, en cada tick. */
    static int periodoEnTicks(Ciclo ciclo) {
        return ciclo.esCron() ? 1 : enTicks(largo(ciclo.periodo()));
    }

    /** Las horas que dura, redondeadas hacia arriba, y al menos una. */
    static int enTicks(Duration largo) {
        long horas = largo.toHours();
        if (largo.compareTo(UN_TICK.multipliedBy(horas)) > 0) {
            horas++;
        }
        return (int) Math.clamp(horas, 1, Integer.MAX_VALUE);
    }

    private static Instant sumar(Duracion duracion, Instant desde, ZoneId zona) {
        return desde.atZone(zona).plus(duracion.dias()).plus(duracion.tiempo()).toInstant();
    }

    /** Dos dias habiles son dos jornadas de tiempo habil: de un viernes a las tres, al martes a las tres. */
    private static Instant sumarHabil(Duracion duracion, Instant desde, CalendarioLaboral calendario) {
        return calendario.sumarHoras(desde, largoHabil(duracion, calendario));
    }

    private static Duration largoHabil(Duracion duracion, CalendarioLaboral calendario) {
        return calendario.jornada().multipliedBy(duracion.dias().getDays()).plus(duracion.tiempo());
    }

    private static Duration largo(Duracion duracion) {
        return Duration.between(REFERENCIA, REFERENCIA.plus(duracion.dias()).plus(duracion.tiempo()));
    }

    private static Instant vuelta(ZonedDateTime base, Duracion periodo, long vuelta) {
        ZonedDateTime conDias = periodo.dias().isZero() ? base
                : base.plus(periodo.dias().multipliedBy(Math.toIntExact(vuelta)));
        return conDias.plus(periodo.tiempo().multipliedBy(vuelta)).toInstant();
    }

    /**
     * Cuantas vueltas se pueden saltar sin pasarse de ese momento, contando cada periodo lo mas largo que puede ser
     * (meses de 31 dias, anos de 366, dias de 25 horas): asi un ciclo por minuto que estuvo un ano parado no se
     * recorre vuelta por vuelta, y uno mensual no se salta la que tocaba.
     */
    private static long saltables(Instant base, Instant despuesDe, Duracion periodo) {
        if (!despuesDe.isAfter(base)) {
            return 0;
        }
        long dias = periodo.dias().getDays() + 31L * periodo.dias().getMonths() + 366L * periodo.dias().getYears();
        Duration masLargo = periodo.tiempo().plus(DIA_MAS_LARGO.multipliedBy(dias));
        return Duration.between(base, despuesDe).dividedBy(masLargo);
    }
}
