package com.facimus.procesos.common.formularios;

import static com.facimus.procesos.common.formularios.CampoArmado.campo;
import static com.facimus.procesos.common.formularios.CampoArmado.formulario;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.facimus.procesos.common.condiciones.Variables;
import com.facimus.procesos.common.formularios.RespuestasDelFormulario.Referencias;
import com.facimus.procesos.common.formularios.RespuestasDelFormulario.Resultado;

/** PR 69: lo que se contesta en un formulario, revisado campo por campo, tipo por tipo y en sus bordes. */
class RespuestasDelFormularioTest {

    /** Las personas 5 y 6 son de la tienda; el documento 40, del proceso. */
    private record Conocidas(Set<Long> personas, Set<Long> archivos) implements Referencias {

        @Override
        public boolean esPersona(long id) {
            return personas.contains(id);
        }

        @Override
        public boolean esArchivo(long id) {
            return archivos.contains(id);
        }
    }

    private static final Referencias TIENDA = new Conocidas(Set.of(5L, 6L), Set.of(40L));
    private static final String OBLIGATORIO = "Es obligatorio.";

    private static Resultado validar(Formulario formulario, Map<String, Object> respuestas) {
        return validar(formulario, respuestas, Map.of());
    }

    private static Resultado validar(Formulario formulario, Map<String, Object> respuestas,
            Map<String, Object> delCaso) {
        return RespuestasDelFormulario.validar(formulario, respuestas, Variables.de(delCaso), TIENDA);
    }

    /** El valor que queda de una sola respuesta a un solo campo; falla la prueba si no se acepta. */
    private static Object valorDe(CampoArmado campo, Object respuesta) {
        Formulario formulario = formulario(campo);
        Map<String, Object> respuestas = new HashMap<>();
        respuestas.put(formulario.campos().getFirst().nombre(), respuesta);
        Resultado resultado = validar(formulario, respuestas);
        assertThat(resultado.errores()).isEmpty();
        return resultado.valores().get(formulario.campos().getFirst().nombre());
    }

    /** El error de una sola respuesta a un solo campo, o nulo si se acepta. */
    private static String errorDe(CampoArmado campo, Object respuesta) {
        Formulario formulario = formulario(campo);
        Map<String, Object> respuestas = new HashMap<>();
        respuestas.put(formulario.campos().getFirst().nombre(), respuesta);
        Resultado resultado = validar(formulario, respuestas);
        if (resultado.valido()) {
            return null;
        }
        assertThat(resultado.valores()).isEmpty();
        return resultado.errores().get(formulario.campos().getFirst().nombre());
    }

    @Nested
    @DisplayName("Lo que se contesta y lo que no")
    class Generales {

        @Test
        @DisplayName("Una respuesta a algo que el formulario no pregunta es un error de esa clave")
        void claveDesconocida() {
            Resultado resultado = validar(formulario(campo("monto", TipoDeCampo.NUMERO)),
                    Map.of("monto", 3, "hackeo", "x"));

            assertThat(resultado.valido()).isFalse();
            assertThat(resultado.errores()).containsExactly(Map.entry("hackeo", "No es un campo de este formulario."));
            assertThat(resultado.valores()).isEmpty();
        }

        @Test
        @DisplayName("Una seccion no lleva valor")
        void aUnaSeccion() {
            assertThat(validar(formulario(campo("datos", TipoDeCampo.SECCION)), Map.of("datos", "x")).errores())
                    .containsExactly(Map.entry("datos", "Una seccion no lleva valor."));
        }

        @Test
        @DisplayName("Sin respuestas, ningun campo obligatorio: se acepta y no guarda nada")
        void sinRespuestas() {
            Resultado resultado = validar(formulario(campo("monto", TipoDeCampo.NUMERO)), null);

            assertThat(resultado.valido()).isTrue();
            assertThat(resultado.valores()).isEmpty();
        }

        @Test
        @DisplayName("Un campo obligatorio sin respuesta, o con una en blanco, es obligatorio")
        void obligatorio() {
            Formulario formulario = formulario(campo("nombre", TipoDeCampo.TEXTO).requerido(),
                    campo("monto", TipoDeCampo.NUMERO).requerido());

            assertThat(validar(formulario, Map.of("nombre", "   ")).errores())
                    .containsExactly(Map.entry("nombre", OBLIGATORIO), Map.entry("monto", OBLIGATORIO));
        }

