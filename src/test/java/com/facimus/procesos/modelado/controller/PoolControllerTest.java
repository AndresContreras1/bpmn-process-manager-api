package com.facimus.procesos.modelado.controller;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import com.facimus.procesos.common.ConflictoDeVersionException;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.modelado.dto.response.PoolResponse;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.service.PoolService;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

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
        given(poolService.crear(eq(1L), eq(1L), eq(10L), anyString(), any(), anyBoolean())).willReturn(pool);

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
        given(poolService.editar(eq(1L), eq(1L), eq(1L), anyString(), any(), eq(3L))).willReturn(pool);

        mockMvc.perform(put("/api/v1/pools/1")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Cliente VIP","tipoParticipante":"CLIENTE","version":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Cliente VIP"));
    }

    @Test
    @DisplayName("PUT /api/v1/pools/{id} - una version vieja devuelve 409 con Problem Details")
    void editar_versionVieja_devuelve409() throws Exception {
        given(poolService.editar(eq(1L), eq(1L), eq(1L), anyString(), any(), eq(2L)))
                .willThrow(new ConflictoDeVersionException(2L, 3L));

        mockMvc.perform(put("/api/v1/pools/1")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Cliente VIP","tipoParticipante":"CLIENTE","version":2}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflicto de versión"))
                .andExpect(jsonPath("$.detail").value("Otra persona guardó un cambio después de que leíste este "
                        + "recurso: enviaste la versión 2 y la actual es la 3. Recarga y vuelve a intentar."));
    }

    @Test
    @DisplayName("PUT /api/v1/pools/{id} - si la base rechaza una edicion simultanea tambien devuelve 409")
    void editar_edicionSimultanea_devuelve409() throws Exception {
        given(poolService.editar(eq(1L), eq(1L), eq(1L), anyString(), any(), eq(2L)))
                .willThrow(new ObjectOptimisticLockingFailureException(Pool.class, 1L));

        mockMvc.perform(put("/api/v1/pools/1")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Cliente VIP","tipoParticipante":"CLIENTE","version":2}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflicto de versión"))
                .andExpect(jsonPath("$.detail")
                        .value("Otra persona guardó un cambio en este recurso al mismo tiempo. "
                                + "Recarga y vuelve a intentar."));
    }

    @Test
    @DisplayName("DELETE /api/v1/pools/{id} - eliminar pool (204)")
    void eliminar_pool() throws Exception {
        doNothing().when(poolService).eliminar(1L, 1L, 1L);

        mockMvc.perform(delete("/api/v1/pools/1").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isNoContent());
    }

    private PoolResponse crearPool(Long id, String nombre) {
        return new PoolResponse(id, nombre, TipoParticipante.CLIENTE, false, 0, 10L, 0L, null, null, null, null);
    }

}
