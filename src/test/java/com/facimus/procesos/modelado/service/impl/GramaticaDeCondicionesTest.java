package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * La gramatica con la que se escribe la condicion de un gateway. Vive en el mismo paquete que la clase para poder
 * leerla directamente: es una funcion pura, sin Spring ni base de datos detras.
 */
class GramaticaDeCondicionesTest {

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
        assertThat(GramaticaDeCondiciones.problema(condicion)).isEmpty();
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
        Optional<String> problema = GramaticaDeCondiciones.problema(condicion);

        assertThat(problema).isPresent();
        assertThat(problema.orElseThrow()).endsWith(".");
    }

    @ParameterizedTest
    @ValueSource(strings = {"order.total > APPROVED", "order.total < 'pronto'", "order.total >= true"})
    @DisplayName("Mayor y menor solo comparan numeros o fechas")
    void comparacionDeOrdenEntreTipos_noCompila(String condicion) {
        assertThat(GramaticaDeCondiciones.problema(condicion).orElseThrow())
                .contains("solo comparan numeros o fechas");
    }
}
