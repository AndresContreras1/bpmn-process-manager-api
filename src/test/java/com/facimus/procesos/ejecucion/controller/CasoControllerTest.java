package com.facimus.procesos.ejecucion.controller;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.facimus.procesos.ejecucion.dto.response.CasoDetalleResponse;
import com.facimus.procesos.ejecucion.dto.response.CasoResponse;
import com.facimus.procesos.ejecucion.dto.response.EventoCasoResponse;
import com.facimus.procesos.ejecucion.dto.response.PasoDelCasoResponse;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.model.TipoNodoCaso;
import com.facimus.procesos.ejecucion.service.CasoService;

@WebMvcTest(CasoController.class)
class CasoControllerTest {

    private static final LocalDateTime ABIERTO = LocalDateTime.of(2026, 9, 24, 15, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CasoService casoService;

    @Test
    @DisplayName("POST /api/v1/procesos/{id}/casos - abre el caso y responde donde quedo (201)")
    void abrir_devuelveElCasoYSuUbicacion() throws Exception {
        given(casoService.abrir(eq(1L), eq(1L), eq(10L), eq("ORD-1001"), any())).willReturn(caso());

        mockMvc.perform(post("/api/v1/procesos/10/casos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"referencia\":\"ORD-1001\",\"variables\":{\"order\":{\"total\":150}}}")
                        .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/casos/42"))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.referencia").value("ORD-1001"))
                .andExpect(jsonPath("$.estado").value("ABIERTO"))
                .andExpect(jsonPath("$.versionNumero").value(2));
    }

    @Test
    @DisplayName("POST /api/v1/procesos/{id}/casos - una referencia larguisima no llega al service (400)")
    void abrir_conReferenciaMuyLarga_esInvalida() throws Exception {
        mockMvc.perform(post("/api/v1/procesos/10/casos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"referencia\":\"" + "X".repeat(121) + "\"}")
                        .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.referencia").exists());

        verifyNoInteractions(casoService);
    }

    @Test
    @DisplayName("POST /api/v1/procesos/{id}/casos - un proceso que empieza por mensaje lo dice (409)")
    void abrir_procesoQueEmpiezaPorMensaje_esConflicto() throws Exception {
        given(casoService.abrir(any(), any(), any(), any(), any()))
                .willThrow(new ReglaNegocioException("Este proceso se inicia con el mensaje \"Order placed\"; "
                        + "envíelo como mensaje entrante."));

        mockMvc.perform(post("/api/v1/procesos/10/casos")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este proceso se inicia con el mensaje \"Order placed\"; "
                        + "envíelo como mensaje entrante."));
    }

