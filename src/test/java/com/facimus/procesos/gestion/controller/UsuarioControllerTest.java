package com.facimus.procesos.gestion.controller;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.service.UsuarioService;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(UsuarioController.class)
class UsuarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsuarioService usuarioService;

    @Test
    @DisplayName("GET /api/v1/usuarios - listar como admin (200)")
    void listar_como_admin() throws Exception {
        UsuarioResponse u = crearUsuario(1L, "Ana", "ana@acme.com", RolAcceso.EDITOR);
        given(usuarioService.listarPorEmpresa(1L)).willReturn(List.of(u));

        mockMvc.perform(get("/api/v1/usuarios").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Ana"));
    }

    @Test
    @DisplayName("GET /api/v1/usuarios - sin sesion retorna 401")
    void listar_sin_sesion() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/usuarios - solo lectura retorna 403")
    void listar_solo_lectura() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/usuarios - crear colaborador como admin (201)")
    void crear_colaborador() throws Exception {
        UsuarioResponse u = crearUsuario(2L, "Pedro", "pedro@acme.com", RolAcceso.EDITOR);
        given(usuarioService.crearColaborador(eq(1L), anyString(), anyString(), anyString(), any()))
                .willReturn(u);

        mockMvc.perform(post("/api/v1/usuarios")
                        .with(principal(RolAcceso.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Pedro",
                                  "email": "pedro@acme.com",
                                  "password": "secret123",
                                  "rolAcceso": "EDITOR"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/usuarios/2"))
                .andExpect(jsonPath("$.nombre").value("Pedro"));
    }

    @Test
    @DisplayName("POST /api/v1/usuarios - validacion falla (400)")
    void crear_validacion_falla() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios")
                        .with(principal(RolAcceso.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"","email":"invalido","password":"12","rolAcceso":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nombre").value("El nombre es obligatorio."))
                .andExpect(jsonPath("$.errors.email").value("El correo no es valido."))
                .andExpect(jsonPath("$.errors.password").value("La contrasena debe tener al menos 6 caracteres."))
                .andExpect(jsonPath("$.errors.rolAcceso").value("Debe seleccionar un rol de acceso."));
    }

    @Test
    @DisplayName("POST /api/v1/usuarios - si la base rechaza el correo duplicado responde 409 sin detalles de SQL")
    void crear_restriccionDeLaBase_devuelve409() throws Exception {
        given(usuarioService.crearColaborador(eq(1L), anyString(), anyString(), anyString(), any()))
                .willThrow(new DataIntegrityViolationException("Unique index violation: UK_USUARIOS_EMAIL"));

        mockMvc.perform(post("/api/v1/usuarios")
                        .with(principal(RolAcceso.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Pedro","email":"pedro@acme.com","password":"secret123","rolAcceso":"EDITOR"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflicto de datos"))
                .andExpect(jsonPath("$.detail").value(not(containsString("UK_USUARIOS_EMAIL"))));
    }

    @Test
    @DisplayName("GET /api/v1/usuarios/{id} - obtener usuario (200)")
    void obtener_usuario() throws Exception {
        UsuarioResponse u = crearUsuario(5L, "Laura", "laura@acme.com", RolAcceso.EDITOR);
        given(usuarioService.obtener(1L, 5L)).willReturn(u);

        mockMvc.perform(get("/api/v1/usuarios/5").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    @DisplayName("PATCH /api/v1/usuarios/{id} - cambiar rol (200)")
    void cambiar_rol() throws Exception {
        UsuarioResponse u = crearUsuario(5L, "Laura", "laura@acme.com", RolAcceso.ADMINISTRADOR);
        given(usuarioService.actualizar(1L, 5L, RolAcceso.ADMINISTRADOR, null)).willReturn(u);

        mockMvc.perform(patch("/api/v1/usuarios/5")
                        .with(principal(RolAcceso.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rolAcceso":"ADMINISTRADOR"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rolAcceso").value("ADMINISTRADOR"));
    }

    @Test
    void actualizar_estado_y_rechazar_patch_vacio() throws Exception {
        UsuarioResponse u = new UsuarioResponse(5L, "Laura", "laura@acme.com", RolAcceso.ADMINISTRADOR, false, 1L);
        given(usuarioService.actualizar(1L, 5L, RolAcceso.ADMINISTRADOR, false)).willReturn(u);

        mockMvc.perform(patch("/api/v1/usuarios/5").with(principal(RolAcceso.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rolAcceso\":\"ADMINISTRADOR\",\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        mockMvc.perform(patch("/api/v1/usuarios/5").with(principal(RolAcceso.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/v1/usuarios/{id} - desactivar usuario (204)")
    void desactivar_usuario() throws Exception {
        doNothing().when(usuarioService).desactivar(1L, 5L);

        mockMvc.perform(delete("/api/v1/usuarios/5").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isNoContent());
    }

    private UsuarioResponse crearUsuario(Long id, String nombre, String email, RolAcceso rol) {
        return new UsuarioResponse(id, nombre, email, rol, true, 1L);
    }

}
