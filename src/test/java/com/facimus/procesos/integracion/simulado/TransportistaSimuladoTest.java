package com.facimus.procesos.integracion.simulado;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.ejecucion.puerto.MensajeParaElSocio;
import com.facimus.procesos.ejecucion.puerto.ParametrosDeSimulacion;
import com.facimus.procesos.ejecucion.puerto.RespuestaDelSocio;
import com.facimus.procesos.ejecucion.puerto.RespuestaEntrante;
import com.facimus.procesos.modelado.model.Integracion;

/**
 * D7: el transportista es el unico socio que contesta en dos momentos. Recoge el paquete y, si el diagrama dice
 * que ese participante avisa por su cuenta, deja dicha la confirmacion de entrega para dentro de unos ticks.
 */
class TransportistaSimuladoTest {

    private static final String PETICION = "Shipment request";
    private static final String CONFIRMACION = "Shipment confirmation";

    private final TransportistaSimulado transportista = new TransportistaSimulado();

    @Test
    @DisplayName("Atiende a los participantes que llevan paquetes, y tarda lo que la tienda dice")
    void atiende_alosDeTransporte() {
        assertThat(transportista.integracion()).isEqualTo(Integracion.TRANSPORTE);
        assertThat(transportista.latencia(new ParametrosDeSimulacion(42L, 10, 1, null, 5, 3, 0, 2))).isEqualTo(5);
    }

    @Test
    @DisplayName("La confirmacion de entrega se deja dicha para dentro de los ticks que la tienda decidio")
    void laConfirmacion_llegaMasTarde() {
        RespuestaDelSocio respuesta = transportista.recibir(peticion(7L, parametros(0)));

        assertThat(respuesta.entregado()).isTrue();
        assertThat(respuesta.respuestas()).singleElement()
                .returns(CONFIRMACION, RespuestaEntrante::nombre)
                .returns("ORD-7", RespuestaEntrante::clave)
                .returns(3, RespuestaEntrante::enTicks);
        assertThat(respuesta.respuestas().getFirst().cuerpo())
                .containsEntry("trackingNumber", "SIM-TRK-7")
                .containsEntry("status", "DELIVERED");
    }

    @Test
    @DisplayName("Con la tasa de perdida al cien, el paquete se pierde y la confirmacion lo dice")
    void tasaCien_pierdeElPaquete() {
        assertThat(IntStream.range(0, 20)
                .mapToObj(caso -> estado(transportista.recibir(peticion((long) caso, parametros(100)))))
                .distinct()).containsExactly("LOST");
    }

    @Test
    @DisplayName("Cuando el diagrama espera una respuesta, tambien contesta la guia en el acto")
    void conRespuestaEsperada_contestaLaGuiaEnElActo() {
        RespuestaDelSocio respuesta = transportista.recibir(new MensajeParaElSocio(7L, PETICION, "ORD-7",
                Map.of(), "Shipment created", CONFIRMACION, 1, parametros(0)));

        assertThat(respuesta.respuestas()).extracting(RespuestaEntrante::nombre, RespuestaEntrante::enTicks)
                .containsExactly(org.assertj.core.api.Assertions.tuple("Shipment created", 0),
                        org.assertj.core.api.Assertions.tuple(CONFIRMACION, 3));
    }

    @Test
    @DisplayName("Un participante que ni contesta ni avisa recibe el paquete y no dice nada mas")
    void sinRespuestaNiAviso_noContestaNada() {
        RespuestaDelSocio respuesta = transportista.recibir(new MensajeParaElSocio(7L, PETICION, "ORD-7",
                Map.of(), null, null, 1, parametros(0)));

        assertThat(respuesta.entregado()).isTrue();
        assertThat(respuesta.respuestas()).isEmpty();
    }

    private static String estado(RespuestaDelSocio respuesta) {
        return String.valueOf(respuesta.respuestas().getFirst().cuerpo().get("status"));
    }

    private static MensajeParaElSocio peticion(Long casoId, ParametrosDeSimulacion parametros) {
        return new MensajeParaElSocio(casoId, PETICION, "ORD-" + casoId, Map.of(), null, CONFIRMACION, 1,
                parametros);
    }

    private static ParametrosDeSimulacion parametros(int tasaPerdida) {
        return new ParametrosDeSimulacion(42L, 10, 1, null, 1, 3, tasaPerdida, 2);
    }
}