    @Test
    @DisplayName("GET /api/v1/casos - una pagina de casos para cualquier rol (200)")
    void listar_devuelveLaPagina() throws Exception {
        given(casoService.listar(eq(1L), eq(10L), eq(EstadoCaso.ABIERTO), eq("ORD-1001"), any(Pageable.class)))
                .willReturn(new PageResponse<>(List.of(caso()), 0, 10, 1, 1));

        mockMvc.perform(get("/api/v1/casos")
                        .param("procesoId", "10").param("estado", "ABIERTO").param("referencia", "ORD-1001")
                        .with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(42))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/casos - un orden que no esta en la lista blanca no llega al service (400)")
    void listar_conOrdenInvalido_esInvalida() throws Exception {
        mockMvc.perform(get("/api/v1/casos").param("orden", "variables,asc")
                        .with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(casoService);
    }

    @Test
    @DisplayName("GET /api/v1/casos - una pagina mas grande que el tope no llega al service (400)")
    void listar_conTamanoExcesivo_esInvalida() throws Exception {
        mockMvc.perform(get("/api/v1/casos").param("tamano", "51").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(casoService);
    }

    @Test
    @DisplayName("GET /api/v1/casos/{id} - el caso con sus pasos y sus variables (200)")
    void obtener_devuelveElDetalle() throws Exception {
        given(casoService.obtener(1L, 42L)).willReturn(new CasoDetalleResponse(caso(),
                List.of(new PasoDelCasoResponse(77L, 12L, "Pick and pack items", TipoNodoCaso.ACTIVIDAD, "USUARIO",
                        2L, EstadoActividadCaso.EN_ESPERA, 1, null, 0, null, null, 0L, ABIERTO, null, ABIERTO)),
                Map.of("payment", Map.of("status", "APPROVED"))));

        mockMvc.perform(get("/api/v1/casos/42").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caso.id").value(42))
                .andExpect(jsonPath("$.pasos[0].nodoNombre").value("Pick and pack items"))
                .andExpect(jsonPath("$.pasos[0].estado").value("EN_ESPERA"))
                .andExpect(jsonPath("$.variables.payment.status").value("APPROVED"));
    }

    @Test
    @DisplayName("GET /api/v1/casos/{id} - el caso de otra tienda no existe (404)")
    void obtener_deOtraTienda_noExiste() throws Exception {
        given(casoService.obtener(1L, 99L)).willThrow(new RecursoNoEncontradoException("Caso no encontrado."));

        mockMvc.perform(get("/api/v1/casos/99").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Caso no encontrado."));
    }

    @Test
    @DisplayName("GET /api/v1/casos/{id}/eventos - la linea de tiempo del caso (200)")
    void eventos_devuelvenLaLineaDeTiempo() throws Exception {
        given(casoService.eventos(1L, 42L)).willReturn(List.of(
                new EventoCasoResponse(1L, 0, ABIERTO, TipoEventoCaso.CASO_ABIERTO, "El caso empieza.", 2L),
                new EventoCasoResponse(2L, 0, ABIERTO, TipoEventoCaso.TAREA_CREADA, "Queda en la bandeja.", null)));

        mockMvc.perform(get("/api/v1/casos/42/eventos").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("CASO_ABIERTO"))
                .andExpect(jsonPath("$[0].autorId").value(2))
                .andExpect(jsonPath("$[1].tipo").value("TAREA_CREADA"));
    }

    @Test
    @DisplayName("POST /api/v1/casos/{id}/cancelar - cierra el caso antes de tiempo (200)")
    void cancelar_devuelveElCasoCancelado() throws Exception {
        given(casoService.cancelar(1L, 1L, 42L)).willReturn(new CasoResponse(42L, 10L, "Order fulfillment", 2,
                "ORD-1001", EstadoCaso.CANCELADO, 0, 0, ABIERTO, ABIERTO, 1L, 2L));

        mockMvc.perform(post("/api/v1/casos/42/cancelar").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.fechaFin").exists());
    }

    @Test
    @DisplayName("POST /api/v1/casos/{id}/cancelar - un caso ya cerrado es un conflicto (409)")
    void cancelar_casoCerrado_esConflicto() throws Exception {
        given(casoService.cancelar(1L, 1L, 42L)).willThrow(new ReglaNegocioException("El caso ya está cerrado."));

        mockMvc.perform(post("/api/v1/casos/42/cancelar").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El caso ya está cerrado."));
    }

    @Test
    @DisplayName("PATCH /api/v1/casos/{id}/variables - reemplaza las variables (200)")
    void variables_devuelveElCaso() throws Exception {
        given(casoService.corregirVariables(eq(1L), eq(42L), any(), eq(0L))).willReturn(caso());

        mockMvc.perform(patch("/api/v1/casos/42/variables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variables\":{\"payment\":{\"status\":\"APPROVED\"}},\"version\":0}")
                        .with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42));

        verify(casoService).corregirVariables(1L, 42L, Map.of("payment", Map.of("status", "APPROVED")), 0L);
    }

    @Test
    @DisplayName("PATCH /api/v1/casos/{id}/variables - sin version no se guarda nada (400)")
    void variables_sinVersion_esInvalida() throws Exception {
        mockMvc.perform(patch("/api/v1/casos/42/variables")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"variables\":{}}")
                        .with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.version").exists());

        verifyNoInteractions(casoService);
    }

    @Test
    @DisplayName("POST /api/v1/casos/{id}/reintentar - vuelve a evaluar lo que fallo (200)")
    void reintentar_devuelveElCaso() throws Exception {
        given(casoService.reintentar(1L, 1L, 42L)).willReturn(caso());

        mockMvc.perform(post("/api/v1/casos/42/reintentar").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ABIERTO"));
    }

    private static CasoResponse caso() {
        return new CasoResponse(42L, 10L, "Order fulfillment", 2, "ORD-1001", EstadoCaso.ABIERTO, 0, null, ABIERTO,
                null, 0L, 2L);
    }
}