        @Test
        @DisplayName("Cada campo que falla dice lo suyo, y entonces no sale ningun valor")
        void variosErrores() {
            Formulario formulario = formulario(campo("a", TipoDeCampo.NUMERO), campo("b", TipoDeCampo.SI_NO),
                    campo("c", TipoDeCampo.TEXTO));

            Resultado resultado = validar(formulario, Map.of("a", "x", "b", 1, "c", "bien"));

            assertThat(resultado.errores()).containsOnlyKeys("a", "b");
            assertThat(resultado.valores()).isEmpty();
        }

        @Test
        @DisplayName("Lo aceptado sale con el nombre de cada campo, en el orden del formulario")
        void valoresEnOrden() {
            Formulario formulario = formulario(campo("a", TipoDeCampo.NUMERO), campo("b", TipoDeCampo.TEXTO),
                    campo("c", TipoDeCampo.SI_NO));

            assertThat(validar(formulario, Map.of("c", true, "a", 1, "b", "x")).valores())
                    .containsExactly(Map.entry("a", 1L), Map.entry("b", "x"), Map.entry("c", true));
        }
    }

    @Nested
    @DisplayName("Textos")
    class Textos {

        @Test
        @DisplayName("Un texto se guarda como llega; lo que no es texto, no")
        void tipo() {
            assertThat(valorDe(campo("a", TipoDeCampo.TEXTO), " Hola ")).isEqualTo(" Hola ");
            assertThat(errorDe(campo("a", TipoDeCampo.TEXTO), 12)).isEqualTo("Debe ser un texto.");
            assertThat(errorDe(campo("a", TipoDeCampo.TEXTO_LARGO), List.of("x"))).isEqualTo("Debe ser un texto.");
        }

        @Test
        @DisplayName("En blanco no es una respuesta")
        void enBlanco() {
            assertThat(valorDe(campo("a", TipoDeCampo.TEXTO), " \t ")).isNull();
            assertThat(valorDe(campo("a", TipoDeCampo.TEXTO), "")).isNull();
        }

        @Test
        @DisplayName("Un texto es una linea; uno largo admite varias")
        void lineas() {
            assertThat(errorDe(campo("a", TipoDeCampo.TEXTO), "uno\ndos"))
                    .isEqualTo("Es una sola linea: para varias, el campo seria de texto largo.");
            assertThat(errorDe(campo("a", TipoDeCampo.TEXTO), "uno\rdos")).isNotNull();
            assertThat(valorDe(campo("a", TipoDeCampo.TEXTO_LARGO), "uno\ndos")).isEqualTo("uno\ndos");
        }

        @Test
        @DisplayName("Sin largo maximo, un texto admite 1000 caracteres y uno largo 10000")
        void topesDelTipo() {
            assertThat(valorDe(campo("a", TipoDeCampo.TEXTO), "x".repeat(1_000))).isNotNull();
            assertThat(errorDe(campo("a", TipoDeCampo.TEXTO), "x".repeat(1_001)))
                    .isEqualTo("Admite hasta 1000 caracteres.");
            assertThat(valorDe(campo("a", TipoDeCampo.TEXTO_LARGO), "x".repeat(10_000))).isNotNull();
            assertThat(errorDe(campo("a", TipoDeCampo.TEXTO_LARGO), "x".repeat(10_001))).isNotNull();
        }

        @Test
        @DisplayName("Largo minimo y maximo, incluidos, y contados en caracteres, no en unidades de UTF-16")
        void largos() {
            CampoArmado entreTresYCinco = campo("a", TipoDeCampo.TEXTO).largo(3, 5);
            assertThat(errorDe(entreTresYCinco, "ab")).isEqualTo("Necesita al menos 3 caracteres.");
            assertThat(errorDe(entreTresYCinco, "abc")).isNull();
            assertThat(errorDe(entreTresYCinco, "abcde")).isNull();
            assertThat(errorDe(entreTresYCinco, "abcdef")).isEqualTo("Admite hasta 5 caracteres.");
            assertThat(errorDe(entreTresYCinco, "😀".repeat(5))).isNull();
            assertThat(errorDe(entreTresYCinco, "😀".repeat(2))).isNotNull();
        }

