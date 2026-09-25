package com.facimus.procesos.common.condiciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * D6: el lenguaje de las condiciones, que se lee al publicar y se evalua al ejecutar. Es una funcion pura, sin
 * Spring ni base de datos detras, asi que se prueba llamandola.
 */
class EvaluadorDeCondicionesTest {

    /** Las variables de un pedido aprobado de 150, que es lo que la mayoria de las pruebas compara. */
    private static final Map<String, Object> PEDIDO = Map.of(
            "payment", Map.of("status", "APPROVED", "amount", 150),
            "order", Map.of("total", 150, "vip", true, "createdAt", "2026-09-24"),
            "caso", Map.of("referencia", "ORD-1"));

    private static boolean evaluar(String condicion, Map<String, Object> variables) {
        return EvaluadorDeCondiciones.compilar(condicion).seCumple(Variables.de(variables));
    }

    @Nested
    @DisplayName("Al publicar: la condicion compila o dice por que no")
    class AlPublicar {

        @ParameterizedTest
        @ValueSource(strings = {
            "payment.status == APPROVED",
            "payment.status == 'APPROVED'",
            "payment.status != \"DECLINED\"",
            "order.total > 100",
            "order.total >= 99.95",
            "order.total < -5",
            "order.createdAt <= '2026-09-24'",
            "order.createdAt > '2026-09-24T10:15:30'",
            "order.paid == true",
            "tarea.revisarPedido.aprobado == false",
            "payment.status == APPROVED and order.total > 100",
            "payment.status == APPROVED or payment.status == PENDING",
            "not payment.failed == true",
            "(payment.status == APPROVED or order.vip == true) and order.total > 100",
            "  payment.status   ==   APPROVED  "
        })
        @DisplayName("Una condicion bien escrita compila")
        void condicionValida_compila(String condicion) {
            assertThat(EvaluadorDeCondiciones.problema(condicion)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "",
            "payment.status",
            "payment.status ==",
            "== APPROVED",
            "payment.status = APPROVED",
            "payment.status ! APPROVED",
            "payment. == APPROVED",
            "payment..status == APPROVED",
            "and == APPROVED",
            "payment.status == 'APPROVED",
            "(payment.status == APPROVED",
            "payment.status == APPROVED order.total > 100",
            "payment.status == APPROVED and",
            "order.total > APPROVED",
            "order.total <= 'pronto'",
            "payment.status == APPROVED; drop table pedidos",
            "borrar(pedidos) == true"
        })
        @DisplayName("Una condicion mal escrita dice por que no compila")
        void condicionInvalida_diceElMotivo(String condicion) {
            Optional<String> problema = EvaluadorDeCondiciones.problema(condicion);

            assertThat(problema).isPresent();
            assertThat(problema.orElseThrow()).endsWith(".");
        }

        @ParameterizedTest
        @ValueSource(strings = {"order.total > APPROVED", "order.total < 'pronto'", "order.total >= true"})
        @DisplayName("Mayor y menor solo comparan numeros o fechas")
        void comparacionDeOrdenEntreTipos_noCompila(String condicion) {
            assertThat(EvaluadorDeCondiciones.problema(condicion).orElseThrow())
                    .contains("solo comparan numeros o fechas");
        }

        @Test
        @DisplayName("Compilar una condicion rota levanta la excepcion, para que nadie la evalue a medias")
        void compilar_condicionRota_levantaLaExcepcion() {
            assertThatThrownBy(() -> EvaluadorDeCondiciones.compilar("payment.status =="))
                    .isInstanceOf(CondicionMalEscrita.class)
                    .hasMessageContaining("Falta con que comparar");
        }
    }

    @Nested
    @DisplayName("Al ejecutar: cada operador compara su tipo")
    class Operadores {

