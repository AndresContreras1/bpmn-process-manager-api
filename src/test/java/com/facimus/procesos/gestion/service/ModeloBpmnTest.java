package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PR 53: el nombre del archivo sirve en cualquier sistema y se reconoce de que proceso es. */
class ModeloBpmnTest {

    @Test
    @DisplayName("Sin tildes, sin espacios ni signos, en minusculas")
    void nombreLimpio() {
        assertThat(ModeloBpmn.delBorrador("Órden de compra / Q4 (España)", "<x/>").archivo())
                .isEqualTo("orden-de-compra-q4-espana.bpmn");
        assertThat(ModeloBpmn.deLaVersion("Order fulfillment", 3, "<x/>").archivo())
                .isEqualTo("order-fulfillment-v3.bpmn");
    }

    @Test
    @DisplayName("Un nombre de solo signos, o vacio, no deja el archivo sin nombre")
    void sinNada() {
        assertThat(ModeloBpmn.delBorrador("¿¡!?", "<x/>").archivo()).isEqualTo("proceso.bpmn");
        assertThat(ModeloBpmn.delBorrador(null, "<x/>").archivo()).isEqualTo("proceso.bpmn");
    }

    @Test
    @DisplayName("Un nombre muy largo se corta, sin dejar un guion al final")
    void largo() {
        String nombre = ModeloBpmn.nombre("a".repeat(59) + " bcdefgh");

        assertThat(nombre).hasSizeLessThanOrEqualTo(60).doesNotEndWith("-");
    }
}
