package com.facimus.procesos.common.formularios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * PR 69: los patrones de los campos los escribe un usuario. RE2/J los evalua en tiempo lineal, rechaza lo que necesita
 * vuelta atras y, antes de compilar, se rechazan las repeticiones anidadas que se comerian la memoria.
 */
class PatronesTest {

    /** Lo que tarda de sobra una respuesta de la API; una expresion catastrofica en java.util.regex tarda anos. */
    private static final Duration RAPIDO = Duration.ofSeconds(2);

    @Test
    @DisplayName("Un patron normal sirve y se cumple con el texto entero, no con un pedazo")
    void patronNormal() {
        assertThat(Patrones.problema("[A-Z]{3}-\\d{4}")).isEmpty();

        assertThat(Patrones.cumple("[A-Z]{3}-\\d{4}", "ABC-1234")).isTrue();
        assertThat(Patrones.cumple("[A-Z]{3}-\\d{4}", "xABC-1234")).isFalse();
        assertThat(Patrones.cumple("[A-Z]{3}-\\d{4}", "ABC-12345")).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"(a)\\1", "(?=a)b", "(?<=a)b", "(?!a)b", "[a-", "(abc"})
    @DisplayName("Lo que RE2 no tiene (referencias atras, miradas) o esta mal escrito no sirve, y dice por que")
    void loQueRe2NoAdmite(String patron) {
        assertThat(Patrones.problema(patron)).hasValueSatisfying(
                problema -> assertThat(problema).startsWith("El patron no es una expresion regular RE2: "));
    }

    @Test
    @DisplayName("Un patron de mas de 200 caracteres no sirve; uno de 200 si")
    void largo() {
        assertThat(Patrones.problema("a".repeat(200))).isEmpty();
        assertThat(Patrones.problema("a".repeat(201))).contains("El patron no puede superar 200 caracteres.");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"((a{1000}){1000}){1000}", "(a{999}){999}", "((a{100}){100}){100}", "(a{2}){501}",
        "([a-z]{50}){21}", "(?:(?:x{10}){10}){11}", "(\\Q)\\Ea{1000}){1000}",
        "((\\Q)\\E\\Q)\\Ea{1000}){1000}){1000}", "(a{1000})(?i){1000}", "(a{1000})\\Q\\E{1000}",
        "(a{100})(?i)(?s-m){100}"})
    @DisplayName("Repeticiones anidadas que se comerian la memoria se rechazan sin compilar, y rapido")
    void repeticionesAnidadas(String patron) {
        assertTimeoutPreemptively(RAPIDO, () -> assertThat(Patrones.problema(patron)).hasValueSatisfying(
                problema -> assertThat(problema).startsWith("El patron repite demasiado")));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"(a+)+$", "^(a|aa)+$", "(x+x+)+y", "(\\w+\\s?)*$", "^(([a-z])+.)+[A-Z]([a-z])+$"})
    @DisplayName("Las expresiones catastroficas de siempre responden rapido sobre un texto largo que no las cumple")
    void catastroficasEnTiempoLineal(String patron) {
        String texto = "a".repeat(10_000) + "!";
        assertThat(Patrones.problema(patron)).isEmpty();

        assertTimeoutPreemptively(RAPIDO, () -> assertThat(Patrones.cumple(patron, texto)).isFalse());
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource(delimiter = ';', value = {
        "abc; 1",
        "a{10}; 10",
        "a{3,7}; 7",
        "a{4,}; 4",
        "(a{10}b){20}; 200",
        "(a{10})(b{20}); 20",
        "((a{2}){3}){4}; 24",
        "[a{9}]{2}; 2",
        "\\{5}; 1",
        "a\\{5}; 1",
        "a{,5}; 1",
        "a{x}; 1",
        "a{5; 1",
        "a{1234567}; 1",
        "a{999}{2}; 1001",
        "(?:a){1000}; 1000",
        "[]a]{3}; 3",
        "[^]a]{3}; 3",
        "[\\]]{4}; 4",
        ")a{5}; 5",
        "a{5}); 5",
        "\\Q(\\E{5}; 5",
        "\\Q)\\Ea{3}; 3",
        "(\\Q)\\Ea{3}){2}; 6",
        "\\Qa{9}; 1",
        "\\Qa{9}\\E; 1",
        "(a{3})\\Q\\E{2}; 6",
        "(a{3})\\Qx\\E{2}; 3",
        "(a{3})(?i){2}; 6",
        "(a{3})(?i:){2}; 3",
        "(?i:a{3}){2}; 6",
        "(?P<n>a{3}){2}; 6"})
    @DisplayName("Cuanto repite un patron: lo contado, multiplicado por lo que lo contiene")
    void cuantoRepite(String patron, long veces) {
        assertThat(Patrones.repeticiones(patron)).isEqualTo(veces);
    }

    @Test
    @DisplayName("Un texto entre \\Q y \\E sirve y se cumple tal cual, parentesis incluidos")
    void textoCitado() {
        assertThat(Patrones.problema("\\Q(+57)\\E \\d{10}")).isEmpty();

        assertThat(Patrones.cumple("\\Q(+57)\\E \\d{10}", "(+57) 3001234567")).isTrue();
        assertThat(Patrones.cumple("\\Q(+57)\\E \\d{10}", "+57 3001234567")).isFalse();
    }

    @Test
    @DisplayName("Mil repeticiones anidadas caben; mil y una, no")
    void justoEnElTope() {
        assertThat(Patrones.problema("(a{10}){100}")).isEmpty();
        assertThat(Patrones.problema("(a{7}){143}")).hasValueSatisfying(
                problema -> assertThat(problema).startsWith("El patron repite demasiado"));
    }
}
