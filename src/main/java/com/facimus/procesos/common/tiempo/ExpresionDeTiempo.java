package com.facimus.procesos.common.tiempo;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;

import org.springframework.scheduling.support.CronExpression;

/**
 * PR 68: cuando salta un temporizador, leido de la expresion que BPMN guarda en el evento. Lo comparten el
 * diagnostico, que dice si se entiende, y el motor, que con ella calcula cuando vence: la gramatica es una sola, como
 * la de las condiciones (D6).
 *
 * <ul>
 * <li>una duracion ISO 8601: {@code PT2H}, {@code P3D}, {@code P1DT2H}, {@code P1M};</li>
 * <li>una duracion habil, que solo cuenta dias y horas: {@code P2D} son dos dias habiles, {@code PT4H} cuatro horas
 * de jornada;</li>
 * <li>una fecha y hora ISO 8601, con zona o sin ella: sin zona se lee en la de la tienda;</li>
 * <li>un ciclo: un intervalo que se repite ({@code R3/PT1H}, {@code R/2026-01-01T08:00:00Z/P1D},
 * {@code R2/PT1H/2026-01-01T08:00:00Z}) o una expresion cron de seis campos ({@code 0 0 9 * * MON-FRI}).</li>
 * </ul>
 *
 * <p>Con topes, para que lo que el diagnostico acepta se pueda armar siempre y no se coma a la tienda: una duracion
 * no pasa de cien anos, una habil de 366 jornadas (o 366 dias de horas de jornada), una fecha va de 1900 a 9999 y un
 * ciclo no salta mas de una vez por minuto.
 */
public final class ExpresionDeTiempo {

    private static final String EN_ISO = " en ISO 8601.";
    private static final String UN_CICLO = "un ciclo";
    private static final char PERIODO = 'P';
    private static final char TIEMPO = 'T';
    private static final char REPETIR = 'R';

    /** Cien anos contados por lo largo, con meses de 31 dias: P100Y cabe, P100Y1D ya no. */
    private static final long DIAS_DE_CIEN_ANOS = 31L * 12 * 100;
    private static final int JORNADAS_MAXIMAS = 366;
    private static final int PRIMER_ANIO = 1900;
    private static final int ULTIMO_ANIO = 9999;
    private static final Duration VUELTA_MINIMA = Duration.ofMinutes(1);

    private ExpresionDeTiempo() {
    }

    /**
     * Una duracion: los dias (o anos, meses y semanas) cuentan como calendario y el resto como horas, minutos y
     * segundos. Una habil solo tiene dias y tiempo.
     */
    public record Duracion(Period dias, Duration tiempo, boolean habil) {

        /** Una duracion que no espera nada: el temporizador vence en cuanto se arma. */
        public boolean esCero() {
            return dias.isZero() && tiempo.isZero();
        }
    }

    /** Una fecha y hora. Sin zona, vale la de quien la lee: la de la tienda. */
    public record Fecha(LocalDateTime local, ZoneId zona) {

        public Instant en(ZoneId deLaTienda) {
            return local.atZone(zona == null ? deLaTienda : zona).toInstant();
        }
    }

    /**
     * Un ciclo. En ISO 8601 se repite cada periodo desde el inicio (si lo dice) o desde que se arma, y si dice un fin,
     * no pasa de el; una expresion cron dice ella misma cuando.
     *
     * @param repeticiones cuantas veces salta; vacio si no tiene fin
     */
    public record Ciclo(Integer repeticiones, Fecha inicio, Duracion periodo, Fecha fin, CronExpression cron) {

        public boolean esCron() {
            return cron != null;
        }
    }

    public static Duracion duracion(String texto) {
        String limpio = limpiar(texto);
        return acotada(limpio, leerDuracion(limpio, false));
    }

    public static Duracion duracionHabil(String texto) {
        String limpio = limpiar(texto);
        return acotada(limpio, leerDuracion(limpio, true));
    }

