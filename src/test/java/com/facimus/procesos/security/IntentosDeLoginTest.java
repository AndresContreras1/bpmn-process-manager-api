package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * HU-03 y D34: la ventana deslizante de los intentos fallidos del login, contada en la tabla que comparten todas las
 * instancias, y la purga de lo que ya salio de ella.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class IntentosDeLoginTest {

    private static final Duration VENTANA = Duration.ofMinutes(15);

    @Autowired
    private JdbcTemplate jdbc;

    private final RelojDePrueba reloj = new RelojDePrueba();
    private IntentosDeLogin limitador;

    @BeforeEach
    void crearConTresIntentos() {
        limitador = new IntentosDeLogin(jdbc, 3, VENTANA, reloj);
    }

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
    @DisplayName("La purga se lleva los intentos que salieron de la ventana y deja los que todavia cuentan")
    void olvidarVencidos_dejaLosQueTodaviaCuentan() {
        limitador.registrar("ana|10.0.0.1");
        reloj.avanzar(Duration.ofMinutes(10));
        limitador.registrar("luis|10.0.0.1");
        reloj.avanzar(Duration.ofMinutes(6));

        assertThat(limitador.olvidarVencidos()).isEqualTo(1);
        assertThat(jdbc.queryForList("select clave from intentos_login", String.class))
                .containsExactly("luis|10.0.0.1");
    }
}
