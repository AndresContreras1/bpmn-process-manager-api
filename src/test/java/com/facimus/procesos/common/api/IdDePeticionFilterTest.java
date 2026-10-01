package com.facimus.procesos.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;

/** El id que acompana a cada peticion: de donde sale, a donde llega y cuando se descarta el que trae el cliente. */
class IdDePeticionFilterTest {

    private final IdDePeticionFilter filtro = new IdDePeticionFilter();

    @Test
    @DisplayName("Una peticion sin id recibe uno nuevo, que sale en la cabecera y en el log mientras se atiende")
    void sinId_recibeUnoNuevo() throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        AtomicReference<String> enElLog = new AtomicReference<>();

        filtro.doFilter(peticion(), respuesta, anotarElLogEn(enElLog));

        String id = respuesta.getHeader(IdDePeticionFilter.CABECERA);
        assertThat(id).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(enElLog.get()).isEqualTo(id);
    }

    @Test
    @DisplayName("El id que trae la peticion se conserva: es el que le puso el proxy de delante o el cliente")
    void conIdValido_loConserva() throws Exception {
        MockHttpServletRequest peticion = peticion();
        peticion.addHeader(IdDePeticionFilter.CABECERA, "a1B2.c3_d4-e5");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        AtomicReference<String> enElLog = new AtomicReference<>();

        filtro.doFilter(peticion, respuesta, anotarElLogEn(enElLog));

        assertThat(respuesta.getHeader(IdDePeticionFilter.CABECERA)).isEqualTo("a1B2.c3_d4-e5");
        assertThat(enElLog.get()).isEqualTo("a1B2.c3_d4-e5");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "con espacios", "salto\nde linea", "<script>", "id;DROP",
            "12345678901234567890123456789012345678901234567890123456789012345"})
    @DisplayName("Un id vacio, de mas de 64 caracteres o con caracteres que no son seguros se cambia por uno nuevo")
    void conIdQueNoSirve_loCambia(String recibido) throws Exception {
        MockHttpServletRequest peticion = peticion();
        peticion.addHeader(IdDePeticionFilter.CABECERA, recibido);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        filtro.doFilter(peticion, respuesta, (req, res) -> { });

        assertThat(respuesta.getHeader(IdDePeticionFilter.CABECERA)).isNotEqualTo(recibido).hasSize(36);
    }

    @Test
    @DisplayName("El despacho de error del servidor conserva el id de la peticion que fallo")
    void despachoDeError_conservaElId() throws Exception {
        MockHttpServletRequest peticion = peticion();
        filtro.doFilter(peticion, new MockHttpServletResponse(), (req, res) -> { });
        String original = (String) peticion.getAttribute(IdDePeticionFilter.class.getName() + ".ID");

        peticion.setDispatcherType(DispatcherType.ERROR);
        MockHttpServletResponse deError = new MockHttpServletResponse();
        AtomicReference<String> enElLog = new AtomicReference<>();
        filtro.doFilter(peticion, deError, anotarElLogEn(enElLog));

        assertThat(original).isNotNull();
        assertThat(deError.getHeader(IdDePeticionFilter.CABECERA)).isEqualTo(original);
        assertThat(enElLog.get()).isEqualTo(original);
    }

    @Test
    @DisplayName("El id sale del log al terminar, tambien si la peticion termina con una excepcion")
    void alTerminar_elIdSaleDelLog() throws Exception {
        filtro.doFilter(peticion(), new MockHttpServletResponse(), (req, res) -> { });
        assertThat(MDC.get(IdDePeticionFilter.CLAVE_EN_EL_LOG)).isNull();

        assertThatThrownBy(() -> filtro.doFilter(peticion(), new MockHttpServletResponse(), (req, res) -> {
            throw new IllegalStateException("falla");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get(IdDePeticionFilter.CLAVE_EN_EL_LOG)).isNull();
    }

    @Test
    @DisplayName("Un Problem Details armado mientras se atiende la peticion lleva su id")
    void problemas_llevanElIdDeLaPeticion() throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        AtomicReference<Object> enElProblema = new AtomicReference<>();

        filtro.doFilter(peticion(), respuesta, (req, res) -> enElProblema.set(
                Problemas.de(HttpStatus.CONFLICT, "Titulo", "Detalle")
                        .getProperties().get(Problemas.ID_DE_PETICION)));

        assertThat(enElProblema.get()).isEqualTo(respuesta.getHeader(IdDePeticionFilter.CABECERA));
    }

    private static MockHttpServletRequest peticion() {
        return new MockHttpServletRequest("GET", "/api/v1/procesos");
    }

    private static FilterChain anotarElLogEn(AtomicReference<String> enElLog) {
        return (req, res) -> enElLog.set(MDC.get(IdDePeticionFilter.CLAVE_EN_EL_LOG));
    }
}
