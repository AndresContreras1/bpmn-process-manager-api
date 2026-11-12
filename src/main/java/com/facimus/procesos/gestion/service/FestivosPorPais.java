package com.facimus.procesos.gestion.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.facimus.procesos.gestion.model.Pais;

/**
 * PR 68: los festivos que la ley de cada pais pone en un ano, calculados y no copiados de una tabla: asi valen para
 * cualquier ano sin que nadie los cargue. La Pascua sale del algoritmo anonimo gregoriano (Meeus, Jones y Butcher), y
 * de ella los festivos moviles.
 */
public final class FestivosPorPais {

    private static final String ADICIONAL = " (additional day)";
    private static final String OBSERVADO = " (observed)";
    private static final String ANZAC = "Anzac Day";

    private FestivosPorPais() {
    }

    /** Los festivos de ese pais en ese ano, en orden. */
    public static List<Festivo> de(Pais pais, int anio) {
        List<Festivo> festivos = switch (pais) {
            case CO -> colombia(anio);
            case US -> estadosUnidos(anio);
            case FR -> francia(anio);
            case AU -> australia(anio);
        };
        return festivos.stream().sorted(Comparator.comparing(Festivo::fecha)).toList();
    }

    /** El domingo de Pascua de ese ano, en el calendario gregoriano. */
    public static LocalDate pascua(int anio) {
        int a = anio % 19;
        int b = anio / 100;
        int c = anio % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(anio, mes, dia);
    }

    /**
     * Ley 51 de 1983: los de fecha fija que no se mueven, los que se pasan al lunes siguiente (Ley Emiliani), y los de
     * Pascua: jueves y viernes santo quietos, y Ascension, Corpus Christi y Sagrado Corazon pasados al lunes.
     */
    private static List<Festivo> colombia(int anio) {
        LocalDate pascua = pascua(anio);
        List<Festivo> festivos = new ArrayList<>();
        festivos.add(Festivo.calculado(LocalDate.of(anio, Month.JANUARY, 1), "Año Nuevo"));
        festivos.add(Festivo.calculado(alLunes(LocalDate.of(anio, Month.JANUARY, 6)), "Día de los Reyes Magos"));
        festivos.add(Festivo.calculado(alLunes(LocalDate.of(anio, Month.MARCH, 19)), "Día de San José"));
        festivos.add(Festivo.calculado(pascua.minusDays(3), "Jueves Santo"));
        festivos.add(Festivo.calculado(pascua.minusDays(2), "Viernes Santo"));
        festivos.add(Festivo.calculado(LocalDate.of(anio, Month.MAY, 1), "Día del Trabajo"));
        festivos.add(Festivo.calculado(alLunes(pascua.plusDays(39)), "Ascensión del Señor"));
        festivos.add(Festivo.calculado(alLunes(pascua.plusDays(60)), "Corpus Christi"));
        festivos.add(Festivo.calculado(alLunes(pascua.plusDays(68)), "Sagrado Corazón de Jesús"));
        festivos.add(Festivo.calculado(alLunes(LocalDate.of(anio, Month.JUNE, 29)), "San Pedro y San Pablo"));
        festivos.add(Festivo.calculado(LocalDate.of(anio, Month.JULY, 20), "Día de la Independencia"));
        festivos.add(Festivo.calculado(LocalDate.of(anio, Month.AUGUST, 7), "Batalla de Boyacá"));
        festivos.add(Festivo.calculado(alLunes(LocalDate.of(anio, Month.AUGUST, 15)), "Asunción de la Virgen"));
        festivos.add(Festivo.calculado(alLunes(LocalDate.of(anio, Month.OCTOBER, 12)), "Día de la Raza"));
        festivos.add(Festivo.calculado(alLunes(LocalDate.of(anio, Month.NOVEMBER, 1)), "Todos los Santos"));
        festivos.add(Festivo.calculado(alLunes(LocalDate.of(anio, Month.NOVEMBER, 11)),
                "Independencia de Cartagena"));
        festivos.add(Festivo.calculado(LocalDate.of(anio, Month.DECEMBER, 8), "Inmaculada Concepción"));
        festivos.add(Festivo.calculado(LocalDate.of(anio, Month.DECEMBER, 25), "Navidad"));
        return festivos;
    }

    /**
     * 5 U.S.C. 6103: los de fecha fija se observan el viernes si caen en sabado y el lunes si caen en domingo. Por eso
     * el Ano Nuevo del ano siguiente puede caer el 31 de diciembre de este, y el de este, en el anterior.
     */
    private static List<Festivo> estadosUnidos(int anio) {
        List<Festivo> festivos = new ArrayList<>(federales(anio));
        festivos.addAll(federales(anio + 1));
        return festivos.stream().filter(festivo -> festivo.fecha().getYear() == anio).toList();
    }

