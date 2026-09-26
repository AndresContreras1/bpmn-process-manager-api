package com.facimus.procesos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.tomcat.autoconfigure.TomcatServerProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.zaxxer.hikari.HikariDataSource;

/** Lo que cambia entre dev y prod, verificado con la aplicacion completa de cada perfil. */
class PerfilesTest {

    private static final String CONSOLA_H2 = "/h2-console/*";

    @Nested
    @SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:perfil-dev")
    @ActiveProfiles("dev")
    @AutoConfigureMockMvc
    class Dev {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ApplicationContext context;

        @Test
        @DisplayName("dev publica el contrato de la API y Swagger UI")
        void dev_publicaLaDocumentacion() throws Exception {
            mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
            mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("dev registra la consola H2 en /h2-console")
        void dev_registraLaConsolaH2() {
            assertThat(rutasDeServlets(context)).contains(CONSOLA_H2);
        }

        @Test
        @DisplayName("dev programa la purga nocturna de las tablas tecnicas")
        void dev_programaLaPurga() {
            assertThat(context.getBeanNamesForType(LimpiezaConfig.class)).isNotEmpty();
        }

        @Test
        @DisplayName("dev programa el reloj de las tiendas que pidieron que corriera solo")
        void dev_programaElRelojDeLaSimulacion() {
            assertThat(context.getBeanNamesForType(SimulacionConfig.class)).isNotEmpty();
        }
    }

    @Nested
    @SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:perfil-test")
    @ActiveProfiles("test")
    class Test_ {

        @Autowired
        private ApplicationContext context;

        @Test
        @DisplayName("test no programa nada: ni la purga ni el reloj corren por detras de una prueba")
        void test_noProgramaNada() {
            assertThat(context.getBeanNamesForType(LimpiezaConfig.class)).isEmpty();
            assertThat(context.getBeanNamesForType(SimulacionConfig.class)).isEmpty();
        }
    }

    @Nested
    @SpringBootTest(properties = {
            // El build no tiene PostgreSQL: se prueba la configuracion de prod sobre una H2 en memoria.
            "spring.datasource.url=jdbc:h2:mem:perfil-prod",
            "JWT_SECRET=prod-profile-test-signing-key-with-32-characters"})
    @ActiveProfiles("prod")
    @AutoConfigureMockMvc
    class Prod {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ApplicationContext context;

        @Autowired
        private EmpresaRepository empresaRepository;

        @Test
        @DisplayName("prod no publica el contrato de la API ni Swagger UI")
        void prod_noPublicaLaDocumentacion() throws Exception {
            mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
            mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("prod fija el tamano del pool de conexiones y de los hilos que atienden peticiones")
        void prod_fijaLaConcurrencia() {
            HikariDataSource pool = context.getBean(HikariDataSource.class);
            TomcatServerProperties tomcat = context.getBean(TomcatServerProperties.class);

            // Pool de tamano fijo: el minimo igual al maximo, para no abrir y cerrar conexiones bajo carga.
            assertThat(pool.getMaximumPoolSize()).isEqualTo(10);
            assertThat(pool.getMinimumIdle()).isEqualTo(pool.getMaximumPoolSize());
            assertThat(pool.getConnectionTimeout()).isEqualTo(3000);
            assertThat(tomcat.getThreads().getMax()).isEqualTo(200);
            assertThat(tomcat.getAcceptCount()).isEqualTo(200);
        }

        @Test
        @DisplayName("prod no registra la consola H2 ni siembra la tienda demo")
        void prod_sinConsolaH2NiTiendaDemo() {
            assertThat(rutasDeServlets(context)).doesNotContain(CONSOLA_H2);
            assertThat(context.getBeanNamesForType(DatosDemoInitializer.class)).isEmpty();
            assertThat(empresaRepository.count()).isZero();
        }
    }

    private static List<String> rutasDeServlets(ApplicationContext context) {
        List<String> rutas = new ArrayList<>();
        for (ServletRegistrationBean<?> registro : context.getBeansOfType(ServletRegistrationBean.class).values()) {
            rutas.addAll(registro.getUrlMappings());
        }
        return rutas;
    }
}
