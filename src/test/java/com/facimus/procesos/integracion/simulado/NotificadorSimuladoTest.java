package com.facimus.procesos.integracion.simulado;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.ejecucion.puerto.MensajeParaElSocio;
import com.facimus.procesos.ejecucion.puerto.ParametrosDeSimulacion;
import com.facimus.procesos.ejecucion.puerto.RespuestaDelSocio;
import com.facimus.procesos.modelado.model.Integracion;

/**
 * D7: el notificador y el cliente son los dos socios que no contestan nada. El primero puede fallar, y cuando
 * falla es el siFalla del mensaje el que decide; al segundo le llega siempre lo que la tienda le manda.
 */
class NotificadorSimuladoTest {

    private static final String AVISO = "Order status notification";

    private final NotificadorSimulado notificador = new NotificadorSimulado();
    private final ClienteSimulado cliente = new ClienteSimulado();

    @Test
    @DisplayName("Cada socio atiende a los suyos y los dos contestan al tick siguiente")
    void cadaSocio_atiendeALosSuyos() {
        assertThat(notificador.integracion()).isEqualTo(Integracion.NOTIFICACIONES);
        assertThat(cliente.integracion()).isEqualTo(Integracion.CLIENTE);
        assertThat(notificador.latencia(ParametrosDeSimulacion.deFabrica())).isEqualTo(1);
        assertThat(cliente.latencia(ParametrosDeSimulacion.deFabrica())).isEqualTo(1);
    }

    @Test
    @DisplayName("Con la tasa de fallo en cero la notificacion llega y no contesta nada")
    void tasaCero_laNotificacionLlega() {
        RespuestaDelSocio respuesta = notificador.recibir(aviso(7L, 0));

        assertThat(respuesta.entregado()).isTrue();
        assertThat(respuesta.error()).isNull();
        assertThat(respuesta.respuestas()).isEmpty();
    }

    @Test
    @DisplayName("Con la tasa de fallo al cien no llega ninguna, y dice por que")
    void tasaCien_noLlegaNinguna() {
        assertThat(IntStream.range(0, 20)
                .mapToObj(caso -> notificador.recibir(aviso((long) caso, 100)).entregado())
                .distinct()).containsExactly(false);
        assertThat(notificador.recibir(aviso(7L, 100)).error()).isEqualTo("el destinatario no la recibio");
    }

    @Test
    @DisplayName("Al cliente le llega siempre, aunque la tasa de fallo de las notificaciones sea del cien")
    void alCliente_leLlegaSiempre() {
        RespuestaDelSocio respuesta = cliente.recibir(new MensajeParaElSocio(7L, AVISO, "ORD-7", Map.of(), null,
                null, 1, new ParametrosDeSimulacion(42L, 10, 1, null, 1, 3, 5, 100)));

        assertThat(respuesta.entregado()).isTrue();
        assertThat(respuesta.respuestas()).isEmpty();
    }

    private static MensajeParaElSocio aviso(Long casoId, int tasaFallo) {
        return new MensajeParaElSocio(casoId, AVISO, "ORD-" + casoId, Map.of(), null, null, 1,
                new ParametrosDeSimulacion(42L, 10, 1, null, 1, 3, 5, tasaFallo));
    }
}