        @ParameterizedTest
        @CsvSource({
            "payment.status == APPROVED, true",
            "payment.status == DECLINED, false",
            "payment.status != DECLINED, true",
            "payment.status != APPROVED, false",
            "order.total > 100, true",
            "order.total > 150, false",
            "order.total >= 150, true",
            "order.total >= 151, false",
            "order.total < 200, true",
            "order.total < 150, false",
            "order.total <= 150, true",
            "order.total <= 149, false",
            "order.vip == true, true",
            "order.vip == false, false",
            "order.vip != false, true"
        })
        @DisplayName("Los seis operadores responden sobre las variables del caso")
        void operadores_comparanElValorDelCaso(String condicion, boolean esperado) {
            assertThat(evaluar(condicion, PEDIDO)).isEqualTo(esperado);
        }

        @Test
        @DisplayName("Una constante sin comillas se compara como el mismo texto entre comillas")
        void constanteSinComillas_valeIgualQueElTexto() {
            assertThat(evaluar("payment.status == APPROVED", PEDIDO)).isTrue();
            assertThat(evaluar("payment.status == 'APPROVED'", PEDIDO)).isTrue();
            assertThat(evaluar("payment.status == \"APPROVED\"", PEDIDO)).isTrue();
        }

        @Test
        @DisplayName("El texto se compara respetando las mayusculas")
        void texto_comparaConMayusculas() {
            assertThat(evaluar("payment.status == approved", PEDIDO)).isFalse();
        }

        @Test
        @DisplayName("Un decimal y un entero con el mismo valor son iguales")
        void numero_comparaPorValorYNoPorFormato() {
            assertThat(evaluar("order.total == 150.00", PEDIDO)).isTrue();
        }

        @ParameterizedTest
        @CsvSource({
            "order.createdAt > '2026-09-23', true",
            "order.createdAt > '2026-09-25', false",
            "order.createdAt <= '2026-09-24', true",
            "order.createdAt < '2026-09-24T10:15:30', true"
        })
        @DisplayName("Dos fechas ISO se comparan por orden, no por texto")
        void fechas_seComparanPorOrden(String condicion, boolean esperado) {
            assertThat(evaluar(condicion, PEDIDO)).isEqualTo(esperado);
        }

        @Test
        @DisplayName("Comparar tipos distintos no es igual, y tampoco pone el caso en orden")
        void tiposDistintos_niIgualesNiOrdenados() {
            Map<String, Object> variables = Map.of("order", Map.of("total", "150"));

            assertThat(evaluar("order.total == 150", variables)).isFalse();
            assertThat(evaluar("order.total != 150", variables)).isTrue();
            assertThat(evaluar("order.total > 100", variables)).isFalse();
            assertThat(evaluar("order.total < 100", variables)).isFalse();
        }
    }

    @Nested
    @DisplayName("Al ejecutar: and, or, not y los parentesis")
    class Combinaciones {

        @ParameterizedTest
        @CsvSource({
            "payment.status == APPROVED and order.total > 100, true",
            "payment.status == APPROVED and order.total > 200, false",
            "payment.status == DECLINED and order.total > 100, false",
            "payment.status == DECLINED or order.total > 100, true",
            "payment.status == DECLINED or order.total > 200, false",
            "not payment.status == DECLINED, true",
            "not payment.status == APPROVED, false",
            "not (payment.status == DECLINED or order.vip == false), true"
        })
        @DisplayName("Las combinaciones responden lo que dicen")
        void combinaciones_responden(String condicion, boolean esperado) {
            assertThat(evaluar(condicion, PEDIDO)).isEqualTo(esperado);
        }

        @Test
        @DisplayName("and ata mas fuerte que or: la de la derecha decide sola cuando es verdadera")
        void precedencia_andAntesQueOr() {
            // false and false or true: con la precedencia correcta es verdadera; agrupando al reves, falsa.
            assertThat(evaluar("payment.status == DECLINED and order.total > 200 or order.vip == true", PEDIDO))
                    .isTrue();
        }