        @Test
        @DisplayName("El patron se cumple con el texto entero")
        void patron() {
            CampoArmado codigo = campo("a", TipoDeCampo.TEXTO).patron("[A-Z]{3}-\\d{4}");
            assertThat(errorDe(codigo, "ABC-1234")).isNull();
            assertThat(errorDe(codigo, "ABC-1234 ")).isEqualTo("No tiene la forma que se pide.");
            assertThat(errorDe(codigo, "abc-1234")).isNotNull();
        }

        @Test
        @DisplayName("Un patron catastrofico en otro motor responde enseguida sobre el texto mas largo")
        void patronCatastrofico() {
            CampoArmado campo = campo("a", TipoDeCampo.TEXTO_LARGO).patron("(a+)+$");
            long antes = System.nanoTime();

            assertThat(errorDe(campo, "a".repeat(9_999) + "!")).isEqualTo("No tiene la forma que se pide.");
            assertThat(System.nanoTime() - antes).isLessThan(2_000_000_000L);
        }
    }

    @Nested
    @DisplayName("Numeros y monedas")
    class Numeros {

        @Test
        @DisplayName("Un entero sale como entero; uno con decimales, con sus decimales y sin ceros de mas")
        void tipo() {
            assertThat(valorDe(campo("a", TipoDeCampo.NUMERO), 3)).isEqualTo(3L);
            assertThat(valorDe(campo("a", TipoDeCampo.NUMERO), 100)).isEqualTo(100L);
            assertThat(valorDe(campo("a", TipoDeCampo.NUMERO), 1.0)).isEqualTo(1L);
            assertThat(valorDe(campo("a", TipoDeCampo.NUMERO), 2.50)).isEqualTo(new BigDecimal("2.5"));
            assertThat(valorDe(campo("a", TipoDeCampo.NUMERO), -0.125)).isEqualTo(new BigDecimal("-0.125"));
            assertThat(valorDe(campo("a", TipoDeCampo.NUMERO), new BigDecimal("0.000"))).isEqualTo(0L);
        }

        @Test
        @DisplayName("Un texto que parece un numero no es un numero, ni NaN, ni infinito")
        void noNumeros() {
            assertThat(errorDe(campo("a", TipoDeCampo.NUMERO), "3")).isEqualTo("Debe ser un numero.");
            assertThat(errorDe(campo("a", TipoDeCampo.NUMERO), true)).isEqualTo("Debe ser un numero.");
            assertThat(errorDe(campo("a", TipoDeCampo.NUMERO), Double.NaN)).isEqualTo("Debe ser un numero.");
            assertThat(errorDe(campo("a", TipoDeCampo.NUMERO), Double.POSITIVE_INFINITY))
                    .isEqualTo("Debe ser un numero.");
        }

        @Test
        @DisplayName("Hasta quince cifras enteras y seis decimales")
        void cifras() {
            assertThat(valorDe(campo("a", TipoDeCampo.NUMERO), 999_999_999_999_999L)).isEqualTo(999_999_999_999_999L);
            assertThat(errorDe(campo("a", TipoDeCampo.NUMERO), 1_000_000_000_000_000L))
                    .isEqualTo("Admite hasta 15 cifras enteras.");
            assertThat(errorDe(campo("a", TipoDeCampo.NUMERO), new BigInteger("9".repeat(40)))).isNotNull();
            assertThat(errorDe(campo("a", TipoDeCampo.NUMERO), new BigDecimal("1E+400"))).isNotNull();
            assertThat(valorDe(campo("a", TipoDeCampo.NUMERO), 0.123456)).isEqualTo(new BigDecimal("0.123456"));
            assertThat(errorDe(campo("a", TipoDeCampo.NUMERO), 0.1234567)).isEqualTo("Admite hasta 6 decimales.");
        }

        @Test
        @DisplayName("Minimo y maximo incluidos")
        void rango() {
            CampoArmado deUnoACien = campo("a", TipoDeCampo.NUMERO).minimo("1").maximo("100");
            assertThat(errorDe(deUnoACien, 0.999)).isEqualTo("Debe ser al menos 1.");
            assertThat(errorDe(deUnoACien, 1)).isNull();
            assertThat(errorDe(deUnoACien, 100)).isNull();
            assertThat(errorDe(deUnoACien, 100.001)).isEqualTo("Debe ser como mucho 100.");
            assertThat(errorDe(campo("a", TipoDeCampo.NUMERO).minimo("-2.5"), -2.6))
                    .isEqualTo("Debe ser al menos -2.5.");
            assertThat(errorDe(campo("a", TipoDeCampo.NUMERO).maximo("1E+3"), 1001))
                    .isEqualTo("Debe ser como mucho 1000.");
        }

