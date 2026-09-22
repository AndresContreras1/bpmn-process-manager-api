package com.facimus.procesos.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DemasiadosIntentosExceptionTest {

    @Test
    @DisplayName("Retry-After redondea la espera hacia arriba, y el mensaje la dice en minutos")
    void esperaConFraccion_seRedondeaHaciaArriba() {
        DemasiadosIntentosException bloqueo = new DemasiadosIntentosException(Duration.ofMillis(899_200));

        assertThat(bloqueo.getSegundosDeEspera()).isEqualTo(900);
        assertThat(bloqueo.getMessage())
                .isEqualTo("Demasiados intentos fallidos de inicio de sesión. Intenta de nuevo en 15 minutos.");
    }

    @Test
    @DisplayName("Una espera casi cumplida pide al menos un segundo, nunca cero")
    void esperaCasiCumplida_pideUnSegundo() {
        DemasiadosIntentosException bloqueo = new DemasiadosIntentosException(Duration.ZERO);

        assertThat(bloqueo.getSegundosDeEspera()).isEqualTo(1);
        assertThat(bloqueo.getMessage()).endsWith("Intenta de nuevo en 1 minuto.");
    }
}
