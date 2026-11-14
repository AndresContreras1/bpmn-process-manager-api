package com.facimus.procesos.ejecucion.service.impl;

import java.time.Duration;
import java.time.Instant;

import com.facimus.procesos.ejecucion.model.Vencimiento;
import com.facimus.procesos.gestion.service.CalendarioLaboral;
import com.facimus.procesos.modelado.dto.response.PlazoResponse;
import com.facimus.procesos.modelado.model.UnidadDePlazo;

/**
 * PR 71: cuando vence un plazo y cuando entra en su ultimo cuarto. Son cuentas puras, como {@link CuandoVence}:
 * reciben el momento desde el que se cuenta y el calendario de la tienda, y una prueba las ejerce con fechas escritas a
 * mano.
 *
 * <p>En produccion, las horas son de reloj: ocho horas son ocho horas aunque esa noche cambie la hora. Los dias habiles
 * son jornadas de tiempo habil del calendario (PR 68): dos dias habiles desde un viernes a las tres son el martes a las
 * tres, y un festivo o un fin de semana no cuentan. El aviso es el mismo plazo contado hasta sus tres cuartos.
 *
 * <p>En prueba (D8) cada tick vale una hora, como en un temporizador: un dia habil son tantos ticks como horas tiene la
 * jornada, y un plazo nunca vence en el mismo tick en que se arma.
 */
final class CuandoVenceElPlazo {

    private static final int CUARTOS = 4;

    private CuandoVenceElPlazo() {
    }

    /** El de produccion, contado desde ese momento con el calendario de la tienda. */
    static Vencimiento enProduccion(PlazoResponse plazo, Instant desde, CalendarioLaboral calendario) {
        Duration largo = largo(plazo, calendario);
        Duration hastaElAviso = largo.minus(largo.dividedBy(CUARTOS));
        if (plazo.unidad() == UnidadDePlazo.HORAS) {
            return Vencimiento.enProduccion(Temporizadores.utc(desde.plus(hastaElAviso)),
                    Temporizadores.utc(desde.plus(largo)));
        }
        return Vencimiento.enProduccion(Temporizadores.utc(calendario.sumarHoras(desde, hastaElAviso)),
                Temporizadores.utc(calendario.sumarDias(desde, plazo.cantidad())));
    }

    /** El de prueba, contado en ticks desde el tick en que se arma. */
    static Vencimiento enPrueba(PlazoResponse plazo, int tick, CalendarioLaboral calendario) {
        int ticks = CuandoVence.enTicks(largo(plazo, calendario));
        return Vencimiento.enPrueba(Math.addExact(tick, ticks - ticks / CUARTOS), Math.addExact(tick, ticks));
    }

    /** Lo que dura: horas de reloj, o jornadas de tiempo habil. */
    private static Duration largo(PlazoResponse plazo, CalendarioLaboral calendario) {
        return plazo.unidad() == UnidadDePlazo.HORAS ? Duration.ofHours(plazo.cantidad())
                : calendario.jornada().multipliedBy(plazo.cantidad());
    }
}
