package com.facimus.procesos.gestion.controller;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.dto.RolProcesoVista;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    @DisplayName("GET /api/v1/roles - listar roles (200)")
    void listar_roles() throws Exception {
        RolProceso rol = crearRol(1L, "Analista", "Analiza procesos");
        RolProcesoVista vista = new RolProcesoVista(rol, 3, true);
        given(rolProcesoService.listarConUso(1L)).willReturn(List.of(vista));

        mockMvc.perform(get("/api/v1/roles").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Analista"))
                .andExpect(jsonPath("$[0].procesosQueLoUsan").value(3));
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
        RolProceso rol = crearRol(1L, "Analista", "Analiza procesos");
        given(rolProcesoService.obtener(1L, 1L)).willReturn(rol);
        given(rolProcesoService.contarUsos(1L, 1L)).willReturn(3L);

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
        RolProceso rol = crearRol(2L, "Supervisor", "Supervisa");
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
        RolProceso rol = crearRol(1L, "Analista Sr", "Senior");
        given(rolProcesoService.editar(eq(1L), eq(1L), anyString(), anyString())).willReturn(rol);

        mockMvc.perform(put("/api/v1/roles/1")
                        .with(principal(RolAcceso.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Analista Sr","descripcion":"Senior"}
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

    private RolProceso crearRol(Long id, String nombre, String descripcion) {
        RolProceso rol = new RolProceso();
        rol.setId(id);
        rol.setNombre(nombre);
        rol.setDescripcion(descripcion);
        rol.setActivo(true);
        return rol;
    }

}
