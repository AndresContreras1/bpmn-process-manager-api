package com.facimus.procesos.modelado.controller;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.xml.XmlRechazadoException;
import com.facimus.procesos.modelado.dto.response.HallazgoImportacionResponse;
import com.facimus.procesos.modelado.dto.response.ImportacionResponse;
import com.facimus.procesos.modelado.dto.response.InformeImportacionResponse;
import com.facimus.procesos.modelado.service.GuardadoDelModeloService;
import com.facimus.procesos.modelado.service.ImportacionBpmnService;

@WebMvcTest(ImportacionBpmnController.class)
class ImportacionBpmnControllerTest {

    private static final String XML = "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"/>";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImportacionBpmnService importacionBpmnService;

    @MockitoBean
    private GuardadoDelModeloService guardadoDelModeloService;

    @Test
    @DisplayName("POST /api/v1/procesos/importar - el proceso nuevo, con su Location y el informe (201)")
    void importar_creado() throws Exception {
        given(importacionBpmnService.importar(eq(1L), any(), eq(RolAcceso.EDITOR), any(InputStream.class), isNull()))
                .willReturn(importacion());

        mockMvc.perform(post("/api/v1/procesos/importar").with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_XML).content(XML))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/procesos/7"))
                .andExpect(jsonPath("$.creados").value(12))
                .andExpect(jsonPath("$.informe.soportados.tareas").value(4))
                .andExpect(jsonPath("$.informe.convertidos[0].elemento").value("Task_1"));
    }

    @Test
    @DisplayName("PUT /api/v1/procesos/{id}/modelo - lo que cambio en el borrador (200)")
    void reemplazar() throws Exception {
        given(guardadoDelModeloService.guardar(eq(1L), any(), eq(RolAcceso.ADMINISTRADOR), eq(7L),
                any(InputStream.class), any())).willReturn(importacion());

        mockMvc.perform(put("/api/v1/procesos/7/modelo").with(principal(RolAcceso.ADMINISTRADOR))
                        .contentType("application/bpmn+xml").content(XML))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.procesoId").value(7));
    }

    @Test
    @DisplayName("Un archivo que rompe las reglas responde 409 con todos los problemas")
    void reglasRotas() throws Exception {
        given(importacionBpmnService.importar(any(), any(), any(), any(InputStream.class), any()))
                .willThrow(new ReglaNegocioException("El archivo no se puede importar: 2 problemas.",
                        List.of("El flujo c sale de un evento de fin (F).", "El flujo d llega a un evento de inicio (I).")));

        mockMvc.perform(post("/api/v1/procesos/importar").with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_XML).content(XML))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    @DisplayName("Un XML que no se puede leer, o con un DOCTYPE, responde 400")
    void xmlRechazado() throws Exception {
        given(importacionBpmnService.importar(any(), any(), any(), any(InputStream.class), any()))
                .willThrow(new XmlRechazadoException("El XML trae un DOCTYPE: los DTD y las entidades no se aceptan."));

        mockMvc.perform(post("/api/v1/procesos/importar").with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.TEXT_XML).content(XML))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("DOCTYPE")));
    }

    @Test
    @DisplayName("Un cuerpo que no es XML responde 415")
    void otroTipo() throws Exception {
        mockMvc.perform(post("/api/v1/procesos/importar").with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("Sin sesion retorna 401")
    void sinSesion() throws Exception {
        mockMvc.perform(post("/api/v1/procesos/importar").contentType(MediaType.APPLICATION_XML)
                        .content(XML))
                .andExpect(status().isUnauthorized());
    }

    private static ImportacionResponse importacion() {
        return new ImportacionResponse(7L, "Order fulfillment", 12, 0, 0, new InformeImportacionResponse(
                Map.of("tareas", 4), List.of(new HallazgoImportacionResponse("Task_1",
                        "Una manualTask la hace una persona: queda como tarea de usuario.")), List.of(), List.of(),
                List.of()));
    }
}
