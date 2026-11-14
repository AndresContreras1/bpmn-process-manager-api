package com.facimus.procesos.common.condiciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/** D36: el arbol que la web recorre es exactamente lo que compilo la gramatica de D6, ni mas ni menos. */
class ArbolDeCondicionTest {

    private static ArbolDeCondicion arbol(String condicion) {
        return ArbolDeCondicion.de(EvaluadorDeCondiciones.compilar(condicion));
    }

    private static ArbolDeCondicion comparacion(String ruta, String operador, Object valor) {
        return new ArbolDeCondicion(ArbolDeCondicion.COMPARACION, List.of(), ruta, operador, valor);
    }

    @Test
    @DisplayName("Una comparacion es la hoja, con el valor ya con su tipo")
    void hojas() {
        assertThat(arbol("order.total >= 100")).isEqualTo(comparacion("order.total", ">=", new BigDecimal("100")));
        assertThat(arbol("pago.aprobado == true")).isEqualTo(comparacion("pago.aprobado", "==", Boolean.TRUE));
        assertThat(arbol("metodo != 'TARJETA'")).isEqualTo(comparacion("metodo", "!=", "TARJETA"));
        assertThat(arbol("estado == APPROVED")).isEqualTo(comparacion("estado", "==", "APPROVED"));
        assertThat(arbol("dia < '2026-12-24'")).isEqualTo(comparacion("dia", "<", "2026-12-24"));
    }

    @Test
    @DisplayName("and, or y not, con la precedencia de la gramatica: and ata mas que or")
    void combinaciones() {
        ArbolDeCondicion a = comparacion("a", "==", new BigDecimal("1"));
        ArbolDeCondicion b = comparacion("b", ">", new BigDecimal("2"));
        ArbolDeCondicion c = comparacion("c", "<=", new BigDecimal("3"));

        assertThat(arbol("a == 1 or b > 2 and c <= 3")).isEqualTo(new ArbolDeCondicion(ArbolDeCondicion.O,
                List.of(a, new ArbolDeCondicion(ArbolDeCondicion.Y, List.of(b, c), null, null, null)), null, null,
                null));
        assertThat(arbol("not a == 1")).isEqualTo(new ArbolDeCondicion(ArbolDeCondicion.NO, List.of(a), null, null,
                null));
    }

    @Test
    @DisplayName("Los parentesis agrupan pero no dejan nodo")
    void parentesis() {
        ArbolDeCondicion a = comparacion("a", "==", new BigDecimal("1"));
        ArbolDeCondicion b = comparacion("b", "==", new BigDecimal("2"));
        ArbolDeCondicion c = comparacion("c", "==", new BigDecimal("3"));

        assertThat(arbol("((a == 1))")).isEqualTo(a);
        assertThat(arbol("(a == 1 or b == 2) and c == 3")).isEqualTo(new ArbolDeCondicion(ArbolDeCondicion.Y,
                List.of(new ArbolDeCondicion(ArbolDeCondicion.O, List.of(a, b), null, null, null), c), null, null,
                null));
    }

    @Test
    @DisplayName("Las rutas que lee, en orden y sin repetir")
    void rutas() {
        assertThat(ArbolDeCondicion.rutas(EvaluadorDeCondiciones.compilar(
                "not (b == 1 or a.x == 2) and b != 3 and (c == 4)"))).containsExactly("b", "a.x", "c");
    }

    @Test
    @DisplayName("Viaja como JSON y vuelve igual: es lo que se congela en la version")
    void comoJson() {
        JsonMapper json = JsonMapper.builder().build();
        ArbolDeCondicion original = arbol("metodo == 'TARJETA' and not monto > 100.5");

        String texto = json.writeValueAsString(original);

        assertThat(texto).contains("\"tipo\":\"Y\"", "\"ruta\":\"metodo\"", "\"valor\":\"TARJETA\"",
                "\"operador\":\">\"", "\"valor\":100.5");
        assertThat(json.readValue(texto, ArbolDeCondicion.class).partes().get(1).tipo())
                .isEqualTo(ArbolDeCondicion.NO);
    }
}
