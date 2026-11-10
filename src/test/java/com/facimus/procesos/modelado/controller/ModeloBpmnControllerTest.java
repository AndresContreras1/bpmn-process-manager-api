package com.facimus.procesos.modelado.controller;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.service.ModeloBpmn;
import com.facimus.procesos.modelado.service.ModeloBpmnService;

@WebMvcTest(ModeloBpmnController.class)
class ModeloBpmnControllerTest {

    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<bpmn:definitions/>\n";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ModeloBpmnService modeloBpmnService;

    @Test
    @DisplayName("GET /api/v1/procesos/{id}/modelo - un adjunto .bpmn en UTF-8, tambien para solo lectura (200)")
    void exportar_adjunto() throws Exception {
        given(modeloBpmnService.exportar(1L, 10L)).willReturn(ModeloBpmn.delBorrador("Orden de compra", XML));

        mockMvc.perform(get("/api/v1/procesos/10/modelo").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/xml;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"orden-de-compra.bpmn\""))
                .andExpect(content().string(XML));
    }

    @Test
    @DisplayName("GET /api/v1/procesos/{id}/modelo - pedido como XML, un error sigue saliendo en JSON (404)")
    void exportar_procesoAjeno() throws Exception {
        given(modeloBpmnService.exportar(1L, 99L)).willThrow(new RecursoNoEncontradoException("Proceso no encontrado."));

        mockMvc.perform(get("/api/v1/procesos/99/modelo").accept(MediaType.APPLICATION_XML)
                        .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Proceso no encontrado."));
    }

    @Test
    @DisplayName("GET /api/v1/procesos/{id}/modelo - sin sesion retorna 401")
    void exportar_sinSesion() throws Exception {
        mockMvc.perform(get("/api/v1/procesos/10/modelo"))
                .andExpect(status().isUnauthorized());
    }
}
