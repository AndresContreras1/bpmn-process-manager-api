package com.facimus.procesos.ejecucion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;

import tools.jackson.databind.json.JsonMapper;

/**
 * Las variables con las que decide un caso: lo que se guardo, mas lo que el propio caso sabe de si mismo. Es una
 * clase sin Spring ni base de datos, asi que se prueba llamandola.
 */
class VariablesDelCasoTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    @DisplayName("Lee lo que se guardo, por dentro de los objetos anidados")
    void valor_leeLoGuardado() {
        VariablesDelCaso variables = de("{\"payment\":{\"status\":\"APPROVED\"},\"order\":{\"total\":150}}");

        assertThat(variables.valor("payment.status")).contains("APPROVED");
        assertThat(variables.valor("order.total")).contains(150);
        assertThat(variables.valor("order.vip")).isEmpty();
        assertThat(variables.valor("payment.status.detalle")).isEmpty();
    }

    @Test
    @DisplayName("El caso se lee a si mismo: su referencia y su tick no se guardan, se preguntan")
    void valor_delCaso_saleDeSusColumnas() {
        VariablesDelCaso variables = de("{}");

        assertThat(variables.valor("caso.referencia")).contains("ORD-1");
        assertThat(variables.valor("caso.tick")).contains(0);
        assertThat(variables.valor("caso.loQueSea")).isEmpty();
    }

    @Test
    @DisplayName("Las rutas que no estaban se anotan una sola vez, en el orden en que se pidieron")
    void ausentes_seAnotanUnaVez() {
        VariablesDelCaso variables = de("{}");

        assertThat(variables.valor("payment.status")).isEmpty();
        variables.anotarAusente("payment.status");
        variables.anotarAusente("order.total");
        variables.anotarAusente("payment.status");

        assertThat(variables.ausentes()).containsExactly("payment.status", "order.total");
        variables.olvidarAusentes();
        assertThat(variables.ausentes()).isEmpty();
    }

    @Test
    @DisplayName("Los datos de una tarea entran bajo tarea.<nombre en camello>, sin pisar lo que ya habia")
    void ponerDeLaTarea_guardaBajoElNombreEnCamello() {
        VariablesDelCaso variables = de("{\"payment\":{\"status\":\"APPROVED\"}}");

        variables.ponerDeLaTarea("Pick and pack items", Map.of("packedItems", 3));
        variables.ponerDeLaTarea("Receive order", Map.of("checked", true));

        assertThat(variables.comoJson()).isEqualTo("{\"payment\":{\"status\":\"APPROVED\"},"
                + "\"tarea\":{\"pickAndPackItems\":{\"packedItems\":3},\"receiveOrder\":{\"checked\":true}}}");
    }

    @Test
    @DisplayName("Un grupo de valores entra con el nombre que se le da, como el cuerpo de un mensaje")
    void poner_guardaBajoSuNombre() {
        VariablesDelCaso variables = de("{}");

        variables.poner("payment", new LinkedHashMap<>(Map.of("status", "DECLINED")));

        assertThat(variables.valor("payment.status")).contains("DECLINED");
        assertThat(variables.comoJson()).isEqualTo("{\"payment\":{\"status\":\"DECLINED\"}}");
    }

    @Test
    @DisplayName("Lo que se guarda no lleva lo del caso: eso se relee de sus columnas cada vez")
    void comoJson_noGuardaLoDelCaso() {
        VariablesDelCaso variables = de("{\"order\":{\"total\":150}}");

        assertThat(variables.comoJson()).isEqualTo("{\"order\":{\"total\":150}}").doesNotContain("caso");
    }

    @ParameterizedTest
    @CsvSource({
        "Pick and pack items, pickAndPackItems",
        "Receive order, receiveOrder",
        "Cancel order, cancelOrder",
        "  Ship   order  , shipOrder",
        "Revisar pedido (urgente), revisarPedidoUrgente",
        "Aprobar, aprobar"
    })
    @DisplayName("El nombre de un nodo se convierte en algo que se pueda escribir en una condicion")
    void enCamello_convierteElNombreDelNodo(String nombre, String esperado) {
        assertThat(VariablesDelCaso.enCamello(nombre)).isEqualTo(esperado);
    }

    private VariablesDelCaso de(String guardadas) {
        return VariablesDelCaso.de(Caso.builder().referencia("ORD-1").estado(EstadoCaso.ABIERTO)
                .variables(guardadas).build(), json);
    }
}
