package com.facimus.procesos.gestion.controller;

import java.util.List;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.dto.response.RolProcesoVistaResponse;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.service.RolProcesoService;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
   import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(RolProcesoController.class)
class RolProcesoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RolProcesoService rolProcesoService;

    @Test
    @DisplayName("GET /api/v1/roles - listar roles: una pagina ordenada por nombre (200)")
    void listar_roles() throws Exception {
        given(rolProcesoService.buscar(eq(1L), isNull(), any(Pageable.class))).willReturn(
                new PageResponse<>(List.of(crearRol(1L, "Analista", "Analiza procesos", 3)), 0, 10, 1, 1));

        mockMvc.perform(get("/api/v1/roles").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nombre").value("Analista"))
                .andExpect(jsonPath("$.content[0].procesosQueLoUsan").value(3))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/roles?nombre=ware&orden=nombre,desc - busca por nombre (HU-20) y desempata por id")
    void listar_roles_buscandoPorNombre() throws Exception {
        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        given(rolProcesoService.buscar(eq(1L), eq("ware"), pagina.capture()))
                .willReturn(new PageResponse<>(List.of(), 1, 5, 0, 0));

        mockMvc.perform(get("/api/v1/roles").param("nombre", "ware").param("pagina", "1").param("tamano", "5")
                        .param("orden", "nombre,desc").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk());

        assertThat(pagina.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pagina.getValue().getPageSize()).isEqualTo(5);
        assertThat(pagina.getValue().getSort()).containsExactly(Sort.Order.desc("nombre"), Sort.Order.asc("id"));
    }

    @Test
    @DisplayName("GET /api/v1/roles?orden=descripcion - un campo fuera de la lista blanca responde 400")
    void listar_roles_ordenNoPermitido() throws Exception {
        mockMvc.perform(get("/api/v1/roles").param("orden", "descripcion").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.orden").value("Orden no permitido. Use nombre, con ,asc o ,desc."));
    }

    @Test
    @DisplayName("GET /api/v1/roles - sin sesion retorna 401")
    void listar_sin_sesion() throws Exception {
        mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/roles/{id} - detalle rol (200)")
    void detalle_rol() throws Exception {
        given(rolProcesoService.obtener(1L, 1L)).willReturn(crearRol(1L, "Analista", "Analiza procesos", 3));

        mockMvc.perform(get("/api/v1/roles/1").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Analista"))
                .andExpect(jsonPath("$.procesosQueLoUsan").value(3));
    }

    @Test
    @DisplayName("GET /api/v1/roles/{id} - sin sesion retorna 401")
    void detalle_sin_sesion() throws Exception {
        mockMvc.perform(get("/api/v1/roles/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/roles - crear rol como admin (201)")
    void crear_rol() throws Exception {
        RolProcesoVistaResponse rol = crearRol(2L, "Supervisor", "Supervisa", 0);
        given(rolProcesoService.crear(eq(1L), anyString(), anyString())).willReturn(rol);

        mockMvc.perform(post("/api/v1/roles")
                        .with(principal(RolAcceso.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Supervisor","descripcion":"Supervisa"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/roles/2"))
                .andExpect(jsonPath("$.nombre").value("Supervisor"));
    }

    @Test
    @DisplayName("POST /api/v1/roles - editor no puede crear (403)")
    void crear_como_editor() throws Exception {
        mockMvc.perform(post("/api/v1/roles")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"X","descripcion":"Y"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/v1/roles/{id} - editar rol (200)")
    void editar_rol() throws Exception {
        RolProcesoVistaResponse rol = crearRol(1L, "Analista Sr", "Senior", 0);
        given(rolProcesoService.editar(eq(1L), eq(1L), anyString(), anyString(), eq(3L))).willReturn(rol);

        mockMvc.perform(put("/api/v1/roles/1")
                        .with(principal(RolAcceso.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Analista Sr","descripcion":"Senior","version":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Analista Sr"));
    }

    @Test
    @DisplayName("DELETE /api/v1/roles/{id} - eliminar rol (204)")
    void eliminar_rol() throws Exception {
        doNothing().when(rolProcesoService).eliminar(1L, 1L);

        mockMvc.perform(delete("/api/v1/roles/1").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/v1/roles - validacion falla sin nombre (400)")
    void crear_validacion_falla() throws Exception {
        mockMvc.perform(post("/api/v1/roles")
                        .with(principal(RolAcceso.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"","descripcion":"algo"}
                                """))
                .andExpect(status().isBadRequest());
    }

    private RolProcesoVistaResponse crearRol(Long id, String nombre, String descripcion, long procesos) {
        return new RolProcesoVistaResponse(id, nombre, descripcion, procesos, procesos > 0, 0L, null, null, null, null);
    }

}
