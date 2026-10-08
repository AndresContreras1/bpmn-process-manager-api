package com.facimus.procesos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.facimus.procesos.ProcesosApplication;
import com.facimus.procesos.postgres.PostgresDePrueba;
import com.facimus.procesos.postgres.PostgresDePrueba.ConexionDePrueba;

/**
 * D34: el outbox de los eventos entre modulos. Un evento queda anotado en la misma transaccion que lo publica; si su
 * receptor falla, sigue ahi, y el siguiente arranque de la aplicacion lo vuelve a entregar. Las dos instancias arrancan
 * a mano, una despues de la otra, sobre una base propia de esta prueba.
 */
class OutboxDeEventosTest {

    private static final AtomicBoolean RECEPTOR_FALLA = new AtomicBoolean();
    private static final List<String> RECIBIDOS = new CopyOnWriteArrayList<>();

    /** Lo que publicaria un modulo para que otro haga algo despues, como mandar un correo. */
    record PedidoConfirmado(String pedido) {
    }

    @TestConfiguration
    static class ReceptorDePrueba {

        @Bean
        Receptor receptor() {
            return new Receptor();
        }
    }

    public static class Receptor {

        @ApplicationModuleListener
        public void alConfirmarse(PedidoConfirmado evento) {
            if (RECEPTOR_FALLA.get()) {
                throw new IllegalStateException("el receptor todavia no puede");
            }
            RECIBIDOS.add(evento.pedido());
        }
    }

    @Test
    @DisplayName("Un evento cuyo receptor fallo sigue en el outbox, y el siguiente arranque lo entrega")
    void eventoSinTerminar_sobreviveAUnReinicio() {
        ConexionDePrueba base = PostgresDePrueba.nuevaBaseCompartida();

        RECEPTOR_FALLA.set(true);
        try (ConfigurableApplicationContext primera = arrancar(base)) {
            publicarEnUnaTransaccion(primera, new PedidoConfirmado("pedido 1001"));
            await().atMost(Duration.ofSeconds(10))
                    .until(() -> contar(primera, "select count(*) from event_publication where status = 'FAILED'") == 1);
        }
        assertThat(RECIBIDOS).isEmpty();

        RECEPTOR_FALLA.set(false);
        try (ConfigurableApplicationContext segunda = arrancar(base)) {
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(RECIBIDOS).containsExactly("pedido 1001");
                assertThat(contar(segunda, "select count(*) from event_publication")).isZero();
            });
        }
    }

    @Test
    @DisplayName("Un evento publicado en una transaccion que se deshace no llega a nadie ni queda en el outbox")
    void transaccionDeshecha_noDejaEvento() {
        ConexionDePrueba base = PostgresDePrueba.nuevaBaseCompartida();
        RECEPTOR_FALLA.set(false);
        RECIBIDOS.clear();

        try (ConfigurableApplicationContext instancia = arrancar(base)) {
            new TransactionTemplate(instancia.getBean(PlatformTransactionManager.class)).executeWithoutResult(estado -> {
                instancia.publishEvent(new PedidoConfirmado("pedido 1002"));
                estado.setRollbackOnly();
            });

            assertThat(contar(instancia, "select count(*) from event_publication")).isZero();
        }
        assertThat(RECIBIDOS).isEmpty();
    }

    private static ConfigurableApplicationContext arrancar(ConexionDePrueba base) {
        return new SpringApplicationBuilder(ProcesosApplication.class, ReceptorDePrueba.class)
                .profiles("test")
                .run("--server.port=0", "--spring.datasource.url=" + base.url(),
                        "--spring.datasource.username=" + base.usuario(),
                        "--spring.datasource.password=" + base.clave());
    }

    private static void publicarEnUnaTransaccion(ConfigurableApplicationContext instancia, Object evento) {
        new TransactionTemplate(instancia.getBean(PlatformTransactionManager.class))
                .executeWithoutResult(estado -> instancia.publishEvent(evento));
    }

    private static int contar(ConfigurableApplicationContext instancia, String consulta) {
        Integer filas = instancia.getBean(JdbcTemplate.class).queryForObject(consulta, Integer.class);
        return filas == null ? 0 : filas;
    }
}
