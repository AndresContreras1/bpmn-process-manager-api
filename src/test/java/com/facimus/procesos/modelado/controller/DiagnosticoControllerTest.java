package com.facimus.procesos.modelado.controller;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.modelado.dto.response.DiagnosticoResponse;
import com.facimus.procesos.modelado.dto.response.HallazgoDiagnosticoResponse;
import com.facimus.procesos.modelado.model.Severidad;
import com.facimus.procesos.modelado.service.DiagnosticoService;

@WebMvcTest(DiagnosticoController.class)
class DiagnosticoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiagnosticoService diagnosticoService;

    @Test
    @DisplayName("GET /api/v1/procesos/{id}/diagnostico - los hallazgos, tambien para solo lectura (200)")
    void diagnosticar_devuelveLosHallazgos() throws Exception {
        given(diagnosticoService.diagnosticar(1L, 10L, null)).willReturn(new DiagnosticoResponse(10L, null, 1, 1,
                List.of(
                new HallazgoDiagnosticoResponse("E-05", Severidad.ALTA, "Actividad \"Pick and pack items\"", 30L,
                        "No sale ningun flujo de este nodo.", "Conectalo con el paso siguiente."),
                new HallazgoDiagnosticoResponse("A-10", Severidad.BAJA, "Lane \"Returns\"", 8L,
                        "Esta lane no tiene ningun nodo.", "Ponle el trabajo que hace su rol."))));

        mockMvc.perform(get("/api/v1/procesos/10/diagnostico").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.procesoId").value(10))
                .andExpect(jsonPath("$.errores").value(1))
                .andExpect(jsonPath("$.advertencias").value(1))
                .andExpect(jsonPath("$.hallazgos[0].codigo").value("E-05"))
                .andExpect(jsonPath("$.hallazgos[0].severidad").value("ALTA"))
                .andExpect(jsonPath("$.hallazgos[0].elementoId").value(30))
                .andExpect(jsonPath("$.hallazgos[1].codigo").value("A-10"));
    }

    @Test
    @DisplayName("GET /api/v1/procesos/{id}/diagnostico?sinElemento - simula el borrado (200)")
    void diagnosticar_simulandoUnBorrado() throws Exception {
        given(diagnosticoService.diagnosticar(1L, 10L, "GATEWAY:12"))
                .willReturn(new DiagnosticoResponse(10L, "GATEWAY:12", 0, 1, List.of(
                        new HallazgoDiagnosticoResponse("A-06", Severidad.MEDIA, "Flujo de \"A\" a \"B\"", 20L,
                                "Si se borra el elemento por el que se pregunta, tambien se va el flujo.",
                                "Revisa si hay que rehacer esta parte del diagrama antes de borrar."))));

        mockMvc.perform(get("/api/v1/procesos/10/diagnostico").param("sinElemento", "GATEWAY:12")
                        .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinElemento").value("GATEWAY:12"))
                .andExpect(jsonPath("$.hallazgos[0].codigo").value("A-06"));
    }

    @Test
    @DisplayName("GET /api/v1/procesos/{id}/diagnostico?sinElemento - elemento mal escrito (400)")
    void diagnosticar_elementoMalEscrito_devuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/procesos/10/diagnostico").param("sinElemento", "GATEWAY-12")
                        .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parámetro inválido"));
    }

    @Test
    @DisplayName("GET /api/v1/procesos/{id}/diagnostico - proceso de otra tienda (404)")
    void diagnosticar_procesoAjeno_devuelve404() throws Exception {
        given(diagnosticoService.diagnosticar(1L, 99L, null))
                .willThrow(new RecursoNoEncontradoException("Proceso no encontrado."));

        mockMvc.perform(get("/api/v1/procesos/99/diagnostico").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Proceso no encontrado."));
    }

    @Test
    @DisplayName("GET /api/v1/procesos/{id}/diagnostico - sin token (401)")
    void diagnosticar_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/procesos/10/diagnostico"))
                .andExpect(status().isUnauthorized());
    }
}