    public static Fecha fecha(String texto) {
        String limpio = limpiar(texto);
        return acotada(limpio, leerFecha(limpio));
    }

    public static Ciclo ciclo(String texto) {
        String limpio = limpiar(texto);
        if (!limpio.isEmpty() && limpio.charAt(0) == REPETIR) {
            return intervalo(limpio);
        }
        CronExpression cron;
        try {
            cron = CronExpression.parse(limpio);
        } catch (IllegalArgumentException _) {
            throw mal(limpio, UN_CICLO + " en ISO 8601 ni una expresion cron.");
        }
        if (limpio.charAt(0) != '@' && !soloCifras(limpio.substring(0, finDelPrimerCampo(limpio)))) {
            throw new ExpresionMalEscrita("\"" + limpio + "\" saltaria mas de una vez por minuto: los segundos van "
                    + "fijos, como en 0 0 9 * * MON-FRI.");
        }
        return new Ciclo(null, null, null, null, cron);
    }

    /** R, con o sin cuantas veces, y uno o dos tramos: un periodo, o un inicio y un periodo, o un periodo y un fin. */
    private static Ciclo intervalo(String limpio) {
        String[] partes = limpio.split("/", -1);
        if (partes.length < 2 || partes.length > 3) {
            throw mal(limpio, UN_CICLO + EN_ISO);
        }
        Integer repeticiones = repeticiones(limpio, partes[0]);
        Fecha inicio = null;
        Fecha fin = null;
        Duracion periodo;
        try {
            if (partes.length == 2) {
                periodo = leerDuracion(limpiar(partes[1]), false);
            } else if (partes[1].indexOf(PERIODO) == 0) {
                periodo = leerDuracion(limpiar(partes[1]), false);
                fin = leerFecha(limpiar(partes[2]));
            } else {
                inicio = leerFecha(limpiar(partes[1]));
                periodo = leerDuracion(limpiar(partes[2]), false);
            }
        } catch (ExpresionMalEscrita _) {
            throw mal(limpio, UN_CICLO + EN_ISO);
        }
        if (periodo.esCero()) {
            throw new ExpresionMalEscrita("\"" + limpio + "\" se repetiria sin esperar nada: el periodo no puede ser "
                    + "cero.");
        }
        if (periodo.dias().isZero() && periodo.tiempo().compareTo(VUELTA_MINIMA) < 0) {
            throw new ExpresionMalEscrita("\"" + limpio + "\" saltaria mas de una vez por minuto: el periodo es de "
                    + "un minuto o mas.");
        }
        acotada(limpio, periodo);
        if (inicio != null) {
            acotada(limpio, inicio);
        }
        if (fin != null) {
            acotada(limpio, fin);
        }
        return new Ciclo(repeticiones, inicio, periodo, fin, null);
    }

    /** {@code R} sin numero no tiene fin; {@code R3}, tres veces; {@code R0} o {@code Rx} no son un ciclo. */
    private static Integer repeticiones(String limpio, String parte) {
        if (parte.length() == 1) {
            return null;
        }
        int veces = 0;
        for (int i = 1; i < parte.length(); i++) {
            char cifra = parte.charAt(i);
            if (cifra < '0' || cifra > '9' || veces > 100_000) {
                throw mal(limpio, UN_CICLO + EN_ISO);
            }
            veces = veces * 10 + (cifra - '0');
        }
        if (veces == 0) {
            throw mal(limpio, UN_CICLO + EN_ISO);
        }
        return veces;
    }