        @Test
        @DisplayName("Una moneda lleva los decimales de su codigo: dos en el peso, ninguno en el yen")
        void decimalesDeLaMoneda() {
            assertThat(valorDe(campo("a", TipoDeCampo.MONEDA).moneda("COP"), 1500.25))
                    .isEqualTo(new BigDecimal("1500.25"));
            assertThat(errorDe(campo("a", TipoDeCampo.MONEDA).moneda("COP"), 1500.255))
                    .isEqualTo("Admite hasta 2 decimales.");
            assertThat(valorDe(campo("a", TipoDeCampo.MONEDA).moneda("JPY"), 1500)).isEqualTo(1500L);
            assertThat(errorDe(campo("a", TipoDeCampo.MONEDA).moneda("JPY"), 1500.5)).isEqualTo("No admite decimales.");
            assertThat(errorDe(campo("a", TipoDeCampo.MONEDA).moneda("BHD"), 1.125)).isNull();
            assertThat(errorDe(campo("a", TipoDeCampo.MONEDA), 9.99)).isNull();
            assertThat(errorDe(campo("a", TipoDeCampo.MONEDA), 9.999)).isEqualTo("Admite hasta 2 decimales.");
            assertThat(errorDe(campo("a", TipoDeCampo.MONEDA).moneda("ZZZ"), 9.999)).isNotNull();
        }
    }

    @Nested
    @DisplayName("Fechas")
    class Fechas {

        @Test
        @DisplayName("Un dia en ISO 8601 que exista")
        void dia() {
            assertThat(valorDe(campo("a", TipoDeCampo.FECHA), "2026-11-12")).isEqualTo("2026-11-12");
            assertThat(errorDe(campo("a", TipoDeCampo.FECHA), "2026-02-30"))
                    .isEqualTo("Es un dia en ISO 8601, como 2026-11-12.");
            assertThat(errorDe(campo("a", TipoDeCampo.FECHA), "12/11/2026")).isNotNull();
            assertThat(errorDe(campo("a", TipoDeCampo.FECHA), "2026-11-12T10:00:00")).isNotNull();
            assertThat(errorDe(campo("a", TipoDeCampo.FECHA), 20261112)).isEqualTo("Debe ser un texto con la fecha.");
        }

        @Test
        @DisplayName("Desde y hasta, incluidos")
        void diasEntre() {
            CampoArmado delAnio = campo("a", TipoDeCampo.FECHA).entre("2026-01-01", "2026-12-31");
            assertThat(errorDe(delAnio, "2025-12-31")).isEqualTo("No puede ser antes de 2026-01-01.");
            assertThat(errorDe(delAnio, "2026-01-01")).isNull();
            assertThat(errorDe(delAnio, "2026-12-31")).isNull();
            assertThat(errorDe(delAnio, "2027-01-01")).isEqualTo("No puede ser despues de 2026-12-31.");
        }

        @Test
        @DisplayName("Un dia y una hora local, sin fracciones de segundo, con los segundos escritos")
        void momento() {
            assertThat(valorDe(campo("a", TipoDeCampo.FECHA_HORA), "2026-11-12T09:30"))
                    .isEqualTo("2026-11-12T09:30:00");
            assertThat(valorDe(campo("a", TipoDeCampo.FECHA_HORA), "2026-11-12T09:30:15.987"))
                    .isEqualTo("2026-11-12T09:30:15");
            assertThat(errorDe(campo("a", TipoDeCampo.FECHA_HORA), "2026-11-12"))
                    .isEqualTo("Es un dia y una hora en ISO 8601, como 2026-11-12T09:30:00.");
            assertThat(errorDe(campo("a", TipoDeCampo.FECHA_HORA), "2026-11-12T09:30:00-05:00")).isNotNull();
        }

        @Test
        @DisplayName("Desde y hasta de una fecha y hora, incluidos, al segundo")
        void momentosEntre() {
            CampoArmado jornada = campo("a", TipoDeCampo.FECHA_HORA).entre("2026-01-05T08:00:00",
                    "2026-01-05T17:00:00");
            assertThat(errorDe(jornada, "2026-01-05T07:59:59")).isNotNull();
            assertThat(errorDe(jornada, "2026-01-05T08:00:00")).isNull();
            assertThat(errorDe(jornada, "2026-01-05T17:00:00.999")).isNull();
            assertThat(errorDe(jornada, "2026-01-05T17:00:01")).isNotNull();
        }

