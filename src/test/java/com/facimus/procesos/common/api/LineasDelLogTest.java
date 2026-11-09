package com.facimus.procesos.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import com.facimus.procesos.common.IntegracionFallidaException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/** Lo que los errores escriben en el log: un valor que llega de afuera no puede partir la linea en dos. */
class LineasDelLogTest {

    /** Lo que un atacante querria ver como una linea propia del log. */
    private static final String LINEA_FALSA = "INFO Pago aprobado";

    private final ApiExceptionHandler handler = new ApiExceptionHandler();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final ListAppender<ILoggingEvent> log = new ListAppender<>();
    private final ServletWebRequest peticion = new ServletWebRequest(
            new MockHttpServletRequest("POST", "/api/v1/procesos"));

    @BeforeEach
    void escuchar() {
        log.start();
        logger.addAppender(log);
    }

    @AfterEach
    void dejarDeEscuchar() {
        logger.detachAppender(log);
        logger.setLevel(null);
    }

    @Test
    @DisplayName("El mensaje de la base, con un salto de linea dentro, queda en una sola linea")
    void mensajeDeLaBase() {
        handler.manejarConflictoDeDatos(new DataIntegrityViolationException("duplicado",
                new IllegalStateException("Key (correo)=(ana@acme.com\n" + LINEA_FALSA + ") already exists")), peticion);

        assertThat(lineas()).singleElement().satisfies(linea -> assertThat(linea)
                .doesNotContain("\n", "\r")
                .contains("uri=/api/v1/procesos", "ana@acme.com_" + LINEA_FALSA));
    }

    @Test
    @DisplayName("La respuesta de un servicio externo, con saltos de cualquier sistema, queda en una sola linea")
    void respuestaDeUnServicioExterno() {
        handler.manejarIntegracionFallida(new IntegracionFallidaException("rechazado\r\n" + LINEA_FALSA + "\r"),
                peticion);

        assertThat(lineas()).singleElement().satisfies(linea -> assertThat(linea)
                .doesNotContain("\n", "\r")
                .contains("rechazado_" + LINEA_FALSA + "_"));
    }

    @Test
    @DisplayName("Un error inesperado deja la ruta en una linea y la traza de la excepcion aparte")
    void errorInesperado() {
        IllegalStateException error = new IllegalStateException("fallo");
        handler.manejarErrorInesperado(error, peticion);

        assertThat(log.list).singleElement().satisfies(evento -> {
            assertThat(evento.getFormattedMessage()).isEqualTo("Error inesperado en uri=/api/v1/procesos");
            assertThat(evento.getThrowableProxy().getMessage()).isEqualTo("fallo");
        });
    }

    @Test
    @DisplayName("Con el log apagado los errores se responden igual y no se arma ninguna linea")
    void logApagado() {
        logger.setLevel(Level.OFF);

        assertThat(handler.manejarConflictoDeDatos(new DataIntegrityViolationException("duplicado"), peticion)
                .getStatus()).isEqualTo(409);
        assertThat(handler.manejarIntegracionFallida(new IntegracionFallidaException("rechazado"), peticion)
                .getStatus()).isEqualTo(502);
        assertThat(handler.manejarErrorInesperado(new IllegalStateException("fallo"), peticion).getStatus())
                .isEqualTo(500);
        assertThat(log.list).isEmpty();
    }

    @Test
    @DisplayName("Un valor nulo se escribe como null y no rompe el error que lo traia")
    void valorNulo() {
        assertThat(ApiExceptionHandler.enUnaLinea(null)).isEqualTo("null");
    }

    private List<String> lineas() {
        return log.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
