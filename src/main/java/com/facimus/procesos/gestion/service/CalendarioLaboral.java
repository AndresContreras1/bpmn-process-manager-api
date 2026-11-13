package com.facimus.procesos.gestion.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.facimus.procesos.gestion.model.OrigenFestivo;
import com.facimus.procesos.gestion.model.Pais;

/**
 * PR 68 (D35): el calendario laboral de una tienda, ya armado para contar con el: que dias son festivos, cuando se
 * trabaja y cuanto tiempo habil hay entre dos momentos. El tiempo habil corre solo dentro de la jornada de un dia
 * habil que no es festivo, en la zona horaria de la tienda: un cambio de hora cae siempre en domingo de madrugada y
 * no toca la jornada, pero si cayera dentro, la jornada de ese dia duraria lo que dura de verdad.
 *
 * <p>Un dia habil son tantas horas habiles como dura la jornada: sumar un dia a un viernes a las tres de la tarde da
 * el lunes a las tres, y sumarlo a un sabado da el lunes al cerrar. Es inmutable salvo por los festivos de cada ano,
 * que se calculan la primera vez que se preguntan.
 */
public final class CalendarioLaboral {

    /** Diez anos de dias sin un solo momento habil no son un calendario: es uno que nunca deja trabajar. */
    private static final int DIAS_MAXIMOS = 3660;

    private final Pais pais;
    private final ZoneId zona;
    private final LocalTime inicio;
    private final LocalTime fin;
    private final Set<DayOfWeek> diasHabiles;
    private final Map<LocalDate, String> renombrados;
    private final Set<LocalDate> quitados;
    private final Map<Integer, Map<LocalDate, Festivo>> porAnio = new ConcurrentHashMap<>();

    /**
     * @param renombrados los dias que la tienda volvio festivos, o cuyo festivo renombro, con su nombre
     * @param quitados los dias cuyo festivo calculado la tienda quito
     */
    public CalendarioLaboral(Pais pais, ZoneId zona, LocalTime inicio, LocalTime fin, Set<DayOfWeek> diasHabiles,
            Map<LocalDate, String> renombrados, Set<LocalDate> quitados) {
        if (!inicio.isBefore(fin)) {
            throw new IllegalArgumentException("La jornada empieza antes de terminar.");
        }
        if (diasHabiles.isEmpty()) {
            throw new IllegalArgumentException("Un calendario tiene al menos un dia habil.");
        }
        this.pais = pais;
        this.zona = zona;
        this.inicio = inicio;
        this.fin = fin;
        this.diasHabiles = Set.copyOf(EnumSet.copyOf(diasHabiles));
        this.renombrados = Map.copyOf(renombrados);
        this.quitados = Set.copyOf(quitados);
    }

    public Pais pais() {
        return pais;
    }

    public ZoneId zona() {
        return zona;
    }

    /** Lo que dura la jornada de un dia habil: lo que vale un dia habil al sumar. */
    public Duration jornada() {
        return Duration.between(inicio, fin);
    }

    /** Los festivos de ese ano, con lo que la tienda cambio, en orden. */
    public List<Festivo> festivos(int anio) {
        List<Festivo> festivos = new ArrayList<>(delAnio(anio).values());
        festivos.sort(Comparator.comparing(Festivo::fecha));
        return festivos;
    }

    public boolean esFestivo(LocalDate fecha) {
        return delAnio(fecha.getYear()).containsKey(fecha);
    }

    /** Un dia habil de la semana que no es festivo. */
    public boolean esDiaHabil(LocalDate fecha) {
        return diasHabiles.contains(fecha.getDayOfWeek()) && !esFestivo(fecha);
    }

    /** Si en ese momento se esta trabajando. */
    public boolean esHabil(Instant momento) {
        LocalDate dia = LocalDate.ofInstant(momento, zona);
        return esDiaHabil(dia) && !momento.isBefore(abre(dia)) && momento.isBefore(cierra(dia));
    }

    /** El mismo momento si es habil; si no, cuando abre el siguiente dia habil. */
    public Instant siguienteMomentoHabil(Instant momento) {
        return sumarHoras(momento, Duration.ZERO);
    }

    /**
     * El momento en que se cumplen esas horas habiles contadas desde ese. Lo que cae fuera de la jornada, en fin de
     * semana o en festivo no cuenta; si se cumplen justo al cerrar, es el cierre, no la apertura del dia siguiente.
     */
    public Instant sumarHoras(Instant desde, Duration horas) {
        if (horas.isNegative()) {
            throw new IllegalArgumentException("El tiempo habil se suma hacia adelante.");
        }
        Duration resto = horas;
        LocalDate dia = LocalDate.ofInstant(desde, zona);
        for (int vuelta = 0; vuelta < DIAS_MAXIMOS; vuelta++, dia = dia.plusDays(1)) {
            if (!esDiaHabil(dia)) {
                continue;
            }
            Instant desdeAqui = desde.isAfter(abre(dia)) ? desde : abre(dia);
            Instant cierre = cierra(dia);
            if (desdeAqui.isBefore(cierre)) {
                Duration queda = Duration.between(desdeAqui, cierre);
                if (resto.compareTo(queda) <= 0) {
                    return desdeAqui.plus(resto);
                }
                resto = resto.minus(queda);
            }
        }
        throw new IllegalStateException("El calendario no tiene tiempo habil en diez anos.");
    }

    /** Esos dias habiles desde ese momento: tantas jornadas de tiempo habil. */
    public Instant sumarDias(Instant desde, long dias) {
        if (dias < 0) {
            throw new IllegalArgumentException("Los dias habiles se suman hacia adelante.");
        }
        return sumarHoras(desde, jornada().multipliedBy(dias));
    }

    /** El tiempo habil entre dos momentos; negativo si el segundo es anterior. */
    public Duration entre(Instant desde, Instant hasta) {
        return hasta.isBefore(desde) ? habilEntre(hasta, desde).negated() : habilEntre(desde, hasta);
    }

    /** El tiempo habil entre dos momentos ya en orden. */
    private Duration habilEntre(Instant antes, Instant despues) {
        Duration total = Duration.ZERO;
        LocalDate ultimo = LocalDate.ofInstant(despues, zona);
        for (LocalDate dia = LocalDate.ofInstant(antes, zona); !dia.isAfter(ultimo); dia = dia.plusDays(1)) {
            if (esDiaHabil(dia)) {
                Instant de = antes.isAfter(abre(dia)) ? antes : abre(dia);
                Instant a = despues.isBefore(cierra(dia)) ? despues : cierra(dia);
                if (de.isBefore(a)) {
                    total = total.plus(Duration.between(de, a));
                }
            }
        }
        return total;
    }

    private Instant abre(LocalDate dia) {
        return dia.atTime(inicio).atZone(zona).toInstant();
    }

    private Instant cierra(LocalDate dia) {
        return dia.atTime(fin).atZone(zona).toInstant();
    }

    private Map<LocalDate, Festivo> delAnio(int anio) {
        return porAnio.computeIfAbsent(anio, this::armar);
    }

    private Map<LocalDate, Festivo> armar(int anio) {
        Map<LocalDate, Festivo> festivos = new HashMap<>();
        FestivosPorPais.de(pais, anio).forEach(festivo -> festivos.put(festivo.fecha(), festivo));
        quitados.forEach(festivos::remove);
        renombrados.forEach((fecha, nombre) -> {
            if (fecha.getYear() == anio) {
                festivos.put(fecha, new Festivo(fecha, nombre, OrigenFestivo.EDITADO));
            }
        });
        return Map.copyOf(festivos);
    }
}