        @Test
        @DisplayName("Un limite mal escrito no limita: el esquema ya lo habria rechazado")
        void limiteIlegible() {
            assertThat(errorDe(campo("a", TipoDeCampo.FECHA).entre("ayer", "manana"), "2026-11-12")).isNull();
        }
    }

    @Nested
    @DisplayName("Si o no, listas")
    class Elecciones {

        @Test
        @DisplayName("Si o no es un booleano: false tambien es una respuesta, aun en un campo obligatorio")
        void siNo() {
            assertThat(valorDe(campo("a", TipoDeCampo.SI_NO).requerido(), false)).isEqualTo(false);
            assertThat(valorDe(campo("a", TipoDeCampo.SI_NO), true)).isEqualTo(true);
            assertThat(errorDe(campo("a", TipoDeCampo.SI_NO), "true")).isEqualTo("Es true o false.");
        }

        @Test
        @DisplayName("Una lista es el valor de una de sus opciones")
        void lista() {
            CampoArmado metodo = campo("a", TipoDeCampo.LISTA).opciones("TARJETA", "EFECTIVO");
            assertThat(valorDe(metodo, "EFECTIVO")).isEqualTo("EFECTIVO");
            assertThat(errorDe(metodo, "efectivo")).isEqualTo("\"efectivo\" no es una de las opciones.");
            assertThat(errorDe(metodo, 1)).isEqualTo("Es el valor de una de las opciones.");
            assertThat(valorDe(metodo, "")).isNull();
        }

        @Test
        @DisplayName("Una lista multiple es una lista de opciones distintas")
        void multiple() {
            CampoArmado extras = campo("a", TipoDeCampo.LISTA_MULTIPLE).opciones("A", "B", "C");
            assertThat(valorDe(extras, List.of("C", "A"))).isEqualTo(List.of("C", "A"));
            assertThat(valorDe(extras, List.of())).isNull();
            assertThat(errorDe(extras, List.of("A", "A"))).isEqualTo("La opcion \"A\" esta elegida dos veces.");
            assertThat(errorDe(extras, List.of("A", "D"))).isEqualTo("\"D\" no es una de las opciones.");
            assertThat(errorDe(extras, "A")).isEqualTo("Es una lista con los valores de las opciones elegidas.");
            assertThat(errorDe(extras, Arrays.asList("A", 2))).isNotNull();
        }

        @Test
        @DisplayName("En una lista multiple, minimo y maximo cuentan lo elegido, incluidos")
        void cuantas() {
            CampoArmado deDosATres = campo("a", TipoDeCampo.LISTA_MULTIPLE).opciones("A", "B", "C", "D")
                    .minimo("2").maximo("3");
            assertThat(errorDe(deDosATres, List.of("A"))).isEqualTo("Elige al menos 2.");
            assertThat(errorDe(deDosATres, List.of("A", "B"))).isNull();
            assertThat(errorDe(deDosATres, List.of("A", "B", "C"))).isNull();
            assertThat(errorDe(deDosATres, List.of("A", "B", "C", "D"))).isEqualTo("Elige como mucho 3.");
        }

        @Test
        @DisplayName("Una lista multiple obligatoria vacia es obligatoria")
        void multipleObligatoria() {
            assertThat(errorDe(campo("a", TipoDeCampo.LISTA_MULTIPLE).opciones("A").requerido(), List.of()))
                    .isEqualTo(OBLIGATORIO);
        }
    }

    @Nested
    @DisplayName("Personas y archivos")
    class Referenciados {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = TipoDeCampo.class, names = {"PERSONA", "ARCHIVO"})
        @DisplayName("Son ids: enteros positivos")
        void ids(TipoDeCampo tipo) {
            assertThat(errorDe(campo("a", tipo), 0)).isEqualTo("Es un id: un entero positivo.");
            assertThat(errorDe(campo("a", tipo), -5)).isEqualTo("Es un id: un entero positivo.");
            assertThat(errorDe(campo("a", tipo), 5.5)).isEqualTo("Es un id: un entero positivo.");
            assertThat(errorDe(campo("a", tipo), "5")).isEqualTo("Debe ser un numero.");
        }

