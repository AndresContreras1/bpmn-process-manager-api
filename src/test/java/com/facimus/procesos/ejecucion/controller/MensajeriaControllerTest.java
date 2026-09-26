package com.facimus.procesos.ejecucion.controller;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.ejecucion.dto.response.MensajeEntranteResponse;
import com.facimus.procesos.ejecucion.dto.response.MensajeSalienteResponse;
import com.facimus.procesos.ejecucion.model.EstadoMensajeSaliente;
import com.facimus.procesos.ejecucion.model.OrigenMensajeEntrante;
import com.facimus.procesos.ejecucion.model.ResultadoCorrelacion;
import com.facimus.procesos.ejecucion.service.DatosDelEntrante;
import com.facimus.procesos.ejecucion.service.MensajeriaService;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.TipoDestino;

@WebMvcTest(MensajeriaController.class)
class MensajeriaControllerTest {

    private static final LocalDateTime LLEGO = LocalDateTime.of(2026, 9, 24, 15, 5);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MensajeriaService mensajeriaService;

    @Test
    @DisplayName("POST /procesos/{id}/mensajes-entrantes - recibe el mensaje y dice que se hizo con el (200)")
    void recibir_diceQueSeHizoConEl() throws Exception {
        given(mensajeriaService.recibir(eq(1L), eq(10L), any())).willReturn(entrante(false));

        mockMvc.perform(post("/api/v1/procesos/10/mensajes-entrantes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Payment authorization result","clave":"ORD-1001",
                                 "cuerpo":{"status":"APPROVED"},"claveExterna":"webhook-7"}
                                """)
                        .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.resultado").value("ENTREGADO_A_CASO"))
                .andExpect(jsonPath("$.casoId").value(42))
                .andExpect(jsonPath("$.cuerpo.status").value("APPROVED"));

        verify(mensajeriaService).recibir(1L, 10L,
                DatosDelEntrante.aMano("Payment authorization result", "ORD-1001",
                        Map.of("status", "APPROVED"), "webhook-7"));
    }

    @Test
    @DisplayName("POST /procesos/{id}/mensajes-entrantes - el repetido se marca en la cabecera (200)")
    void recibir_repetido_loDiceEnLaCabecera() throws Exception {
        given(mensajeriaService.recibir(eq(1L), eq(10L), any())).willReturn(entrante(true));

        mockMvc.perform(post("/api/v1/procesos/10/mensajes-entrantes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Payment authorization result\",\"claveExterna\":\"webhook-7\"}")
                        .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.repetido").value(true));
    }

    @Test
    @DisplayName("POST /procesos/{id}/mensajes-entrantes - sin nombre no llega al service (400)")
    void recibir_sinNombre_esInvalido() throws Exception {
        mockMvc.perform(post("/api/v1/procesos/10/mensajes-entrantes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clave\":\"ORD-1001\"}")
                        .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nombre").exists());

        verifyNoInteractions(mensajeriaService);
    }

    @Test
    @DisplayName("POST /procesos/{id}/mensajes-entrantes - un mensaje que el proceso no recibe es 409")
    void recibir_mensajeQueNoEsSuyo_esConflicto() throws Exception {
        given(mensajeriaService.recibir(eq(1L), eq(10L), any()))
                .willThrow(new ReglaNegocioException("La versión publicada del proceso no recibe ningún mensaje "
                        + "llamado \"Refund requested\"."));

        mockMvc.perform(post("/api/v1/procesos/10/mensajes-entrantes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Refund requested\"}")
                        .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Refund requested")));
    }

    @Test
    @DisplayName("GET /procesos/{id}/bandeja-salida - responde lo que el proceso mando (200)")
    void bandejaDeSalida_respondeLoQueMando() throws Exception {
        given(mensajeriaService.bandejaDeSalida(eq(1L), eq(10L), eq(EstadoMensajeSaliente.PENDIENTE),
                any(Pageable.class))).willReturn(new PageResponse<>(List.of(saliente()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/procesos/10/bandeja-salida")
                        .param("estado", "PENDIENTE")
                        .with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nombre").value("Payment authorization request"))
                .andExpect(jsonPath("$.content[0].poolDestinoNombre").value("Payment gateway"))
                .andExpect(jsonPath("$.content[0].tickEntrega").value(3));
    }

    @Test
    @DisplayName("GET /procesos/{id}/bandeja-entrada - responde lo que llego y como acabo (200)")
    void bandejaDeEntrada_respondeLoQueLlego() throws Exception {
        given(mensajeriaService.bandejaDeEntrada(eq(1L), eq(10L), eq(ResultadoCorrelacion.DESCARTADO),
                any(Pageable.class))).willReturn(new PageResponse<>(List.of(entrante(false)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/procesos/10/bandeja-entrada")
                        .param("resultado", "DESCARTADO")
                        .with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nombre").value("Payment authorization result"));
    }

    @Test
    @DisplayName("GET /procesos/{id}/bandeja-salida - un tamano de pagina imposible no llega al service (400)")
    void bandejaDeSalida_conTamanoImposible_esInvalido() throws Exception {
        mockMvc.perform(get("/api/v1/procesos/10/bandeja-salida")
                        .param("tamano", "500")
                        .with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(mensajeriaService);
    }

    @Test
    @DisplayName("GET /casos/{id}/mensajes - responde lo que el caso mando y lo que recibio (200)")
    void mensajesDelCaso_respondeLasDosListas() throws Exception {
        given(mensajeriaService.salientesDelCaso(1L, 42L)).willReturn(List.of(saliente()));
        given(mensajeriaService.entrantesDelCaso(1L, 42L)).willReturn(List.of(entrante(false)));

        mockMvc.perform(get("/api/v1/casos/42/mensajes").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salientes[0].nombre").value("Payment authorization request"))
                .andExpect(jsonPath("$.entrantes[0].resultado").value("ENTREGADO_A_CASO"));
    }

    @Test
    @DisplayName("GET /casos/{id}/mensajes - el caso de otra tienda no existe para esta (404)")
    void mensajesDelCaso_deOtraTienda_noExiste() throws Exception {
        given(mensajeriaService.salientesDelCaso(1L, 99L))
                .willThrow(new RecursoNoEncontradoException("Caso no encontrado."));

        mockMvc.perform(get("/api/v1/casos/99/mensajes").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isNotFound());
    }

    private static MensajeSalienteResponse saliente() {
        return new MensajeSalienteResponse(17L, 42L, "ORD-1001", "Payment authorization request",
                "Payment gateway", Integracion.PAGOS, TipoDestino.SERVICIO_WEB, "ORD-1001",
                Map.of("orderId", "ORD-1001"), EstadoMensajeSaliente.PENDIENTE, 2, 3, 0, null, LLEGO);
    }

    private static MensajeEntranteResponse entrante(boolean repetido) {
        return new MensajeEntranteResponse(31L, 10L, 42L, "ORD-1001", "Payment authorization result", "ORD-1001",
                Map.of("status", "APPROVED"), OrigenMensajeEntrante.MANUAL, "webhook-7",
                ResultadoCorrelacion.ENTREGADO_A_CASO, 3, LLEGO, repetido);
    }
}
