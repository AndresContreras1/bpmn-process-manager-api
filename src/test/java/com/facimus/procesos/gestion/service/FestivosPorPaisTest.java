package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.facimus.procesos.gestion.model.OrigenFestivo;
import com.facimus.procesos.gestion.model.Pais;

/**
 * PR 68: los festivos de 2026 y 2027 de los cuatro paises, contra sus listas oficiales, escritas aqui a mano. Si el
 * calculo se equivoca en un solo dia (una Pascua, un lunes de la Ley Emiliani, un dia observado), la lista no coincide.
 */
class FestivosPorPaisTest {

    @ParameterizedTest
    @CsvSource({"2024,2024-03-31", "2025,2025-04-20", "2026,2026-04-05", "2027,2027-03-28", "2028,2028-04-16",
            "2038,2038-04-25", "2285,2285-03-22", "1818,1818-03-22", "2000,2000-04-23", "2100,2100-03-28"})
    @DisplayName("La Pascua sale del algoritmo anonimo gregoriano, tambien en sus extremos (22 de marzo, 25 de abril)")
    void pascua(int anio, LocalDate domingo) {
        assertThat(FestivosPorPais.pascua(anio)).isEqualTo(domingo);
    }

    /**
     * Colombia, Ley 51 de 1983 (Ley Emiliani; texto en SUIN-Juriscol, suin-juriscol.gov.co) y el calendario de festivos
     * de 2026: los de fecha fija que no se mueven, los que pasan al lunes y los de Pascua (5 de abril).
     */
    @Test
    @DisplayName("Colombia 2026: los 18 festivos de la lista oficial")
    void colombia2026() {
        assertThat(fechas(Pais.CO, 2026)).containsExactly(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 12), LocalDate.of(2026, 3, 23),
                LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 3), LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 18), LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 6, 29), LocalDate.of(2026, 7, 20), LocalDate.of(2026, 8, 7),
                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 16), LocalDate.of(2026, 12, 8), LocalDate.of(2026, 12, 25));
    }

    /** Colombia 2027, misma ley: Pascua el 28 de marzo; el 1 de noviembre cae en lunes y se queda. */
    @Test
    @DisplayName("Colombia 2027: los 18 festivos de la lista oficial")
    void colombia2027() {
        assertThat(fechas(Pais.CO, 2027)).containsExactly(
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 11), LocalDate.of(2027, 3, 22),
                LocalDate.of(2027, 3, 25), LocalDate.of(2027, 3, 26), LocalDate.of(2027, 5, 1),
                LocalDate.of(2027, 5, 10), LocalDate.of(2027, 5, 31), LocalDate.of(2027, 6, 7),
                LocalDate.of(2027, 7, 5), LocalDate.of(2027, 7, 20), LocalDate.of(2027, 8, 7),
                LocalDate.of(2027, 8, 16), LocalDate.of(2027, 10, 18), LocalDate.of(2027, 11, 1),
                LocalDate.of(2027, 11, 15), LocalDate.of(2027, 12, 8), LocalDate.of(2027, 12, 25));
    }

    @Test
    @DisplayName("Colombia: los nombres de la ley, calculados")
    void colombia_nombres() {
        assertThat(FestivosPorPais.de(Pais.CO, 2026)).contains(
                new Festivo(LocalDate.of(2026, 1, 12), "Día de los Reyes Magos", OrigenFestivo.CALCULADO),
                new Festivo(LocalDate.of(2026, 6, 15), "Sagrado Corazón de Jesús", OrigenFestivo.CALCULADO),
                new Festivo(LocalDate.of(2026, 11, 16), "Independencia de Cartagena", OrigenFestivo.CALCULADO));
    }

    /**
     * Estados Unidos, 5 U.S.C. 6103 y la lista de la Office of Personnel Management (opm.gov, "Federal Holidays"):
     * el 4 de julio de 2026 cae en sabado y se observa el viernes 3.
     */
    @Test
    @DisplayName("Estados Unidos 2026: los 11 festivos federales, con el dia observado")
    void estadosUnidos2026() {
        assertThat(fechas(Pais.US, 2026)).containsExactly(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 19), LocalDate.of(2026, 2, 16),
                LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 19), LocalDate.of(2026, 7, 3),
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 11),
                LocalDate.of(2026, 11, 26), LocalDate.of(2026, 12, 25));
    }

    /**
     * Estados Unidos 2027 (OPM): Juneteenth, el 4 de julio y Navidad caen en fin de semana y se observan; el Ano Nuevo
     * de 2028 cae en sabado y se observa el viernes 31 de diciembre de 2027.
     */
    @Test
    @DisplayName("Estados Unidos 2027: tambien el Ano Nuevo de 2028, observado el 31 de diciembre")
    void estadosUnidos2027() {
        assertThat(fechas(Pais.US, 2027)).containsExactly(
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 18), LocalDate.of(2027, 2, 15),
                LocalDate.of(2027, 5, 31), LocalDate.of(2027, 6, 18), LocalDate.of(2027, 7, 5),
                LocalDate.of(2027, 9, 6), LocalDate.of(2027, 10, 11), LocalDate.of(2027, 11, 11),
                LocalDate.of(2027, 11, 25), LocalDate.of(2027, 12, 24), LocalDate.of(2027, 12, 31));
        assertThat(FestivosPorPais.de(Pais.US, 2027)).contains(
                new Festivo(LocalDate.of(2027, 7, 5), "Independence Day (observed)", OrigenFestivo.CALCULADO),
                new Festivo(LocalDate.of(2027, 12, 31), "New Year's Day (observed)", OrigenFestivo.CALCULADO));
    }

    @Test
    @DisplayName("Estados Unidos: el Ano Nuevo observado en el ano anterior no se cuenta dos veces")
    void estadosUnidos_anoNuevoDelAnterior() {
        // El 1 de enero de 2022 cayo en sabado: se observo el 31 de diciembre de 2021, no en 2022
        assertThat(fechas(Pais.US, 2022)).doesNotContain(LocalDate.of(2022, 1, 1))
                .doesNotContain(LocalDate.of(2022, 12, 31)).contains(LocalDate.of(2022, 12, 26));
        assertThat(fechas(Pais.US, 2021)).contains(LocalDate.of(2021, 12, 31), LocalDate.of(2021, 12, 24));
    }

    /** Francia, Code du travail L3133-1 (service-public.fr, "Jours feries"): once, con Pascua el 5 de abril. */
    @Test
    @DisplayName("Francia 2026: los 11 jours feries")
    void francia2026() {
        assertThat(fechas(Pais.FR, 2026)).containsExactly(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 6), LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 8), LocalDate.of(2026, 5, 14), LocalDate.of(2026, 5, 25),
                LocalDate.of(2026, 7, 14), LocalDate.of(2026, 8, 15), LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 11), LocalDate.of(2026, 12, 25));
    }

    /** Francia 2027 (service-public.fr): Pascua el 28 de marzo, sin dias observados aunque caigan en domingo. */
    @Test
    @DisplayName("Francia 2027: los 11 jours feries, sin moverlos del fin de semana")
    void francia2027() {
        assertThat(fechas(Pais.FR, 2027)).containsExactly(
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 3, 29), LocalDate.of(2027, 5, 1),
                LocalDate.of(2027, 5, 6), LocalDate.of(2027, 5, 8), LocalDate.of(2027, 5, 17),
                LocalDate.of(2027, 7, 14), LocalDate.of(2027, 8, 15), LocalDate.of(2027, 11, 1),
                LocalDate.of(2027, 11, 11), LocalDate.of(2027, 12, 25));
    }

    /**
     * Australia, los nacionales (australia.gov.au, "Australian public holidays"): el 26 de diciembre de 2026 cae en
     * sabado y suma el lunes 28; Anzac Day en sabado no suma ninguno en la lista nacional.
     */
    @Test
    @DisplayName("Australia 2026: los nacionales, con el dia adicional de Boxing Day")
    void australia2026() {
        assertThat(fechas(Pais.AU, 2026)).containsExactly(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 26), LocalDate.of(2026, 4, 3),
                LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 25), LocalDate.of(2026, 12, 25),
                LocalDate.of(2026, 12, 26), LocalDate.of(2026, 12, 28));
        assertThat(FestivosPorPais.de(Pais.AU, 2026)).contains(
                new Festivo(LocalDate.of(2026, 12, 28), "Boxing Day (additional day)", OrigenFestivo.CALCULADO));
    }

    /**
     * Australia 2027 (australia.gov.au): Navidad en sabado suma el lunes 27, y Boxing Day en domingo suma el martes 28,
     * porque el lunes ya lo tomo Navidad.
     */
    @Test
    @DisplayName("Australia 2027: Navidad y Boxing Day en fin de semana suman lunes y martes")
    void australia2027() {
        assertThat(fechas(Pais.AU, 2027)).containsExactly(
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 26), LocalDate.of(2027, 3, 26),
                LocalDate.of(2027, 3, 29), LocalDate.of(2027, 4, 25), LocalDate.of(2027, 12, 25),
                LocalDate.of(2027, 12, 26), LocalDate.of(2027, 12, 27), LocalDate.of(2027, 12, 28));
        assertThat(FestivosPorPais.de(Pais.AU, 2027)).contains(
                new Festivo(LocalDate.of(2027, 12, 27), "Christmas Day (additional day)", OrigenFestivo.CALCULADO),
                new Festivo(LocalDate.of(2027, 12, 28), "Boxing Day (additional day)", OrigenFestivo.CALCULADO));
    }

    @Test
    @DisplayName("Australia: un Ano Nuevo en sabado suma el lunes siguiente")
    void australia_anoNuevoEnSabado() {
        assertThat(fechas(Pais.AU, 2028)).contains(LocalDate.of(2028, 1, 1), LocalDate.of(2028, 1, 3));
    }

    @Test
    @DisplayName("La Ley Emiliani pasa al lunes siguiente, y el que ya es lunes se queda")
    void alLunes() {
        assertThat(FestivosPorPais.alLunes(LocalDate.of(2026, 1, 6))).isEqualTo(LocalDate.of(2026, 1, 12));
        assertThat(FestivosPorPais.alLunes(LocalDate.of(2026, 6, 29))).isEqualTo(LocalDate.of(2026, 6, 29));
        assertThat(FestivosPorPais.alLunes(LocalDate.of(2026, 11, 1))).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    private static List<LocalDate> fechas(Pais pais, int anio) {
        return FestivosPorPais.de(pais, anio).stream().map(Festivo::fecha).toList();
    }
}
