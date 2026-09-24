package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;

import tools.jackson.databind.json.JsonMapper;

/**
 * D17: un colaborador puede empezar con una contrasena temporal. Hasta que la cambie no puede hacer nada mas, y al
 * cambiarla se cierra todo lo que estuviera abierto con la anterior.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContrasenasIntegracionTest {

    private static final String CLAVE = "clave12345";
    private static final String ADMIN = "admin@contrasenas.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Long empresaId;
    private String tokenAdmin;

    @BeforeAll
    void registrarTienda() throws Exception {
        empresaId = empresaService.registrar("Tienda de contrasenas", "900414243-4", "contacto@contrasenas.com",
                "Administradora", ADMIN, CLAVE).id();
        tokenAdmin = login(ADMIN, CLAVE);
    }

    @Test
    @DisplayName("Un usuario creado sin contrasena recibe una temporal, y solo la respuesta del alta la trae")
    void altaSinContrasena_devuelveLaTemporalUnaSolaVez() throws Exception {
        String respuesta = crearUsuario("Editora temporal", "temporal@contrasenas.com", RolAcceso.EDITOR);
        String clave = jsonMapper.readTree(respuesta).get("claveTemporal").asString();
        Long usuarioId = jsonMapper.readTree(respuesta).get("id").asLong();

        assertThat(clave).isNotBlank();
        assertThat(jsonMapper.readTree(respuesta).get("debeCambiarClave").asBoolean()).isTrue();
        mockMvc.perform(get("/api/v1/usuarios/{id}", usuarioId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarClave").value(true))
                .andExpect(jsonPath("$.claveTemporal").doesNotExist());
    }

    @Test
    @DisplayName("Con la clave temporal se entra, no se puede hacer nada mas, y al cambiarla se trabaja")
    void claveTemporal_soloDejaCambiarla() throws Exception {
        String respuesta = crearUsuario("Editor nuevo", "nuevo@contrasenas.com", RolAcceso.EDITOR);
        String temporal = jsonMapper.readTree(respuesta).get("claveTemporal").asString();
        String token = login("nuevo@contrasenas.com", temporal);

        mockMvc.perform(get("/api/v1/procesos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Sin permisos"))
                .andExpect(jsonPath("$.detail").value("Debe cambiar su contraseña antes de seguir."));

        String nuevos = mockMvc.perform(post("/api/v1/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actual\":\"" + temporal + "\",\"nueva\":\"mi-clave-nueva\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.debeCambiarClave").value(false))
                .andExpect(jsonPath("$.usuario.claveTemporal").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // El token nuevo ya no arrastra la marca, asi que el usuario trabaja.
        String despues = jsonMapper.readTree(nuevos).get("accessToken").asString();
        mockMvc.perform(get("/api/v1/procesos").header(HttpHeaders.AUTHORIZATION, "Bearer " + despues))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Cambiar la contrasena cierra las sesiones abiertas y la anterior deja de servir")
    void cambiarLaContrasena_cierraLoQueEstabaAbierto() throws Exception {
        String respuesta = crearUsuario("Editora doble", "doble@contrasenas.com", RolAcceso.EDITOR);
        String temporal = jsonMapper.readTree(respuesta).get("claveTemporal").asString();
        String enOtroSitio = login("doble@contrasenas.com", temporal);
        String token = login("doble@contrasenas.com", temporal);

        mockMvc.perform(post("/api/v1/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actual\":\"" + temporal + "\",\"nueva\":\"otra-clave-larga\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/procesos").header(HttpHeaders.AUTHORIZATION, "Bearer " + enOtroSitio))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest("doble@contrasenas.com", temporal))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("La contrasena actual equivocada, o repetir la que ya tenia, responde 400")
    void cambiarLaContrasena_conLaActualEquivocada_esUnaSolicitudInvalida() throws Exception {
        String respuesta = crearUsuario("Editor torpe", "torpe@contrasenas.com", RolAcceso.EDITOR);
        String temporal = jsonMapper.readTree(respuesta).get("claveTemporal").asString();
        String token = login("torpe@contrasenas.com", temporal);

        mockMvc.perform(post("/api/v1/auth/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actual\":\"la-que-no-es\",\"nueva\":\"una-clave-nueva\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La contraseña actual no coincide."));
        mockMvc.perform(post("/api/v1/auth/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actual\":\"" + temporal + "\",\"nueva\":\"" + temporal + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La contraseña nueva tiene que ser distinta de la actual."));
    }

    @Test
    @DisplayName("Restablecer la clave de un usuario le da otra temporal y lo echa de donde estuviera")
    void restablecer_dejaOtraTemporalYCierraLasSesiones() throws Exception {
        String respuesta = crearUsuario("Editora olvidadiza", "olvido@contrasenas.com", RolAcceso.EDITOR);
        Long usuarioId = jsonMapper.readTree(respuesta).get("id").asLong();
        String temporal = jsonMapper.readTree(respuesta).get("claveTemporal").asString();
        String token = login("olvido@contrasenas.com", temporal);

        String reset = mockMvc.perform(post("/api/v1/usuarios/{id}/restablecer-clave", usuarioId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarClave").value(true))
                .andReturn().getResponse().getContentAsString();

        String nueva = jsonMapper.readTree(reset).get("claveTemporal").asString();
        assertThat(nueva).isNotBlank().isNotEqualTo(temporal);
        mockMvc.perform(get("/api/v1/procesos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        assertThat(login("olvido@contrasenas.com", nueva)).isNotBlank();
    }

    @Test
    @DisplayName("Una contrasena de mas de 72 caracteres no se acepta: es el tope de BCrypt")
    void contrasenaDemasiadoLarga_seRechaza() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Larga\",\"email\":\"larga@contrasenas.com\",\"password\":\""
                                + "a".repeat(73) + "\",\"rolAcceso\":\"EDITOR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
        assertThat(usuarioRepository.findByEmail("larga@contrasenas.com")).isEmpty();
    }

    private String crearUsuario(String nombre, String email, RolAcceso rol) throws Exception {
        return mockMvc.perform(post("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"" + nombre + "\",\"email\":\"" + email + "\",\"rolAcceso\":\""
                                + rol + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String login(String email, String password) throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(respuesta).get("accessToken").asString();
    }
}
