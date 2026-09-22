package com.facimus.procesos.modelado.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.modelado.dto.response.CorrelacionResponse;
import com.facimus.procesos.modelado.service.CorrelacionService;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CorrelacionController.class)
class CorrelacionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CorrelacionService correlacionService;

    @Test
    @DisplayName("PUT /api/v1/mensajes/{mensajeId}/correlacion - la primera clave se define sin version (200)")
    void definir_correlacion() throws Exception {
        CorrelacionResponse c = crearCorrelacion(1L, "orderId");
        given(correlacionService.definir(eq(1L), eq(1L), eq(5L), anyString(), isNull())).willReturn(c);

        mockMvc.perform(put("/api/v1/mensajes/5/correlacion")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"criterio":"orderId"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criterio").value("orderId"))
                .andExpect(jsonPath("$.mensajeId").value(5));
    }

    @Test
    @DisplayName("PUT /api/v1/mensajes/{mensajeId}/correlacion - validacion falla (400)")
    void definir_validacion_falla() throws Exception {
        mockMvc.perform(put("/api/v1/mensajes/5/correlacion")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"criterio":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/mensajes/{mensajeId}/correlacion - obtener correlacion (200)")
    void obtener_correlacion() throws Exception {
        CorrelacionResponse c = crearCorrelacion(1L, "customerId");
        given(correlacionService.obtener(1L, 5L)).willReturn(c);

        mockMvc.perform(get("/api/v1/mensajes/5/correlacion").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criterio").value("customerId"));
    }

    @Test
    @DisplayName("GET /api/v1/mensajes/{mensajeId}/correlacion - sin sesion retorna 401")
    void obtener_sin_sesion() throws Exception {
        mockMvc.perform(get("/api/v1/mensajes/5/correlacion"))
                .andExpect(status().isUnauthorized());
    }

    private CorrelacionResponse crearCorrelacion(Long id, String criterio) {
        return new CorrelacionResponse(id, criterio, 5L, 0L, null, null, null, null);
    }

}