    private static List<Festivo> federales(int anio) {
        List<Festivo> festivos = new ArrayList<>();
        festivos.add(observado(LocalDate.of(anio, Month.JANUARY, 1), "New Year's Day"));
        festivos.add(Festivo.calculado(enesimo(anio, Month.JANUARY, 3, DayOfWeek.MONDAY),
                "Birthday of Martin Luther King, Jr."));
        festivos.add(Festivo.calculado(enesimo(anio, Month.FEBRUARY, 3, DayOfWeek.MONDAY), "Washington's Birthday"));
        festivos.add(Festivo.calculado(LocalDate.of(anio, Month.MAY, 1)
                .with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY)), "Memorial Day"));
        festivos.add(observado(LocalDate.of(anio, Month.JUNE, 19), "Juneteenth National Independence Day"));
        festivos.add(observado(LocalDate.of(anio, Month.JULY, 4), "Independence Day"));
        festivos.add(Festivo.calculado(enesimo(anio, Month.SEPTEMBER, 1, DayOfWeek.MONDAY), "Labor Day"));
        festivos.add(Festivo.calculado(enesimo(anio, Month.OCTOBER, 2, DayOfWeek.MONDAY), "Columbus Day"));
        festivos.add(observado(LocalDate.of(anio, Month.NOVEMBER, 11), "Veterans Day"));
        festivos.add(Festivo.calculado(enesimo(anio, Month.NOVEMBER, 4, DayOfWeek.THURSDAY), "Thanksgiving Day"));
        festivos.add(observado(LocalDate.of(anio, Month.DECEMBER, 25), "Christmas Day"));
        return festivos;
    }

    /** Code du travail, L3133-1: los once jours feries, sin dias observados. */
    private static List<Festivo> francia(int anio) {
        LocalDate paques = pascua(anio);
        return List.of(
                Festivo.calculado(LocalDate.of(anio, Month.JANUARY, 1), "Jour de l'an"),
                Festivo.calculado(paques.plusDays(1), "Lundi de Pâques"),
                Festivo.calculado(LocalDate.of(anio, Month.MAY, 1), "Fête du Travail"),
                Festivo.calculado(LocalDate.of(anio, Month.MAY, 8), "Victoire 1945"),
                Festivo.calculado(paques.plusDays(39), "Ascension"),
                Festivo.calculado(paques.plusDays(50), "Lundi de Pentecôte"),
                Festivo.calculado(LocalDate.of(anio, Month.JULY, 14), "Fête nationale"),
                Festivo.calculado(LocalDate.of(anio, Month.AUGUST, 15), "Assomption"),
                Festivo.calculado(LocalDate.of(anio, Month.NOVEMBER, 1), "Toussaint"),
                Festivo.calculado(LocalDate.of(anio, Month.NOVEMBER, 11), "Armistice 1918"),
                Festivo.calculado(LocalDate.of(anio, Month.DECEMBER, 25), "Noël"));
    }

    /**
     * Los que tienen todos los estados y territorios. Si Ano Nuevo, el Dia de Australia, Navidad o el 26 de diciembre
     * caen en fin de semana, se suma el siguiente dia entre semana libre como dia adicional; Anzac Day no lo suma en
     * todos los estados, asi que aqui no lo hace. El cumpleanos del rey y los demas de cada estado se agregan a mano.
     */
    private static List<Festivo> australia(int anio) {
        LocalDate easter = pascua(anio);
        List<Festivo> festivos = new ArrayList<>(List.of(
                Festivo.calculado(LocalDate.of(anio, Month.JANUARY, 1), "New Year's Day"),
                Festivo.calculado(LocalDate.of(anio, Month.JANUARY, 26), "Australia Day"),
                Festivo.calculado(easter.minusDays(2), "Good Friday"),
                Festivo.calculado(easter.plusDays(1), "Easter Monday"),
                Festivo.calculado(LocalDate.of(anio, Month.APRIL, 25), ANZAC),
                Festivo.calculado(LocalDate.of(anio, Month.DECEMBER, 25), "Christmas Day"),
                Festivo.calculado(LocalDate.of(anio, Month.DECEMBER, 26), "Boxing Day")));
        Set<LocalDate> tomados = new HashSet<>();
        festivos.forEach(festivo -> tomados.add(festivo.fecha()));
        List<Festivo> adicionales = new ArrayList<>();
        for (Festivo festivo : festivos) {
            if (!ANZAC.equals(festivo.nombre()) && esFinDeSemana(festivo.fecha())) {
                LocalDate otro = festivo.fecha().plusDays(1);
                while (esFinDeSemana(otro) || tomados.contains(otro)) {
                    otro = otro.plusDays(1);
                }
                tomados.add(otro);
                adicionales.add(Festivo.calculado(otro, festivo.nombre() + ADICIONAL));
            }
        }
        festivos.addAll(adicionales);
        return festivos;
    }

    /** Ley Emiliani: el que no cae en lunes se pasa al lunes siguiente. */
    static LocalDate alLunes(LocalDate fecha) {
        return fecha.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    private static LocalDate enesimo(int anio, Month mes, int cual, DayOfWeek dia) {
        return LocalDate.of(anio, mes, 1).with(TemporalAdjusters.dayOfWeekInMonth(cual, dia));
    }

    /** El sabado se observa el viernes anterior y el domingo, el lunes siguiente. */
    private static Festivo observado(LocalDate fecha, String nombre) {
        return switch (fecha.getDayOfWeek()) {
            case SATURDAY -> Festivo.calculado(fecha.minusDays(1), nombre + OBSERVADO);
            case SUNDAY -> Festivo.calculado(fecha.plusDays(1), nombre + OBSERVADO);
            default -> Festivo.calculado(fecha, nombre);
        };
    }

    private static boolean esFinDeSemana(LocalDate fecha) {
        return fecha.getDayOfWeek() == DayOfWeek.SATURDAY || fecha.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}
