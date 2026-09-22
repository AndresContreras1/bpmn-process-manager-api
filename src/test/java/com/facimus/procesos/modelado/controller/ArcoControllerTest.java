package com.facimus.procesos.modelado.controller;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.modelado.dto.response.ArcoResponse;
import com.facimus.procesos.modelado.service.ArcoService;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(ArcoController.class)
class ArcoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArcoService arcoService;

    @Test
    @DisplayName("POST /api/v1/arcos - crear arco (201)")
    void crear_arco() throws Exception {
        ArcoResponse arco = crearArco(1L);
        given(arcoService.crear(eq(1L), anyLong(), anyLong(), anyString(), anyString())).willReturn(arco);

        mockMvc.perform(post("/api/v1/arcos")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"origenId":10,"destinoId":20,"etiqueta":"si","condicion":"aprobado"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/arcos/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.etiqueta").value("si"));
    }

    @Test
    @DisplayName("POST /api/v1/arcos - sin sesion retorna 401")
    void crear_sin_sesion() throws Exception {
        mockMvc.perform(post("/api/v1/arcos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"origenId":10,"destinoId":20,"etiqueta":"si","condicion":"x"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/arcos - validacion falla sin origenId (400)")
    void crear_validacion_falla() throws Exception {
        mockMvc.perform(post("/api/v1/arcos")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"origenId":null,"destinoId":null,"etiqueta":"","condicion":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/arcos/{id} - detalle arco (200)")
    void detalle_arco() throws Exception {
        ArcoResponse arco = crearArco(1L);
        given(arcoService.obtener(1L, 1L)).willReturn(arco);

        mockMvc.perform(get("/api/v1/arcos/1").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etiqueta").value("si"));
    }

    @Test
    @DisplayName("GET /api/v1/arcos/{id} - sin sesion retorna 401")
    void detalle_sin_sesion() throws Exception {
        mockMvc.perform(get("/api/v1/arcos/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/pools/{poolId}/arcos - listar arcos (200)")
    void listar_arcos() throws Exception {
        ArcoResponse arco = crearArco(1L);
        given(arcoService.listarPorPool(1L, 5L)).willReturn(List.of(arco));

        mockMvc.perform(get("/api/v1/pools/5/arcos").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].etiqueta").value("si"));
    }

    @Test
    @DisplayName("GET /api/v1/pools/{poolId}/arcos - sin sesion retorna 401")
    void listar_sin_sesion() throws Exception {
        mockMvc.perform(get("/api/v1/pools/5/arcos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/v1/arcos/{id} - editar arco (200)")
    void editar_arco() throws Exception {
        ArcoResponse arco = new ArcoResponse(1L, "no", "aprobado", 10L, 20L, 5L, 0L);
        given(arcoService.editar(eq(1L), eq(1L), anyString(), anyString(), eq(3L))).willReturn(arco);

        mockMvc.perform(put("/api/v1/arcos/1")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"etiqueta":"no","condicion":"rechazado","version":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etiqueta").value("no"));
    }

    @Test
    @DisplayName("DELETE /api/v1/arcos/{id} - eliminar arco (204)")
    void eliminar_arco() throws Exception {
        doNothing().when(arcoService).eliminar(1L, 1L);
        mockMvc.perform(delete("/api/v1/arcos/1").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isNoContent());
    }

    private ArcoResponse crearArco(Long id) {
        return new ArcoResponse(id, "si", "aprobado", 10L, 20L, 5L, 0L);
    }

}