    /**
     * Partida por la T a mano, porque {@link Duration} no sabe de meses y {@link Period} no sabe de horas. Una habil
     * no dice anos, meses ni semanas: un mes habil no es una cantidad de jornadas.
     */
    private static Duracion leerDuracion(String limpio, boolean habil) {
        String que = habil ? "una duracion habil" : "una duracion";
        if (limpio.length() < 2 || limpio.charAt(0) != PERIODO) {
            throw mal(limpio, que + EN_ISO);
        }
        int t = limpio.indexOf(TIEMPO);
        String fecha = t < 0 ? limpio : limpio.substring(0, t);
        String hora = t < 0 ? "" : limpio.substring(t);
        if (hora.length() == 1) {
            throw mal(limpio, que + EN_ISO);
        }
        Period dias;
        Duration tiempo;
        try {
            dias = fecha.length() > 1 ? Period.parse(fecha) : Period.ZERO;
            tiempo = hora.isEmpty() ? Duration.ZERO : Duration.parse(PERIODO + hora);
        } catch (DateTimeException _) {
            throw mal(limpio, que + EN_ISO);
        }
        if (dias.isNegative() || tiempo.isNegative()) {
            throw new ExpresionMalEscrita("\"" + limpio + "\" es una duracion hacia atras.");
        }
        if (habil && (dias.getYears() != 0 || dias.getMonths() != 0 || fecha.indexOf('W') >= 0)) {
            throw new ExpresionMalEscrita("\"" + limpio + "\" no es una duracion habil: cuenta dias y horas de "
                    + "jornada (P2D, PT4H), no anos, meses ni semanas.");
        }
        return new Duracion(dias, tiempo, habil);
    }

    private static Fecha leerFecha(String limpio) {
        try {
            TemporalAccessor leido = DateTimeFormatter.ISO_DATE_TIME.parse(limpio);
            return new Fecha(LocalDateTime.from(leido), leido.query(TemporalQueries.zone()));
        } catch (DateTimeException _) {
            throw mal(limpio, "una fecha y hora" + EN_ISO);
        }
    }

    /** Las duraciones con su tope: cien anos de calendario, o 366 jornadas de tiempo habil. */
    private static Duracion acotada(String limpio, Duracion duracion) {
        if (duracion.habil()) {
            if (duracion.dias().getDays() > JORNADAS_MAXIMAS
                    || duracion.tiempo().compareTo(Duration.ofDays(JORNADAS_MAXIMAS)) > 0) {
                throw new ExpresionMalEscrita("\"" + limpio + "\" pasa de " + JORNADAS_MAXIMAS + " jornadas: es el "
                        + "tope de una duracion habil.");
            }
            return duracion;
        }
        // Por partes y en ese orden: sumar antes de saber que cada una cabe podria desbordar la cuenta
        long dias = 31L * duracion.dias().toTotalMonths() + duracion.dias().getDays();
        Duration tope = Duration.ofDays(DIAS_DE_CIEN_ANOS);
        if (dias > DIAS_DE_CIEN_ANOS || duracion.tiempo().compareTo(tope) > 0
                || Duration.ofDays(dias).plus(duracion.tiempo()).compareTo(tope) > 0) {
            throw new ExpresionMalEscrita("\"" + limpio + "\" pasa de cien anos: es el tope de una duracion.");
        }
        return duracion;
    }

    private static Fecha acotada(String limpio, Fecha fecha) {
        int anio = fecha.local().getYear();
        if (anio < PRIMER_ANIO || anio > ULTIMO_ANIO) {
            throw new ExpresionMalEscrita("\"" + limpio + "\" cae fuera de los anos que se cuentan: de "
                    + PRIMER_ANIO + " a " + ULTIMO_ANIO + ".");
        }
        return fecha;
    }

    private static int finDelPrimerCampo(String limpio) {
        int espacio = limpio.indexOf(' ');
        return espacio < 0 ? limpio.length() : espacio;
    }

    private static boolean soloCifras(String campo) {
        for (int i = 0; i < campo.length(); i++) {
            if (campo.charAt(i) < '0' || campo.charAt(i) > '9') {
                return false;
            }
        }
        return !campo.isEmpty();
    }

    private static String limpiar(String texto) {
        return texto == null ? "" : texto.strip();
    }

    private static ExpresionMalEscrita mal(String limpio, String loQueNoEs) {
        return new ExpresionMalEscrita("\"" + limpio + "\" no es " + loQueNoEs);
    }
}
