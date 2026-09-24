package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
import org.springframework.test.web.servlet.RequestBuilder;

import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.UsuarioService;

import tools.jackson.databind.json.JsonMapper;

/**
 * La contrasena nunca sale de la API: ni en texto, ni su hash, ni como campo del contrato. Recorre las respuestas
 * que llevan datos de usuarios y tambien el documento de OpenAPI, que es donde un campo nuevo se colaria sin querer.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CredencialesFueraDeLasRespuestasTest {

    private static final String ADMIN = "admin@credenciales.com";
    private static final String COLABORADOR = "colaborador@credenciales.com";
    private static final String CLAVE = "clave-secreta-12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private String token;
    private Long colaboradorId;
    private String hashGuardado;

    @BeforeAll
    void registrarTiendaYEntrar() throws Exception {
        Long empresaId = empresaService.registrar("Tienda Credenciales", "900242526-7",
                "contacto@credenciales.com", "Administradora", ADMIN, CLAVE).id();
        colaboradorId = usuarioService.crearColaborador(empresaId, null, "Colaborador", COLABORADOR, CLAVE,
                RolAcceso.EDITOR).id();
        hashGuardado = usuarioRepository.findByEmail(ADMIN).orElseThrow().getPasswordHash();
        token = login();
    }

    @Test
    @DisplayName("Ninguna respuesta con datos de usuarios lleva la contrasena ni el hash guardado")
    void respuestasDeUsuarios_sinContrasenaNiHash() throws Exception {
        List<RequestBuilder> peticiones = List.of(
                get("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + token),
                get("/api/v1/usuarios/{id}", colaboradorId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token),
                patch("/api/v1/usuarios/{id}", colaboradorId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rolAcceso\":\"SOLO_LECTURA\",\"version\":0}"),
                get("/api/v1/empresas/actual").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        for (RequestBuilder peticion : peticiones) {
            String cuerpo = mockMvc.perform(peticion)
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(cuerpo)
                    .doesNotContain(CLAVE)
                    .doesNotContain(hashGuardado)
                    .doesNotContain("$2")
                    .doesNotContainIgnoringCase("password")
                    .doesNotContainIgnoringCase("contrasena")
                    .doesNotContainIgnoringCase("claveHash");
        }
    }

    @Test
    @DisplayName("El login y la creacion de un colaborador responden sin devolver la clave que recibieron")
    void loginYAltaDeColaborador_sinDevolverLaClave() throws Exception {
        String respuestaLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(ADMIN, CLAVE))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String respuestaAlta = mockMvc.perform(post("/api/v1/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nueva\",\"email\":\"nueva@credenciales.com\","
                                + "\"password\":\"" + CLAVE + "\",\"rolAcceso\":\"EDITOR\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        for (String cuerpo : List.of(respuestaLogin, respuestaAlta)) {
            assertThat(cuerpo)
                    .doesNotContain(CLAVE)
                    .doesNotContain("$2")
                    .doesNotContainIgnoringCase("password");
        }
    }

    @Test
    @DisplayName("La clave temporal sale solo en la respuesta que la genera, y nunca la guardada")
    void claveTemporal_soloEnLaRespuestaQueLaGenera() throws Exception {
        String alta = mockMvc.perform(post("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Temporal\",\"email\":\"temporal@credenciales.com\","
                                + "\"rolAcceso\":\"EDITOR\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String temporal = jsonMapper.readTree(alta).get("claveTemporal").asString();
        Long nuevoId = jsonMapper.readTree(alta).get("id").asLong();
        assertThat(temporal).isNotBlank();
        // Ni el hash de la clave temporal, ni la clave en ninguna lectura posterior.
        assertThat(alta).doesNotContain("$2");

        String leido = mockMvc.perform(get("/api/v1/usuarios/{id}", nuevoId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(leido).doesNotContain(temporal).doesNotContain("claveTemporal");
    }

    @Test
    @DisplayName("El contrato de OpenAPI no declara ninguna clave en la respuesta de usuario")
    void contratoDeOpenApi_sinClaveEnLaRespuestaDeUsuario() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.UsuarioResponse.properties.password").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.UsuarioResponse.properties.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.UsuarioResponse.properties.claveHash").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.LoginResponse.properties.password").doesNotExist());
    }

    private String login() throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(ADMIN, CLAVE))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(respuesta).get("accessToken").asString();
    }
}
