package com.facimus.procesos.config;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.UsuarioService;

import tools.jackson.databind.json.JsonMapper;

/**
 * Lo que Actuator publica: el estado y la version para quien opera la API sin credenciales (un balanceador, el
 * health check del contenedor), las metricas solo para un administrador, y nada mas expuesto.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActuatorTest {

    private static final String ADMIN = "admin@actuator.com";
    private static final String EDITOR = "editor@actuator.com";
    private static final String CLAVE = "clave12345";

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

    @BeforeAll
    void registrarTiendaYEntrar() throws Exception {
        Long empresaId = empresaService.registrar("Tienda Actuator", "900272829-1", "contacto@actuator.com",
                "Administradora", ADMIN, CLAVE).id();
        usuarioService.crearColaborador(empresaId, null, "Editor", EDITOR, CLAVE, RolAcceso.EDITOR);
        tokenAdmin = login(ADMIN);
        tokenEditor = login(EDITOR);
    }

    @Test
    @DisplayName("El estado es publico y no cuenta que hay detras")
    void health_esPublicoYSinDetalles() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    @DisplayName("Las sondas de arranque y de disponibilidad responden, y son las que mira el contenedor")
    void health_sondasDeLivenessYReadiness() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("La version de la API tambien es publica")
    void info_esPublicoYLlevaLaVersion() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.name").value("BPMN Process Manager API"))
                .andExpect(jsonPath("$.app.version").isNotEmpty());
    }

    @Test
    @DisplayName("Las metricas piden un administrador: sin token 401, con rol editor 403")
    void metrics_pidenUnAdministrador() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));

        mockMvc.perform(get("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenEditor))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Sin permisos"));

        mockMvc.perform(get("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isNotEmpty());
    }

    @Test
    @DisplayName("Lo demas de Actuator no esta expuesto, ni siquiera para un administrador")
    void resto_deActuator_noEstaExpuesto() throws Exception {
        for (String endpoint : new String[] {"env", "beans", "configprops", "loggers", "heapdump", "threaddump"}) {
            mockMvc.perform(get("/actuator/" + endpoint)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                    .andExpect(status().isNotFound());
        }
    }

    private String login(String email) throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, CLAVE))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(respuesta).get("accessToken").asString();
    }
}
