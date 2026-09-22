package com.facimus.procesos.gestion.controller;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.dto.response.EmpresaResponse;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.service.EmpresaService;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(EmpresaController.class)
class EmpresaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmpresaService empresaService;

    @Test
    @DisplayName("POST /api/v1/empresas - registrar empresa exitoso (201)")
    void registrar_exitoso() throws Exception {
        EmpresaResponse empresa = new EmpresaResponse(1L, "Acme Corp", "900123456", "info@acme.com", LocalDate.now());

        given(empresaService.registrar(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .willReturn(empresa);

        mockMvc.perform(post("/api/v1/empresas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombreEmpresa": "Acme Corp",
                                  "nit": "900123456",
                                  "correoContacto": "info@acme.com",
                                  "nombreAdmin": "Admin",
                                  "emailAdmin": "admin@acme.com",
                                  "passwordAdmin": "secret123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/empresas/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Acme Corp"))
                .andExpect(jsonPath("$.nit").value("900123456"));
    }

    @Test
    @DisplayName("GET /api/v1/empresas/actual - la tienda del usuario del token (200)")
    void actual_laTiendaDelToken() throws Exception {
        given(empresaService.obtener(1L, 1L))
                .willReturn(new EmpresaResponse(1L, "Acme Corp", "900123456", "info@acme.com", LocalDate.now()));

        mockMvc.perform(get("/api/v1/empresas/actual").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Acme Corp"));
    }

    @Test
    @DisplayName("GET /api/v1/empresas/{id} - otra tienda no existe para quien pregunta (404)")
    void detalle_otraTienda() throws Exception {
        given(empresaService.obtener(1L, 2L)).willThrow(new RecursoNoEncontradoException("Empresa no encontrada."));

        mockMvc.perform(get("/api/v1/empresas/2").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Empresa no encontrada."));
    }

    @Test
    @DisplayName("GET /api/v1/empresas/actual - sin sesion retorna 401")
    void actual_sinSesion() throws Exception {
        mockMvc.perform(get("/api/v1/empresas/actual"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/empresas - validacion falla (400)")
    void registrar_validacion_falla() throws Exception {
        mockMvc.perform(post("/api/v1/empresas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombreEmpresa": "",
                                  "nit": "",
                                  "correoContacto": "no-es-email",
                                  "nombreAdmin": "",
                                  "emailAdmin": "invalido",
                                  "passwordAdmin": "12"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
