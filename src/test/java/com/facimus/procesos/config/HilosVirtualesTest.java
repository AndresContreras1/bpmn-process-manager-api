package com.facimus.procesos.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tomcat.util.threads.VirtualThreadExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * El servidor de verdad atiende cada peticion en un hilo virtual: Tomcat no tiene un pool de hilos que se agote, y el
 * techo del trabajo simultaneo es el pool de conexiones a la base. Usa la misma configuracion que PuertoDeGestionTest
 * para compartir su contexto y no arrancar otro servidor.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@ActiveProfiles("test")
class HilosVirtualesTest {

    @Autowired
    private ServletWebServerApplicationContext contexto;

    @Test
    @DisplayName("Tomcat atiende las peticiones en hilos virtuales")
    void tomcat_atiendeEnHilosVirtuales() {
        TomcatWebServer servidor = (TomcatWebServer) contexto.getWebServer();

        assertThat(servidor.getTomcat().getConnector().getProtocolHandler().getExecutor())
                .isInstanceOf(VirtualThreadExecutor.class);
    }
}