        @Test
        @DisplayName("and ata mas fuerte que or: la de la izquierda no arrastra a la de la derecha")
        void precedencia_orNoArrastraAlAnd() {
            // true or true and false: con la precedencia correcta es verdadera; agrupando al reves, falsa.
            assertThat(evaluar("order.vip == true or payment.status == APPROVED and order.total > 200", PEDIDO))
                    .isTrue();
        }

        @Test
        @DisplayName("Los parentesis cambian lo que agrupa cada operador")
        void parentesis_cambianElAgrupamiento() {
            String conParentesis = "(order.vip == true or payment.status == APPROVED) and order.total > 200";

            assertThat(evaluar(conParentesis, PEDIDO)).isFalse();
        }

        @Test
        @DisplayName("not se aplica al factor que sigue, no a toda la expresion")
        void not_seAplicaAlFactorQueSigue() {
            // not A and B: si not tomara toda la expresion, el resultado seria el contrario.
            assertThat(evaluar("not payment.status == DECLINED and order.total > 100", PEDIDO)).isTrue();
        }
    }

    @Nested
    @DisplayName("Al ejecutar: variables que no estan")
    class VariablesAusentes {

        @Test
        @DisplayName("Una variable que el caso no tiene hace falsa la comparacion, la escriba como la escriba")
        void variableAusente_haceFalsaLaComparacion() {
            assertThat(evaluar("payment.status == APPROVED", Map.of())).isFalse();
            assertThat(evaluar("payment.status != APPROVED", Map.of())).isFalse();
            assertThat(evaluar("payment.status > 10", Map.of())).isFalse();
        }

        @Test
        @DisplayName("Una ruta que atraviesa algo que no es un mapa no existe")
        void rutaQueNoBaja_noExiste() {
            assertThat(evaluar("payment.status.detalle == X", PEDIDO)).isFalse();
        }

        @Test
        @DisplayName("Cada comparacion sin variable deja su aviso, tambien las del lado que no decide")
        void variableAusente_dejaAvisoDeCadaLado() {
            List<String> ausentes = new ArrayList<>();
            Variables variables = new Variables() {

                @Override
                public Optional<Object> valor(String ruta) {
                    return Optional.empty();
                }

                @Override
                public void anotarAusente(String ruta) {
                    ausentes.add(ruta);
                }
            };

            boolean resultado = EvaluadorDeCondiciones.compilar("payment.status == APPROVED and order.total > 100")
                    .seCumple(variables);

            assertThat(resultado).isFalse();
            assertThat(ausentes).containsExactly("payment.status", "order.total");
        }

        @Test
        @DisplayName("Una variable con valor nulo cuenta como ausente")
        void valorNulo_cuentaComoAusente() {
            Map<String, Object> pago = new HashMap<>();
            pago.put("status", null);

            assertThat(evaluar("payment.status == APPROVED", Map.of("payment", pago))).isFalse();
        }
    }

    @Nested
    @DisplayName("Como se lee de vuelta")
    class Texto {

        @ParameterizedTest
        @ValueSource(strings = {
            "payment.status == APPROVED",
            "order.total >= 99.95",
            "payment.status == APPROVED and order.total > 100",
            "payment.status == DECLINED or order.vip == true",
            "not payment.failed == true",
            "(payment.status == APPROVED or order.vip == true) and order.total > 100"
        })
        @DisplayName("La condicion compilada se vuelve a escribir igual, para la bitacora del caso")
        void texto_devuelveLaCondicionComoSeEscribio(String condicion) {
            assertThat(EvaluadorDeCondiciones.compilar(condicion).texto()).isEqualTo(condicion);
        }

        @Test
        @DisplayName("Un texto entre comillas dobles se relee con comillas simples, que es la misma condicion")
        void texto_normalizaLasComillas() {
            assertThat(EvaluadorDeCondiciones.compilar("payment.status == \"APPROVED\"").texto())
                    .isEqualTo("payment.status == 'APPROVED'");
        }
    }
}
