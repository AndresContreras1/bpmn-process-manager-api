package com.facimus.procesos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.api.ApiExceptionHandler;
import com.facimus.procesos.common.api.IdDePeticionFilter;
import com.facimus.procesos.common.api.Problemas;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.security.SesionEnCookies;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La traza de cada peticion, con la aplicacion entera: su traceId llega a los errores y a las lineas del log que se
 * escriben mientras se atiende, y sin la direccion de un colector ningun span sale de la API.
 */
class TrazasIntegracionTest {

    private static final String ADMIN = "admin@trazas.com";
    private static final String CLAVE = "marea-violeta-del-sur";

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    @AutoConfigureMockMvc
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Import(SinColector.ControllerQueFalla.class)
    class SinColector {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private JsonMapper jsonMapper;

        @Autowired
        private EmpresaService empresaService;

        @Autowired
        private ApplicationContext contexto;

        private final ListAppender<ILoggingEvent> log = new ListAppender<>();
        private String token;

        @BeforeAll
        void registrarTiendaYEntrar() throws Exception {
            empresaService.registrar("Tienda con trazas", "900515253-1", "contacto@trazas.com", "Administradora",
                    ADMIN, CLAVE);
            token = SesionEnCookies.acceso(mockMvc.perform(SesionEnCookies.login().contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(new LoginRequest(ADMIN, CLAVE))))
                    .andExpect(status().isOk()));
        }

        @AfterEach
        void soltarElLog() {
            ((Logger) LoggerFactory.getLogger(ApiExceptionHandler.class)).detachAppender(log);
        }

        @Test
        @DisplayName("Un error lleva el traceId de su traza, y cada peticion tiene la suya")
        void error_llevaElTraceIdDeSuTraza() throws Exception {
            String primera = trazaDe(mockMvc.perform(get("/api/v1/procesos"))
                    .andExpect(status().isUnauthorized()).andReturn());
            String segunda = trazaDe(mockMvc.perform(get("/api/v1/procesos"))
                    .andExpect(status().isUnauthorized()).andReturn());

            assertThat(primera).matches("[0-9a-f]{32}");
            assertThat(segunda).matches("[0-9a-f]{32}").isNotEqualTo(primera);
        }

        @Test
        @DisplayName("La linea del log de un error lleva el traceId y el requestId de la peticion que fallo")
        void logDelError_llevaLosDosIds() throws Exception {
            log.start();
            ((Logger) LoggerFactory.getLogger(ApiExceptionHandler.class)).addAppender(log);

            MvcResult resultado = mockMvc.perform(get("/api/v1/prueba-de-trazas/falla")
                            .with(SesionEnCookies.conSesion(token)))
                    .andExpect(status().isInternalServerError())
                    .andReturn();

            List<ILoggingEvent> errores = log.list.stream().filter(evento -> evento.getThrowableProxy() != null)
                    .toList();
            assertThat(errores).hasSize(1);
            assertThat(errores.getFirst().getMDCPropertyMap())
                    .containsEntry(Problemas.ID_DE_TRAZA, trazaDe(resultado))
                    .containsEntry(IdDePeticionFilter.CLAVE_EN_EL_LOG,
                            resultado.getResponse().getHeader(IdDePeticionFilter.CABECERA));
        }

        @Test
        @DisplayName("Sin la direccion de un colector no hay exportador: ningun span sale de la API")
        void sinColector_noExporta() {
            assertThat(contexto.getBeanNamesForType(SpanExporter.class)).isEmpty();
        }

        @Test
        @DisplayName("Las metricas no salen por OTLP: las lee Prometheus, y el registro de OTLP no esta en el classpath")
        void metricas_noSalenPorOtlp() {
            assertThat(ClassUtils.isPresent("io.micrometer.registry.otlp.OtlpMeterRegistry", null)).isFalse();
        }

        private String trazaDe(MvcResult resultado) throws Exception {
            JsonNode cuerpo = jsonMapper.readTree(resultado.getResponse().getContentAsString());
            return cuerpo.get(Problemas.ID_DE_TRAZA).asString();
        }

        /**
         * Un endpoint que solo existe en esta prueba, para fallar como falla un error de programacion. Va dentro de
         * la clase con las pruebas: anidado en una clase sin pruebas propias, Spring lo escanearia como un controller
         * mas en el contexto de todas las demas.
         */
        @RestController
        static class ControllerQueFalla {

            @GetMapping("/api/v1/prueba-de-trazas/falla")
            String fallar() {
                throw new IllegalStateException("falla a proposito");
            }
        }
    }

    @Nested
    @SpringBootTest(properties = "management.opentelemetry.tracing.export.otlp.endpoint=http://localhost:4318/v1/traces")
    @ActiveProfiles("test")
    class ConColector {

        @Autowired
        private ApplicationContext contexto;

        @Test
        @DisplayName("Con la direccion de un colector, los spans salen por OTLP")
        void conColector_exportaPorOtlp() {
            assertThat(contexto.getBeanNamesForType(OtlpHttpSpanExporter.class)).hasSize(1);
        }
    }
}