        @Test
        @DisplayName("Una persona es de la organizacion y esta activa")
        void persona() {
            assertThat(valorDe(campo("a", TipoDeCampo.PERSONA), 5)).isEqualTo(5L);
            assertThat(valorDe(campo("a", TipoDeCampo.PERSONA), 6.0)).isEqualTo(6L);
            assertThat(errorDe(campo("a", TipoDeCampo.PERSONA), 7))
                    .isEqualTo("No es una persona activa de la organizacion.");
            assertThat(errorDe(campo("a", TipoDeCampo.PERSONA), 40)).isNotNull();
        }

        @Test
        @DisplayName("Un archivo es un documento del proceso listo para usar")
        void archivo() {
            assertThat(valorDe(campo("a", TipoDeCampo.ARCHIVO), 40)).isEqualTo(40L);
            assertThat(errorDe(campo("a", TipoDeCampo.ARCHIVO), 5))
                    .isEqualTo("No es un documento del proceso listo para usar.");
        }
    }

    @Nested
    @DisplayName("Visibilidad y obligatoriedad")
    class Condiciones {

        private final Formulario pago = formulario(
                campo("metodo", TipoDeCampo.LISTA).opciones("TARJETA", "EFECTIVO").requerido(),
                campo("tarjeta", TipoDeCampo.TEXTO).visibleSi("metodo == 'TARJETA'").requerido(),
                campo("cuotas", TipoDeCampo.NUMERO).requeridoSi("metodo == 'TARJETA' and order.total > 100"));

        @Test
        @DisplayName("Un campo escondido no es obligatorio, y lo que se mande en el se descarta")
        void escondido() {
            Resultado resultado = validar(pago, Map.of("metodo", "EFECTIVO", "tarjeta", "4111"));

            assertThat(resultado.errores()).isEmpty();
            assertThat(resultado.valores()).containsExactly(Map.entry("metodo", "EFECTIVO"));
        }

        @Test
        @DisplayName("Un campo visible y obligatorio sin respuesta es obligatorio")
        void visible() {
            assertThat(validar(pago, Map.of("metodo", "TARJETA"), Map.of("order", Map.of("total", 50))).errores())
                    .containsExactly(Map.entry("tarjeta", OBLIGATORIO));
        }

        @Test
        @DisplayName("requeridoSi lee los campos de arriba y las variables del caso")
        void requeridoSi() {
            Map<String, Object> respuestas = Map.of("metodo", "TARJETA", "tarjeta", "4111");

            assertThat(validar(pago, respuestas, Map.of("order", Map.of("total", 150))).errores())
                    .containsExactly(Map.entry("cuotas", OBLIGATORIO));
            assertThat(validar(pago, respuestas, Map.of("order", Map.of("total", 100))).valido()).isTrue();
            assertThat(validar(pago, respuestas, Map.of()).valido()).isTrue();
        }

        @Test
        @DisplayName("Un campo de arriba que fallo no cuenta para las condiciones de abajo")
        void arribaInvalido() {
            Formulario formulario = formulario(campo("monto", TipoDeCampo.NUMERO),
                    campo("motivo", TipoDeCampo.TEXTO).requeridoSi("monto > 100"));

            assertThat(validar(formulario, Map.of("monto", "mucho")).errores()).containsOnlyKeys("monto");
        }

        @Test
        @DisplayName("Un campo escondido tampoco cuenta para las condiciones de abajo")
        void arribaEscondido() {
            Formulario formulario = formulario(campo("pais", TipoDeCampo.TEXTO),
                    campo("nit", TipoDeCampo.TEXTO).visibleSi("pais == 'CO'"),
                    campo("dv", TipoDeCampo.NUMERO).visibleSi("nit != 'x'").requerido());

            assertThat(validar(formulario, Map.of("pais", "FR", "nit", "900")).valores())
                    .containsExactly(Map.entry("pais", "FR"));
            assertThat(validar(formulario, Map.of("pais", "CO", "nit", "900")).errores())
                    .containsExactly(Map.entry("dv", OBLIGATORIO));
        }

        @Test
        @DisplayName("Un nombre del formulario tapa a la variable del caso que se llama igual, aunque este vacio")
        void tapaAlCaso() {
            Formulario formulario = formulario(campo("order", TipoDeCampo.TEXTO),
                    campo("detalle", TipoDeCampo.TEXTO).visibleSi("order == 'X'").requerido());

            assertThat(validar(formulario, Map.of(), Map.of("order", "X")).valido()).isTrue();
            assertThat(validar(formulario, Map.of("order", "X"), Map.of("order", "Y")).errores())
                    .containsExactly(Map.entry("detalle", OBLIGATORIO));
        }

        @Test
        @DisplayName("Una seccion escondida esconde sus campos, hasta la siguiente seccion")
        void seccion() {
            Formulario formulario = formulario(campo("envio", TipoDeCampo.SI_NO),
                    campo("direccion", TipoDeCampo.SECCION).visibleSi("envio == true"),
                    campo("calle", TipoDeCampo.TEXTO).requerido(),
                    campo("ciudad", TipoDeCampo.TEXTO).requerido(),
                    campo("cierre", TipoDeCampo.SECCION),
                    campo("firma", TipoDeCampo.TEXTO).requerido());

            assertThat(validar(formulario, Map.of("envio", false, "calle", "x")).errores())
                    .containsExactly(Map.entry("firma", OBLIGATORIO));
            assertThat(validar(formulario, Map.of("envio", true, "firma", "Ana")).errores())
                    .containsExactly(Map.entry("calle", OBLIGATORIO), Map.entry("ciudad", OBLIGATORIO));
        }

        @Test
        @DisplayName("Dentro de una seccion visible, cada campo sigue con su propia condicion")
        void dentroDeLaSeccion() {
            Formulario formulario = formulario(campo("s", TipoDeCampo.SECCION),
                    campo("a", TipoDeCampo.SI_NO),
                    campo("b", TipoDeCampo.TEXTO).visibleSi("a == true").requerido());

            assertThat(validar(formulario, Map.of("a", false)).valido()).isTrue();
            assertThat(validar(formulario, Map.of("a", true)).errores()).containsOnlyKeys("b");
        }

        @Test
        @DisplayName("Una condicion que no compila vale lo que valdria sin ella")
        void noCompila() {
            Formulario formulario = formulario(campo("a", TipoDeCampo.TEXTO).visibleSi("((").requerido(),
                    campo("b", TipoDeCampo.TEXTO).requeridoSi("(("));

            assertThat(validar(formulario, Map.of()).errores()).containsExactly(Map.entry("a", OBLIGATORIO));
        }
    }

    @Nested
    @DisplayName("Lo que se propone al abrir el formulario")
    class Iniciales {

        @Test
        @DisplayName("El valor de la variable del caso que nombra porDefecto, si cabe en el campo")
        void deLasVariables() {
            Formulario formulario = formulario(
                    campo("monto", TipoDeCampo.MONEDA).porDefecto("order.total"),
                    campo("cliente", TipoDeCampo.TEXTO).porDefecto("order.customer"),
                    campo("cita", TipoDeCampo.FECHA_HORA).porDefecto("tarea.agendar.cuando"),
                    campo("metodo", TipoDeCampo.LISTA).opciones("A").porDefecto("order.metodo"),
                    campo("cantidad", TipoDeCampo.NUMERO).porDefecto("order.customer"),
                    campo("nota", TipoDeCampo.TEXTO).porDefecto("no.existe"),
                    campo("sinDefecto", TipoDeCampo.TEXTO),
                    campo("seccion", TipoDeCampo.SECCION));
            Map<String, Object> delCaso = Map.of(
                    "order", Map.of("total", 150.5, "customer", "Ana", "metodo", "Z"),
                    "tarea", Map.of("agendar", Map.of("cuando", "2026-11-12T09:30")));

            assertThat(RespuestasDelFormulario.iniciales(formulario, Variables.de(delCaso))).containsExactly(
                    Map.entry("monto", new BigDecimal("150.5")),
                    Map.entry("cliente", "Ana"),
                    Map.entry("cita", "2026-11-12T09:30:00"));
        }

        @Test
        @DisplayName("Una variable en blanco no se propone")
        void enBlanco() {
            Formulario formulario = formulario(campo("cliente", TipoDeCampo.TEXTO).porDefecto("cliente"));

            assertThat(RespuestasDelFormulario.iniciales(formulario, Variables.de(Map.of("cliente", " ")))).isEmpty();
        }
    }
}
