package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.gestion.controller.dto.LoginRequest;
import com.facimus.procesos.gestion.controller.dto.ProcesoRequest;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.service.UsuarioService;

import tools.jackson.databind.json.JsonMapper;

/** Escenarios de seguridad con la aplicacion completa: SecurityConfig, filtro JWT y base de datos reales. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:seguridad-it;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class SeguridadIntegracionTest {

    private static final String ADMIN_DEMO = "admin@demo.com";
    private static final String CLAVE_DEMO = "admin123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Test
    @DisplayName("Sin token, un endpoint protegido responde 401 con ProblemDetail")
    void Seguridad_endpointProtegido_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/procesos"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.instance").value("/api/v1/procesos"));
    }

    @Test
    @DisplayName("Con un token invalido responde 401")
    void Seguridad_endpointProtegido_tokenInvalido_devuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/procesos").header(HttpHeaders.AUTHORIZATION, "Bearer no-es-un-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Con clave incorrecta el login responde 401")
    void Seguridad_login_claveIncorrecta_devuelve401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(ADMIN_DEMO, "clave-mala"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("El token del login real permite consultar los procesos de la empresa")
    void Seguridad_endpointProtegido_tokenDelLogin_devuelve200() throws Exception {
        String token = login(ADMIN_DEMO, CLAVE_DEMO);

        mockMvc.perform(get("/api/v1/procesos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un usuario de solo lectura no puede crear procesos: 403")
    void Seguridad_crearProceso_soloLectura_devuelve403() throws Exception {
        crearColaborador("lector@demo.com", "lector123", RolAcceso.SOLO_LECTURA);
        String token = login("lector@demo.com", "lector123");

        mockMvc.perform(post("/api/v1/procesos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new ProcesoRequest("Vacaciones", "Solicitud de vacaciones", "Talento humano"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El token de un usuario desactivado deja de servir: 401")
    void Seguridad_endpointProtegido_usuarioDesactivado_devuelve401() throws Exception {
        Usuario editor = crearColaborador("editor.baja@demo.com", "editor123", RolAcceso.EDITOR);
        String token = jwtService.generarToken(ApiPrincipal.of(editor));

        usuarioService.desactivar(editor.getEmpresa().getId(), editor.getId());

        mockMvc.perform(get("/api/v1/procesos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("El AccessDeniedHandler responde 403 con ProblemDetail")
    void Seguridad_accessDeniedHandler_respondeProblemDetail403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/procesos/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAccessDeniedHandler.handle(request, response, new AccessDeniedException("rol insuficiente"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("\"title\":\"Sin permisos\"", "\"status\":403");
    }

    private String login(String email, String password) throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(respuesta).get("accessToken").asString();
    }

    private Usuario crearColaborador(String email, String password, RolAcceso rol) throws Exception {
        Long empresaId = jwtService.validar(login(ADMIN_DEMO, CLAVE_DEMO)).orElseThrow()
                .get(JwtService.CLAIM_EMPRESA_ID, Long.class);
        return usuarioService.crearColaborador(empresaId, "Colaborador de prueba", email, password, rol);
    }
}
