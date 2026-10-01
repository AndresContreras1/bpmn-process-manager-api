package com.facimus.procesos.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.security.IdempotencyFilter;

import jakarta.servlet.RequestDispatcher;
import tools.jackson.databind.json.JsonMapper;

/**
 * Todo Problem Details lleva el id de la peticion, igual al de la cabecera X-Request-Id, lo arme un controller, un
 * filtro de seguridad o la pagina de error del servidor. Y un 500 no cuenta nada de lo que paso por dentro.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(ProblemasConIdIntegracionTest.ControllerQueFalla.class)
class ProblemasConIdIntegracionTest {

    private static final String ADMIN = "admin@problemas.com";
    private static final String EDITOR = "editor@problemas.com";
    private static final String TEMPORAL = "temporal@problemas.com";
    private static final String CLAVE = "clave12345";
    private static final String DETALLE_INTERNO = "detalle interno que no tiene que salir";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioService usuarioService;

    private String tokenAdmin;
    private String tokenEditor;
    private String tokenTemporal;

    /** Un endpoint que solo existe en esta prueba, para fallar como falla un error de programacion. */
    @RestController
    static class ControllerQueFalla {

        @GetMapping("/api/v1/prueba-de-errores/falla")
        String fallar() {
            throw new IllegalStateException(DETALLE_INTERNO);
        }
    }

    @BeforeAll
    void registrarTiendaYEntrar() throws Exception {
        Long empresaId = empresaService.registrar("Tienda de los problemas", "900313233-1", "contacto@problemas.com",
                "Administradora", ADMIN, CLAVE).id();
        usuarioService.crearColaborador(empresaId, null, "Editor", EDITOR, CLAVE, RolAcceso.EDITOR);
        String claveTemporal = usuarioService.crearColaborador(empresaId, null, "Temporal", TEMPORAL, null,
                RolAcceso.EDITOR).claveTemporal();
        tokenAdmin = login(ADMIN, CLAVE);
        tokenEditor = login(EDITOR, CLAVE);
        tokenTemporal = login(TEMPORAL, claveTemporal);
    }

    @Test
    @DisplayName("Una respuesta que sale bien tambien lleva su id en la cabecera")
    void respuestaCorrecta_llevaElIdEnLaCabecera() throws Exception {
        mockMvc.perform(get("/api/v1/procesos").header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(header().exists(IdDePeticionFilter.CABECERA));
    }

    @Test
    @DisplayName("401 sin token: el filtro de seguridad responde con el id de la peticion")
    void sinToken_401ConId() throws Exception {
        conElMismoId(mockMvc.perform(get("/api/v1/procesos")).andExpect(status().isUnauthorized()));
    }

    @Test
    @DisplayName("403 por rol: el manejador de acceso denegado responde con el id de la peticion")
    void rolQueNoAlcanza_403ConId() throws Exception {
        conElMismoId(mockMvc.perform(get("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, bearer(tokenEditor)))
                .andExpect(status().isForbidden()));
    }

    @Test
    @DisplayName("403 por clave temporal: el filtro que obliga a cambiarla responde con el id de la peticion")
    void claveTemporal_403ConId() throws Exception {
        conElMismoId(mockMvc.perform(get("/api/v1/procesos").header(HttpHeaders.AUTHORIZATION, bearer(tokenTemporal)))
                .andExpect(status().isForbidden()));
    }

    @Test
    @DisplayName("404 de un recurso que no existe: el manejador de la API responde con el id de la peticion")
    void recursoQueNoExiste_404ConId() throws Exception {
        conElMismoId(mockMvc.perform(get("/api/v1/procesos/987654")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin)))
                .andExpect(status().isNotFound()));
    }

    @Test
    @DisplayName("404 de una ruta que no existe: el Problem Details que arma Spring tambien lleva el id")
    void rutaQueNoExiste_404DeSpringConId() throws Exception {
        conElMismoId(mockMvc.perform(get("/api/v1/ruta-que-no-existe")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin)))
                .andExpect(status().isNotFound()));
    }

    @Test
    @DisplayName("400 de validacion: los errores por campo llegan con el id de la peticion")
    void validacion_400ConId() throws Exception {
        conElMismoId(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists()));
    }

    @Test
    @DisplayName("400 del firewall: la URL rechazada antes de llegar a la API tambien lleva el id")
    void firewall_400ConId() throws Exception {
        conElMismoId(mockMvc.perform(get("/api/v1/procesos;jsessionid=1")).andExpect(status().isBadRequest()));
    }

    @Test
    @DisplayName("422 de una Idempotency-Key reutilizada: el filtro de idempotencia responde con el id")
    void idempotencia_422ConId() throws Exception {
        crearProceso("clave-problemas", "Returns").andExpect(status().isCreated());

        conElMismoId(crearProceso("clave-problemas", "Gift cards").andExpect(status().isUnprocessableContent()));
    }

    @Test
    @DisplayName("500 inesperado en un controller: mensaje generico, nada de lo de dentro, y el id para buscarlo")
    void errorInesperado_500SinDetalleYConId() throws Exception {
        MvcResult resultado = conElMismoId(mockMvc.perform(get("/api/v1/prueba-de-errores/falla")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Error interno")));

        assertThat(resultado.getResponse().getContentAsString()).doesNotContain(DETALLE_INTERNO)
                .doesNotContain("IllegalStateException");
    }

    @Test
    @DisplayName("500 fuera de los controllers: la pagina de error responde en Problem Details, sin detalle y con id")
    void paginaDeError_500SinDetalleYConId() throws Exception {
        MvcResult resultado = conElMismoId(mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/procesos")
                        .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException(DETALLE_INTERNO)))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.title").value("Error interno"))
                .andExpect(jsonPath("$.instance").value("/api/v1/procesos")));

        assertThat(resultado.getResponse().getContentAsString()).doesNotContain(DETALLE_INTERNO);
    }

    @Test
    @DisplayName("La pagina de error conserva el codigo de un 4xx del servidor y sin error detras responde 404")
    void paginaDeError_4xxY404() throws Exception {
        conElMismoId(mockMvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Solicitud inválida")));
        conElMismoId(mockMvc.perform(get("/error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso no encontrado")));
    }

    @Test
    @DisplayName("El id que trae el cliente es el que vuelve en la cabecera y en el Problem Details")
    void idDelCliente_vuelveEnElProblema() throws Exception {
        mockMvc.perform(get("/api/v1/procesos").header(IdDePeticionFilter.CABECERA, "soporte-4815"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(IdDePeticionFilter.CABECERA, "soporte-4815"))
                .andExpect(jsonPath("$.requestId").value("soporte-4815"));
    }

    private static MvcResult conElMismoId(ResultActions respuesta) throws Exception {
        MvcResult resultado = respuesta.andReturn();
        String id = resultado.getResponse().getHeader(IdDePeticionFilter.CABECERA);
        assertThat(id).isNotBlank();
        respuesta.andExpect(jsonPath("$." + Problemas.ID_DE_PETICION).value(id));
        return resultado;
    }

    private ResultActions crearProceso(String clave, String nombre) throws Exception {
        return mockMvc.perform(post("/api/v1/procesos")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin))
                .header(IdempotencyFilter.CABECERA, clave)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"" + nombre + "\",\"descripcion\":\"Lo que cubre\",\"categoria\":\"Ops\"}"));
    }

    private String login(String email, String clave) throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, clave))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(respuesta).get("accessToken").asString();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
