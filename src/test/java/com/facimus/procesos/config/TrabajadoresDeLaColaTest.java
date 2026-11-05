package com.facimus.procesos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.common.trabajos.ManejadorDeTrabajo;
import com.facimus.procesos.common.trabajos.Trabajo;

/**
 * Los trabajadores de la cola, encendidos: un trabajo encolado corre solo, sin que nadie llame a la cola. Es la unica
 * prueba en la que algo corre por detras, y por eso tiene su propio contexto.
 */
@SpringBootTest(properties = {"trabajos.trabajar=true", "trabajos.pausa=100ms"})
@ActiveProfiles("test")
class TrabajadoresDeLaColaTest {

    @TestConfiguration
    static class TrabajoDePrueba {

        @Bean
        ManejadorDeTrabajo saludar() {
            return new ManejadorDeTrabajo() {
                @Override
                public String tipo() {
                    return "saludar";
                }

                @Override
                public void ejecutar(Trabajo trabajo) {
                    // No hace falta hacer nada: que corra es lo que se prueba.
                }
            };
        }
    }

    @Autowired
    private ColaDeTrabajos cola;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Con los trabajadores encendidos, un trabajo encolado corre solo y queda hecho")
    void trabajoEncolado_correSolo() {
        long id = cola.encolar(null, "saludar", "{}");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(
                jdbc.queryForObject("select estado from trabajos where id = ?", String.class, id)).isEqualTo("HECHO"));
    }
}
