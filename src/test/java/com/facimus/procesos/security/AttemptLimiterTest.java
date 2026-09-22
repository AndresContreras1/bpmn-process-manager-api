package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttemptLimiterTest {

    private static final Duration VENTANA = Duration.ofMinutes(15);

    private final RelojDePrueba reloj = new RelojDePrueba();
    private final AttemptLimiter limitador = new AttemptLimiter(3, VENTANA, 100, reloj);

    @Test
    @DisplayName("Por debajo del maximo una clave no espera, y una clave sin intentos tampoco")
    void debajoDelMaximo_noEspera() {
        limitador.registrar("ana|10.0.0.1");
        limitador.registrar("ana|10.0.0.1");

        assertThat(limitador.espera("ana|10.0.0.1")).isEmpty();
        assertThat(limitador.espera("nadie|10.0.0.1")).isEmpty();
    }

    @Test
    @DisplayName("Con el maximo dentro de la ventana, espera a que el intento mas viejo salga de ella")
    void enElMaximo_esperaAQueSalgaElIntentoMasViejo() {
        limitador.registrar("ana|10.0.0.1");
        reloj.avanzar(Duration.ofMinutes(5));
        limitador.registrar("ana|10.0.0.1");
        limitador.registrar("ana|10.0.0.1");

        assertThat(limitador.espera("ana|10.0.0.1")).contains(Duration.ofMinutes(10));

        reloj.avanzar(Duration.ofMinutes(10));
        assertThat(limitador.espera("ana|10.0.0.1")).isEmpty();
    }

    @Test
    @DisplayName("La ventana es deslizante: al salir el intento mas viejo queda uno, y otro fallo vuelve a bloquear")
    void ventanaDeslizante_devuelveUnIntentoCadaVez() {
        limitador.registrar("ana|10.0.0.1");
        reloj.avanzar(Duration.ofMinutes(5));
        limitador.registrar("ana|10.0.0.1");
        limitador.registrar("ana|10.0.0.1");
        reloj.avanzar(Duration.ofMinutes(10));

        assertThat(limitador.espera("ana|10.0.0.1")).isEmpty();
        limitador.registrar("ana|10.0.0.1");
        assertThat(limitador.espera("ana|10.0.0.1")).contains(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("Reiniciar olvida los intentos de una clave, y cada clave cuenta los suyos")
    void reiniciar_olvidaSoloEsaClave() {
        for (int intento = 0; intento < 3; intento++) {
            limitador.registrar("ana|10.0.0.1");
            limitador.registrar("luis|10.0.0.1");
        }

        limitador.reiniciar("ana|10.0.0.1");

        assertThat(limitador.espera("ana|10.0.0.1")).isEmpty();
        assertThat(limitador.espera("luis|10.0.0.1")).isPresent();
    }

    @Test
    @DisplayName("Recuerda un numero acotado de claves y olvida primero la que menos se usa")
    void clavesAcotadas_olvidaLaMenosUsada() {
        AttemptLimiter acotado = new AttemptLimiter(1, VENTANA, 2, reloj);
        acotado.registrar("primera");
        acotado.registrar("segunda");
        acotado.espera("primera");

        acotado.registrar("tercera");

        assertThat(acotado.espera("segunda")).isEmpty();
        assertThat(acotado.espera("primera")).isPresent();
        assertThat(acotado.espera("tercera")).isPresent();
    }
}
