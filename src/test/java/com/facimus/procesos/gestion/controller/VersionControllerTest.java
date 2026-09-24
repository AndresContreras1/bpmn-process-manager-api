package com.facimus.procesos.gestion.controller;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.dto.response.VersionResponse;
import com.facimus.procesos.gestion.model.EstadoVersion;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.service.VersionService;

@WebMvcTest(VersionController.class)
class VersionControllerTest {

    private static final String HUELLA = "9f2c4a6b8d0e1f23456789abcdef0123456789abcdef0123456789abcdef0123";
    private static final LocalDateTime PUBLICADA = LocalDateTime.of(2026, 9, 23, 11, 5);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VersionService versionService;

    @Test
    @DisplayName("GET /api/v1/procesos/{id}/versiones - de la mas reciente a la mas vieja, para cualquier rol (200)")
    void listar_devuelveLasVersiones() throws Exception {
        given(versionService.listar(1L, 10L)).willReturn(List.of(
                new VersionResponse(8L, 10L, 2, EstadoVersion.VIGENTE, PUBLICADA, 5L, HUELLA),
                new VersionResponse(4L, 10L, 1, EstadoVersion.RETIRADA, PUBLICADA.minusDays(3), 5L, HUELLA)));

        mockMvc.perform(get("/api/v1/procesos/10/versiones").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].numero").value(2))
                .andExpect(jsonPath("$[0].estado").value("VIGENTE"))
                .andExpect(jsonPath("$[0].huella").value(HUELLA))
                .andExpect(jsonPath("$[1].numero").value(1))
                .andExpect(jsonPath("$[1].estado").value("RETIRADA"));
    }

    @Test
    @DisplayName("GET /api/v1/procesos/{id}/versiones/{n} - una version (200)")
    void obtener_devuelveLaVersion() throws Exception {
        given(versionService.obtener(1L, 10L, 2))
                .willReturn(new VersionResponse(8L, 10L, 2, EstadoVersion.VIGENTE, PUBLICADA, 5L, HUELLA));

        mockMvc.perform(get("/api/v1/procesos/10/versiones/2").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.procesoId").value(10))
                .andExpect(jsonPath("$.numero").value(2))
                .andExpect(jsonPath("$.publicadoPor").value(5));
    }

    @Test
    @DisplayName("GET /api/v1/procesos/{id}/versiones/{n}/diagrama - el diagrama tal como se publico (200)")
    void diagrama_devuelveLaDefinicionGuardada() throws Exception {
        given(versionService.definicion(1L, 10L, 1))
                .willReturn("{\"proceso\":{\"id\":10,\"nombre\":\"Order fulfillment\"},\"pools\":[]}");

        mockMvc.perform(get("/api/v1/procesos/10/versiones/1/diagrama").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.proceso.nombre").value("Order fulfillment"))
                .andExpect(jsonPath("$.pools").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/procesos/{id}/versiones/{n} - una version que no existe (404)")
    void obtener_versionQueNoExiste_noEncontrada() throws Exception {
        given(versionService.obtener(1L, 10L, 7))
                .willThrow(new RecursoNoEncontradoException("Versión no encontrada."));

        mockMvc.perform(get("/api/v1/procesos/10/versiones/7").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.detail").value("Versión no encontrada."));
    }
}
