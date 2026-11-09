package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.facimus.procesos.common.security.Base32;
import com.facimus.procesos.common.security.Totp;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.service.EmpresaService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 49: la tienda decide a quien le exige un segundo factor. Quien lo necesita y no lo tiene entra, pero solo puede
 * inscribirlo; al renovar la sesion despues de confirmarlo trabaja como siempre.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PoliticaSegundoFactorIntegracionTest {

    private static final String CLAVE = "marea-violeta-del-sur";
    private static int tiendas;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbc;

    private String admin;

    @BeforeEach
    void registrarTienda() {
        int numero = ++tiendas;
        admin = "admin" + numero + "@politica-mfa.test";
        empresaService.registrar("Tienda con politica " + numero, "9031" + numero + "000-1", admin, "Rosa Politica",
                admin, CLAVE);
    }

    @Test
    @DisplayName("Una tienda nueva deja el segundo factor a eleccion de cada persona")
    void porDefectoEsOpcional() throws Exception {
        String acceso = SesionEnCookies.acceso(login(admin));
        configuracion(acceso).andExpect(jsonPath("$.politicaSegundoFactor").value("OPCIONAL"));
        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(acceso))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Si la tienda lo exige, quien no lo tiene solo puede inscribirlo, y al renovar despues trabaja")
    void exigido_soloPuedeInscribirlo() throws Exception {
        politica(SesionEnCookies.acceso(login(admin)), "ADMINISTRADORES").andExpect(status().isOk())
                .andExpect(jsonPath("$.politicaSegundoFactor").value("ADMINISTRADORES"));

        ResultActions entrada = login(admin);
        String acceso = SesionEnCookies.acceso(entrada);
        assertThat(jwtService.validar(acceso).orElseThrow().debeInscribirSegundoFactor()).isTrue();
        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Segundo factor obligatorio"));
        configuracion(acceso).andExpect(status().isForbidden());

        JsonNode inscripcion = jsonMapper.readTree(mockMvc.perform(post("/api/v1/auth/segundo-factor/totp")
                        .with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        byte[] secreto = Base32.decodificar(inscripcion.get("secreto").asString());
        mockMvc.perform(post("/api/v1/auth/segundo-factor/totp/confirmacion").with(SesionEnCookies.conSesion(acceso))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("codigo",
                                Totp.codigo(secreto, Totp.paso(Instant.now()), Totp.DIGITOS)))))
                .andExpect(status().isOk());

        String renovada = SesionEnCookies.acceso(mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(SesionEnCookies.conRefresco(SesionEnCookies.refresco(entrada))))
                .andExpect(status().isOk()));
        assertThat(jwtService.validar(renovada).orElseThrow().debeInscribirSegundoFactor()).isFalse();
        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(renovada))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Exigido a los administradores, un editor trabaja sin el; exigido a todos, tampoco el editor")
    void segunElRol() throws Exception {
        String acceso = SesionEnCookies.acceso(login(admin));
        String editor = "editor-" + UUID.randomUUID() + "@politica-mfa.test";
        JsonNode creado = jsonMapper.readTree(mockMvc.perform(post("/api/v1/usuarios")
                        .with(SesionEnCookies.conSesion(acceso)).contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("nombre", "Eva Editora", "email", editor,
                                "password", CLAVE, "rolAcceso", "EDITOR"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        jdbc.update("update usuarios set debe_cambiar_clave = false where id = ?", creado.get("id").asLong());

        politica(acceso, "ADMINISTRADORES").andExpect(status().isOk());
        assertThat(jwtService.validar(SesionEnCookies.acceso(login(editor))).orElseThrow()
                .debeInscribirSegundoFactor()).isFalse();

        politica(acceso, "TODOS").andExpect(status().isOk());
        assertThat(jwtService.validar(SesionEnCookies.acceso(login(editor))).orElseThrow()
                .debeInscribirSegundoFactor()).isTrue();
    }

    @Test
    @DisplayName("Cambiar la politica queda en el historial, y una edicion que no la manda la deja como estaba")
    void historialYSinCambios() throws Exception {
        String acceso = SesionEnCookies.acceso(login(admin));
        politica(acceso, "TODOS").andExpect(status().isOk());

        JsonNode antes = jsonMapper.readTree(configuracion(acceso).andReturn().getResponse().getContentAsString());
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("politicaEstructura", antes.get("politicaEstructura").asString());
        cuerpo.put("version", antes.get("version").asLong());
        mockMvc.perform(put("/api/v1/empresas/actual/configuracion").with(SesionEnCookies.conSesion(acceso))
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.politicaSegundoFactor").value("TODOS"));

        assertThat(jdbc.queryForObject("select count(*) from historial_cambios h join usuarios u "
                + "on u.empresa_id = h.empresa_id where u.email = ? and h.descripcion_cambio = ?", Integer.class,
                admin, "Todos tienen que usar un segundo factor.")).isEqualTo(1);
    }

    /** Pone la politica de segundo factor con la version que tenga la configuracion. */
    private ResultActions politica(String acceso, String politica) throws Exception {
        JsonNode antes = jsonMapper.readTree(configuracion(acceso).andReturn().getResponse().getContentAsString());
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("politicaEstructura", antes.get("politicaEstructura").asString());
        cuerpo.put("politicaSegundoFactor", politica);
        cuerpo.put("version", antes.get("version").asLong());
        return mockMvc.perform(put("/api/v1/empresas/actual/configuracion").with(SesionEnCookies.conSesion(acceso))
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(cuerpo)));
    }

    private ResultActions configuracion(String acceso) throws Exception {
        return mockMvc.perform(get("/api/v1/empresas/actual/configuracion").with(SesionEnCookies.conSesion(acceso)));
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(SesionEnCookies.login().contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, CLAVE))))
                .andExpect(status().isOk());
    }
}
