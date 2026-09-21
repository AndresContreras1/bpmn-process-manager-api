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
import com.facimus.procesos.modelado.dto.response.PoolResponse;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.service.PoolService;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(PoolController.class)
class PoolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PoolService poolService;

    @Test
    @DisplayName("GET /api/v1/procesos/{procesoId}/pools - listar pools (200)")
    void listar_pools() throws Exception {
        PoolResponse pool = crearPool(1L, "Cliente");
        given(poolService.listarPorProceso(1L, 10L)).willReturn(List.of(pool));

        mockMvc.perform(get("/api/v1/procesos/10/pools").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Cliente"));
    }

    @Test
    @DisplayName("GET /api/v1/procesos/{procesoId}/pools - sin sesion retorna 401")
    void listar_sin_sesion() throws Exception {
        mockMvc.perform(get("/api/v1/procesos/10/pools"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/pools/{id} - detalle pool (200)")
    void detalle_pool() throws Exception {
        PoolResponse pool = crearPool(1L, "Cliente");
        given(poolService.obtener(1L, 1L)).willReturn(pool);

        mockMvc.perform(get("/api/v1/pools/1").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Cliente"));
    }

    @Test
    @DisplayName("GET /api/v1/pools/{id} - sin sesion retorna 401")
    void detalle_sin_sesion() throws Exception {
        mockMvc.perform(get("/api/v1/pools/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/procesos/{procesoId}/pools - crear pool (201)")
    void crear_pool() throws Exception {
        PoolResponse pool = crearPool(2L, "Proveedor");
        given(poolService.crear(eq(1L), eq(10L), anyString(), any(), anyBoolean())).willReturn(pool);

        mockMvc.perform(post("/api/v1/procesos/10/pools")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Proveedor","tipoParticipante":"PROVEEDOR","cajaNegra":false}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/pools/2"))
                .andExpect(jsonPath("$.nombre").value("Proveedor"));
    }

    @Test
    @DisplayName("POST /api/v1/procesos/{procesoId}/pools - validacion falla (400)")
    void crear_validacion_falla() throws Exception {
        mockMvc.perform(post("/api/v1/procesos/10/pools")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"","tipoParticipante":null,"cajaNegra":false}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/v1/pools/{id} - editar pool (200)")
    void editar_pool() throws Exception {
        PoolResponse pool = crearPool(1L, "Cliente VIP");
        given(poolService.editar(eq(1L), eq(1L), anyString(), any())).willReturn(pool);

        mockMvc.perform(put("/api/v1/pools/1")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Cliente VIP","tipoParticipante":"CLIENTE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Cliente VIP"));
    }

    @Test
    @DisplayName("DELETE /api/v1/pools/{id} - eliminar pool (204)")
    void eliminar_pool() throws Exception {
        doNothing().when(poolService).eliminar(1L, 1L);

        mockMvc.perform(delete("/api/v1/pools/1").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isNoContent());
    }

    private PoolResponse crearPool(Long id, String nombre) {
        return new PoolResponse(id, nombre, TipoParticipante.CLIENTE, false, 0, 10L);
    }

}
