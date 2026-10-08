package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;

import tools.jackson.databind.json.JsonMapper;

/**
 * D17: un colaborador puede empezar con una contrasena temporal. Hasta que la cambie no puede hacer nada mas, y al
 * cambiarla se cierra todo lo que estuviera abierto con la anterior. Las que elige una persona pasan la politica de
 * NIST SP 800-63B-4, y el hash de una clave vieja se rehace al entrar.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContrasenasIntegracionTest {

    private static final String CLAVE = "marea-violeta-del-sur";
    private static final String ADMIN = "admin@contrasenas.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JdbcTemplate jdbc;

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
                        .with(SesionEnCookies.conSesion(tokenAdmin)))
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

        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Sin permisos"))
                .andExpect(jsonPath("$.detail").value("Debe cambiar su contraseña antes de seguir."));

        String despues = SesionEnCookies.acceso(mockMvc.perform(post("/api/v1/auth/password")
                        .with(SesionEnCookies.conSesion(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actual\":\"" + temporal + "\",\"nueva\":\"girasoles-en-el-campo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.debeCambiarClave").value(false))
                .andExpect(jsonPath("$.usuario.claveTemporal").doesNotExist()));

        // El token nuevo, en su cookie, ya no arrastra la marca, asi que el usuario trabaja.
        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(despues)))
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
                        .with(SesionEnCookies.conSesion(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actual\":\"" + temporal + "\",\"nueva\":\"otra-clave-larga\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(enOtroSitio)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(SesionEnCookies.login().contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest("doble@contrasenas.com", temporal))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("La contrasena actual equivocada, o repetir la que ya tenia, responde 400")
    void cambiarLaContrasena_conLaActualEquivocada_esUnaSolicitudInvalida() throws Exception {
        String respuesta = crearUsuario("Editor torpe", "torpe@contrasenas.com", RolAcceso.EDITOR);
        String temporal = jsonMapper.readTree(respuesta).get("claveTemporal").asString();
        String token = login("torpe@contrasenas.com", temporal);

        mockMvc.perform(post("/api/v1/auth/password").with(SesionEnCookies.conSesion(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actual\":\"la-que-no-es\",\"nueva\":\"una-clave-nueva\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La contraseña actual no coincide."));
        mockMvc.perform(post("/api/v1/auth/password").with(SesionEnCookies.conSesion(token))
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
                        .with(SesionEnCookies.conSesion(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarClave").value(true))
                .andReturn().getResponse().getContentAsString();

        String nueva = jsonMapper.readTree(reset).get("claveTemporal").asString();
        assertThat(nueva).isNotBlank().isNotEqualTo(temporal);
        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(token)))
                .andExpect(status().isUnauthorized());
        assertThat(login("olvido@contrasenas.com", nueva)).isNotBlank();
    }

    @Test
    @DisplayName("Una contrasena de mas de 72 caracteres no se acepta: es el tope de BCrypt")
    void contrasenaDemasiadoLarga_seRechaza() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios").with(SesionEnCookies.conSesion(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Larga\",\"email\":\"larga@contrasenas.com\",\"password\":\""
                                + "a".repeat(73) + "\",\"rolAcceso\":\"EDITOR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
        assertThat(usuarioRepository.findByEmail("larga@contrasenas.com")).isEmpty();
    }

    @Test
    @DisplayName("NIST 800-63B: una frase larga, sin mayusculas, numeros ni simbolos, sirve de contrasena")
    void unaFraseLarga_sirve() throws Exception {
        crearUsuarioConClave("Editora con frase", "frase@contrasenas.com", "girasoles en el campo")
                .andExpect(status().isCreated());

        assertThat(login("frase@contrasenas.com", "girasoles en el campo")).isNotBlank();
    }

    @Test
    @DisplayName("Menos de 15 caracteres no sirve, aunque lleven mayusculas, numeros y simbolos")
    void menosDe15Caracteres_noSirve() throws Exception {
        crearUsuarioConClave("Editor corto", "corto@contrasenas.com", "Abc123!xyz")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("La contraseña debe tener al menos 15 caracteres."));
        assertThat(usuarioRepository.findByEmail("corto@contrasenas.com")).isEmpty();
    }

    @Test
    @DisplayName("Una filtrada, una repetida, o una con el nombre de quien la elige o de su tienda, no sirven y lo "
            + "dicen")
    void laPolitica_diceQueFalla() throws Exception {
        crearUsuarioConClave("Editora filtrada", "filtrada@contrasenas.com", "Contraseña Segura")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("filtradas")));
        crearUsuarioConClave("Editor repetido", "repetido@contrasenas.com", "1212121212121212")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("repetido")));
        crearUsuarioConClave("Rodrigo Prueba", "rodrigo@contrasenas.com", "rodrigo y su perro fiel")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("tu nombre")));
        crearUsuarioConClave("Editora Marta", "marta.ruiz@correo.test", "las contrasenas del trabajo")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("tienda")));
        assertThat(usuarioRepository.findByEmail("rodrigo@contrasenas.com")).isEmpty();
        assertThat(usuarioRepository.findByEmail("marta.ruiz@correo.test")).isEmpty();
    }

    @Test
    @DisplayName("69 caracteres con eñes pasan de 72 bytes: el contrato los deja pasar y la politica no")
    void masDe72Bytes_noSirve() throws Exception {
        String clave = "el señor de la montaña ".repeat(3);

        crearUsuarioConClave("Editora larga", "bytes@contrasenas.com", clave)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("72 bytes")));
        assertThat(usuarioRepository.findByEmail("bytes@contrasenas.com")).isEmpty();
    }

    @Test
    @DisplayName("Cambiar la clave propia por una que no pasa la politica deja la de antes")
    void cambiarPorUnaDebil_dejaLaDeAntes() throws Exception {
        String respuesta = crearUsuario("Editor que repite", "repite@contrasenas.com", RolAcceso.EDITOR);
        String temporal = jsonMapper.readTree(respuesta).get("claveTemporal").asString();
        String token = login("repite@contrasenas.com", temporal);

        mockMvc.perform(post("/api/v1/auth/password").with(SesionEnCookies.conSesion(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("actual", temporal, "nueva", "abcdabcdabcdabcd"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("repetido")));
        assertThat(login("repite@contrasenas.com", temporal)).isNotBlank();
    }

    @Test
    @DisplayName("Registrar una tienda con una clave que lleva el nombre de la tienda no crea nada")
    void registrarConElNombreDeLaTienda_noCreaNada() throws Exception {
        mockMvc.perform(post("/api/v1/empresas").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("nombreEmpresa", "Panaderia La Espiga",
                                "nit", "900414243-9", "correoContacto", "hola@espiga.test", "nombreAdmin", "Rosa",
                                "emailAdmin", "rosa@espiga.test", "passwordAdmin", "el pan de la espiga dorada"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("tienda")));

        assertThat(usuarioRepository.findByEmail("rosa@espiga.test")).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from empresas where nit = ?", Integer.class, "900414243-9"))
                .isZero();
    }

    @Test
    @DisplayName("Cada hash nuevo lleva su algoritmo; uno de antes del prefijo sigue sirviendo, y el login lo rehace "
            + "sin tocar la version del usuario")
    void unHashDeAntes_elLoginLoRehace() throws Exception {
        String correo = "antes@contrasenas.com";
        String clave = "girasoles de otro campo";
        crearUsuarioConClave("Editor de antes", correo, clave).andExpect(status().isCreated());
        assertThat(hashDe(correo)).startsWith("{bcrypt}$2a$");
        String deAntes = new BCryptPasswordEncoder(4).encode(clave);
        jdbc.update("update usuarios set password_hash = ? where email = ?", deAntes, correo);
        long version = versionDe(correo);

        assertThat(login(correo, clave)).isNotBlank();

        String rehecho = hashDe(correo);
        assertThat(rehecho).startsWith("{bcrypt}$2a$").isNotEqualTo("{bcrypt}" + deAntes);
        assertThat(versionDe(correo)).isEqualTo(version);
        assertThat(login(correo, clave)).isNotBlank();
        assertThat(hashDe(correo)).as("un hash al dia no se vuelve a hacer").isEqualTo(rehecho);
    }

    private ResultActions crearUsuarioConClave(String nombre, String email, String clave) throws Exception {
        return mockMvc.perform(post("/api/v1/usuarios").with(SesionEnCookies.conSesion(tokenAdmin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("nombre", nombre, "email", email, "password", clave,
                        "rolAcceso", "EDITOR"))));
    }

    private String hashDe(String email) {
        return jdbc.queryForObject("select password_hash from usuarios where email = ?", String.class, email);
    }

    private long versionDe(String email) {
        return jdbc.queryForObject("select version from usuarios where email = ?", Long.class, email);
    }

    private String crearUsuario(String nombre, String email, RolAcceso rol) throws Exception {
        return mockMvc.perform(post("/api/v1/usuarios").with(SesionEnCookies.conSesion(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"" + nombre + "\",\"email\":\"" + email + "\",\"rolAcceso\":\""
                                + rol + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String login(String email, String password) throws Exception {
        return SesionEnCookies.acceso(mockMvc.perform(SesionEnCookies.login().contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk()));
    }
}
