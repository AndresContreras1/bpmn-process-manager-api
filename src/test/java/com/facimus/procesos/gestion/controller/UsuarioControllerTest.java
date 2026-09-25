package com.facimus.procesos.gestion.controller;

import java.util.List;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.dto.response.RolDeUsuarioResponse;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.service.MembresiaRolService;
import com.facimus.procesos.gestion.service.UsuarioService;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @MockitoBean
    private MembresiaRolService membresiaRolService;

    @Test
    @DisplayName("GET /api/v1/usuarios - listar como admin: una pagina ordenada por nombre (200)")
    void listar_como_admin() throws Exception {
        UsuarioResponse u = crearUsuario(1L, "Ana", "ana@acme.com", RolAcceso.EDITOR);
        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        given(usuarioService.buscar(eq(1L), pagina.capture())).willReturn(new PageResponse<>(List.of(u), 0, 10, 1, 1));

        mockMvc.perform(get("/api/v1/usuarios").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nombre").value("Ana"));

        assertThat(pagina.getValue().getPageSize()).isEqualTo(10);
        assertThat(pagina.getValue().getSort()).containsExactly(Sort.Order.asc("nombre"), Sort.Order.asc("id"));
    }

    @Test
    @DisplayName("GET /api/v1/usuarios?tamano=51 - mas de 50 por pagina responde 400")
    void listar_tamanoDemasiadoGrande() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios").param("tamano", "51").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.tamano").value("El tamaño de página va de 1 a 50."));
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
        given(usuarioService.crearColaborador(eq(1L), eq(1L), anyString(), anyString(), anyString(), any()))
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
        given(usuarioService.crearColaborador(eq(1L), eq(1L), anyString(), anyString(), anyString(), any()))
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
        given(usuarioService.actualizar(1L, 1L, 5L, null, RolAcceso.ADMINISTRADOR, null, 3L)).willReturn(u);

        mockMvc.perform(patch("/api/v1/usuarios/5")
                        .with(principal(RolAcceso.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rolAcceso":"ADMINISTRADOR","version":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rolAcceso").value("ADMINISTRADOR"));
    }

    @Test
    void actualizar_estado_y_rechazar_patch_vacio() throws Exception {
        UsuarioResponse u = new UsuarioResponse(5L, "Laura", "laura@acme.com", RolAcceso.ADMINISTRADOR, false, 1L, 0L,
                null, null, null, null, false, null);
        given(usuarioService.actualizar(1L, 1L, 5L, null, RolAcceso.ADMINISTRADOR, false, 3L)).willReturn(u);

        mockMvc.perform(patch("/api/v1/usuarios/5").with(principal(RolAcceso.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rolAcceso\":\"ADMINISTRADOR\",\"activo\":false,\"version\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        mockMvc.perform(patch("/api/v1/usuarios/5").with(principal(RolAcceso.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /api/v1/usuarios/{id} - cambiar solo el nombre (200)")
    void actualizar_soloElNombre() throws Exception {
        UsuarioResponse u = new UsuarioResponse(5L, "Laura Mejia", "laura@acme.com", RolAcceso.EDITOR, true, 1L, 1L,
                null, null, null, null, false, null);
        given(usuarioService.actualizar(1L, 1L, 5L, "Laura Mejia", null, null, 3L)).willReturn(u);

        mockMvc.perform(patch("/api/v1/usuarios/5").with(principal(RolAcceso.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Laura Mejia\",\"version\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Laura Mejia"))
                .andExpect(jsonPath("$.rolAcceso").value("EDITOR"));
    }

    @Test
    @DisplayName("DELETE /api/v1/usuarios/{id} - desactivar usuario (204)")
    void desactivar_usuario() throws Exception {
        doNothing().when(usuarioService).desactivar(1L, 1L, 5L);

        mockMvc.perform(delete("/api/v1/usuarios/5").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isNoContent());
    }

    private UsuarioResponse crearUsuario(Long id, String nombre, String email, RolAcceso rol) {
        return new UsuarioResponse(id, nombre, email, rol, true, 1L, 0L, null, null, null, null, false, null);
    }

    @Test
    @DisplayName("GET /api/v1/usuarios/{id}/roles-proceso - los roles de proceso de una persona (200)")
    void rolesDeProceso_devuelveLosRoles() throws Exception {
        given(membresiaRolService.rolesDe(1L, 5L)).willReturn(List.of(
                new RolDeUsuarioResponse(2L, "Warehouse", "Picks, packs and ships the orders."),
                new RolDeUsuarioResponse(3L, "Sales", null)));

        mockMvc.perform(get("/api/v1/usuarios/5/roles-proceso").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Warehouse"))
                .andExpect(jsonPath("$[1].id").value(3));
    }

    @Test
    @DisplayName("PUT /api/v1/usuarios/{id}/roles-proceso - reemplaza la lista entera (200)")
    void reemplazarRolesDeProceso_devuelveLosQueQuedan() throws Exception {
        given(membresiaRolService.reemplazar(1L, 1L, 5L, List.of(2L, 3L)))
                .willReturn(List.of(new RolDeUsuarioResponse(2L, "Warehouse", null),
                        new RolDeUsuarioResponse(3L, "Sales", null)));

        mockMvc.perform(put("/api/v1/usuarios/5/roles-proceso")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rolesProcesoIds\":[2,3]}")
                        .with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        verify(membresiaRolService).reemplazar(1L, 1L, 5L, List.of(2L, 3L));
    }

    @Test
    @DisplayName("PUT /api/v1/usuarios/{id}/roles-proceso - sin la lista no se guarda nada (400)")
    void reemplazarRolesDeProceso_sinLista_esInvalida() throws Exception {
        mockMvc.perform(put("/api/v1/usuarios/5/roles-proceso")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rolesProcesoIds").exists());

        verifyNoInteractions(membresiaRolService);
    }

    @Test
    @DisplayName("PUT /api/v1/usuarios/{id}/roles-proceso - un rol de otra tienda no existe (404)")
    void reemplazarRolesDeProceso_conRolAjeno_noExiste() throws Exception {
        given(membresiaRolService.reemplazar(1L, 1L, 5L, List.of(99L)))
                .willThrow(new RecursoNoEncontradoException("Rol de proceso no encontrado."));

        mockMvc.perform(put("/api/v1/usuarios/5/roles-proceso")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rolesProcesoIds\":[99]}")
                        .with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Rol de proceso no encontrado."));
    }
}
