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
 * D7: la pasarela decide en dos pasos. La regla de la tienda rechaza seguro, y lo que la regla no rechaza queda en
 * manos de la tasa, que tambien decide siempre igual porque sale de la semilla.
 */
class PasarelaSimuladaTest {

    private static final String PETICION = "Payment authorization request";
    private static final String RESPUESTA = "Payment authorization result";

    private final PasarelaSimulada pasarela = new PasarelaSimulada();

    @Test
    @DisplayName("Atiende a los participantes que son una pasarela de pagos")
    void atiende_alosDePagos() {
        assertThat(pasarela.integracion()).isEqualTo(Integracion.PAGOS);
    }

    @Test
    @DisplayName("Tarda lo que la tienda dice que tarda")
    void latencia_esLaDeLaTienda() {
        assertThat(pasarela.latencia(new ParametrosDeSimulacion(42L, 0, 4, null, 1, 3, 5, 2))).isEqualTo(4);
    }

    @Test
    @DisplayName("Sin regla y con tasa cero, aprueba y contesta lo que el diagrama espera")
    void sinRegla_apruebaYContesta() {
        RespuestaDelSocio respuesta = pasarela.recibir(peticion(7L, Map.of("total", 150), parametros(0, null)));

        assertThat(respuesta.entregado()).isTrue();
        assertThat(respuesta.respuestas()).singleElement()
                .returns(RESPUESTA, RespuestaEntrante::nombre)
                .returns("ORD-7", RespuestaEntrante::clave);
        assertThat(respuesta.respuestas().getFirst().cuerpo())
                .containsEntry("status", "APPROVED")
                .containsEntry("transactionId", "SIM-PAY-7")
                .containsEntry("amount", 150);
    }

    @Test
    @DisplayName("Con tasa cien rechaza siempre, sin que el hash tenga nada que decir")
    void tasaCien_rechazaSiempre() {
        assertThat(IntStream.range(0, 30)
                .mapToObj(caso -> estado(pasarela.recibir(peticion((long) caso, Map.of("total", 10),
                        parametros(100, null)))))
                .distinct()).containsExactly("DECLINED");
    }

    @Test
    @DisplayName("La regla rechaza lo que dice, y lo demas lo aprueba aunque la tasa sea cero")
    void laRegla_rechazaLoQueDice() {
        ParametrosDeSimulacion conRegla = parametros(0, "total > 5000");

        assertThat(estado(pasarela.recibir(peticion(7L, Map.of("total", 9000), conRegla)))).isEqualTo("DECLINED");
        assertThat(estado(pasarela.recibir(peticion(7L, Map.of("total", 150), conRegla)))).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("Lo que la regla no rechaza lo sigue decidiendo la tasa")
    void loQueLaReglaNoRechaza_loDecideLaTasa() {
        ParametrosDeSimulacion conReglaYTasa = parametros(100, "total > 5000");

        assertThat(estado(pasarela.recibir(peticion(7L, Map.of("total", 150), conReglaYTasa))))
                .isEqualTo("DECLINED");
    }

    @Test
    @DisplayName("Una regla mal escrita no tumba la pasarela: decide la tasa, que es lo que habria decidido")
    void reglaMalEscrita_dejaDecidirALaTasa() {
        assertThat(estado(pasarela.recibir(peticion(7L, Map.of("total", 150), parametros(0, "total >")))))
                .isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("El mismo pedido decide siempre igual; dos pedidos distintos no corren la misma suerte")
    void elMismoPedido_decideSiempreIgual() {
        ParametrosDeSimulacion mitad = parametros(50, null);

        assertThat(estado(pasarela.recibir(peticion(7L, Map.of("total", 150), mitad))))
                .isEqualTo(estado(pasarela.recibir(peticion(7L, Map.of("total", 150), mitad))));
        assertThat(IntStream.range(0, 40)
                .mapToObj(caso -> estado(pasarela.recibir(peticion((long) caso, Map.of("total", 150), mitad))))
                .distinct()).containsExactlyInAnyOrder("APPROVED", "DECLINED");
    }

    @Test
    @DisplayName("Un mensaje a la pasarela que nadie espera que se conteste llega y no contesta nada")
    void sinRespuestaEsperada_noContestaNada() {
        RespuestaDelSocio respuesta = pasarela.recibir(new MensajeParaElSocio(7L, "Payment receipt", "ORD-7",
                Map.of(), null, 1, parametros(0, null)));

        assertThat(respuesta.entregado()).isTrue();
        assertThat(respuesta.respuestas()).isEmpty();
    }

    private static String estado(RespuestaDelSocio respuesta) {
        return String.valueOf(respuesta.respuestas().getFirst().cuerpo().get("status"));
    }

    private static MensajeParaElSocio peticion(Long casoId, Map<String, Object> cuerpo,
            ParametrosDeSimulacion parametros) {
        return new MensajeParaElSocio(casoId, PETICION, "ORD-" + casoId, cuerpo, RESPUESTA, 1, parametros);
    }

    private static ParametrosDeSimulacion parametros(int tasa, String regla) {
        return new ParametrosDeSimulacion(42L, tasa, 1, regla, 1, 3, 5, 2);
    }
}
