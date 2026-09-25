package com.facimus.procesos.integracion.simulado;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.ejecucion.puerto.MensajeParaElSocio;
import com.facimus.procesos.ejecucion.puerto.ParametrosDeSimulacion;
import com.facimus.procesos.ejecucion.puerto.RespuestaDelSocio;
import com.facimus.procesos.ejecucion.puerto.RespuestaEntrante;
import com.facimus.procesos.modelado.model.Integracion;

/**
 * El socio que atiende mientras no existan los de verdad: da por entregado lo que le llega y contesta lo que el
 * diagrama dice que se contesta, con la misma clave. No decide nada mas.
 */
class EcoSimuladoTest {

    private final EcoSimulado eco = new EcoSimulado();

    @Test
    @DisplayName("Atiende a los participantes que no tienen socio propio, y contesta al tick siguiente")
    void atiende_alQueNoTieneSocioPropio() {
        assertThat(eco.integracion()).isEqualTo(Integracion.NINGUNA);
        assertThat(eco.latencia(ParametrosDeSimulacion.deFabrica())).isEqualTo(1);
    }

    @Test
    @DisplayName("Contesta la respuesta que el diagrama espera, con la clave del mensaje que recibio")
    void contesta_laRespuestaEsperadaConLaMismaClave() {
        RespuestaDelSocio respuesta = eco.recibir(new MensajeParaElSocio(42L, "Payment authorization request",
                "ORD-1", Map.of("total", 150), "Payment authorization result", 3,
                ParametrosDeSimulacion.deFabrica()));

        assertThat(respuesta.entregado()).isTrue();
        assertThat(respuesta.error()).isNull();
        assertThat(respuesta.respuestas()).singleElement()
                .returns("Payment authorization result", RespuestaEntrante::nombre)
                .returns("ORD-1", RespuestaEntrante::clave)
                .returns(Map.of(), RespuestaEntrante::cuerpo);
    }

    @Test
    @DisplayName("Un mensaje que no espera respuesta se da por entregado y no contesta nada")
    void sinRespuestaEsperada_noContestaNada() {
        RespuestaDelSocio respuesta = eco.recibir(new MensajeParaElSocio(42L, "Order status notification",
                "ORD-1", Map.of(), null, 3, ParametrosDeSimulacion.deFabrica()));

        assertThat(respuesta.entregado()).isTrue();
        assertThat(respuesta.respuestas()).isEmpty();
    }
}
