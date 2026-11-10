package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

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

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.service.EmpresaService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * F13: la web ya no recuerda el perfil en el navegador; lo pide al arrancar, junto con lo que le queda a la sesion.
 * Pedirlo no renueva nada, asi que mirar no mantiene viva una sesion abandonada.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SesionActualIntegracionTest {

    private static final String CLAVE = "marea-violeta-del-sur";
    private static final String SESION = "/api/v1/auth/sesion";
    private static int tiendas;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private JdbcTemplate jdbc;

    private String admin;

    @BeforeEach
    void registrarTienda() {
        int numero = ++tiendas;
        admin = "admin" + numero + "@sesion-actual.test";
        empresaService.registrar("Tienda de la sesion " + numero, "9056" + numero + "000-1", admin, "Ada Actual",
                admin, CLAVE);
    }

    @Test
    @DisplayName("El login dice cuanto aguanta la sesion sin usarse y cuanto le queda; la sesion actual, lo mismo y "
            + "de quien es")
    void loginYSesionActual() throws Exception {
        ResultActions login = entrar().andExpect(jsonPath("$.inactividadSegundos").value(3600))
                .andExpect(jsonPath("$.restanteSegundos").value(86400));

        JsonNode actual = leer(mockMvc.perform(get(SESION).with(SesionEnCookies.conSesion(SesionEnCookies.acceso(login))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.email").value(admin))
                .andExpect(jsonPath("$.usuario.rolAcceso").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.segundoFactorPendiente").value(false)));
        assertThat(actual.get("inactividadSegundos").asLong()).isBetween(3590L, 3600L);
        assertThat(actual.get("restanteSegundos").asLong()).isBetween(86390L, 86400L);
    }

    @Test
    @DisplayName("Lo que le queda por inactividad cuenta desde la ultima renovacion, y pedir la sesion no la renueva")
    void inactividad_desdeLaUltimaRenovacion() throws Exception {
        ResultActions login = entrar();
        String refresco = SesionEnCookies.refresco(login);
        // La sesion lleva 40 minutos sin renovarse
        jdbc.update("update refresh_tokens set fecha_expiracion = ? where token_hash = ?",
                LocalDateTime.now(ZoneOffset.UTC).plusMinutes(20), Huella.de(refresco));

        JsonNode actual = leer(mockMvc.perform(get(SESION).with(SesionEnCookies.conSesion(SesionEnCookies.acceso(login))))
                .andExpect(status().isOk()));
        assertThat(actual.get("inactividadSegundos").asLong()).isBetween(1190L, 1200L);
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ? and fecha_uso is null",
                Integer.class, Huella.de(refresco))).as("preguntar no gasta el refresh token").isEqualTo(1);

        ResultActions renovada = mockMvc.perform(post("/api/v1/auth/refresh").with(SesionEnCookies.conRefresco(refresco)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inactividadSegundos").value(3600));
        JsonNode despues = leer(mockMvc.perform(get(SESION)
                        .with(SesionEnCookies.conSesion(SesionEnCookies.acceso(renovada))))
                .andExpect(status().isOk()));
        assertThat(despues.get("inactividadSegundos").asLong()).isBetween(3590L, 3600L);
    }

    @Test
    @DisplayName("Sin cookies, o con la sesion cerrada, no hay sesion actual")
    void sinSesion() throws Exception {
        mockMvc.perform(get(SESION)).andExpect(status().isUnauthorized());

        String acceso = SesionEnCookies.acceso(entrar());
        mockMvc.perform(post("/api/v1/auth/logout").with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(SESION).with(SesionEnCookies.conSesion(acceso))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Quien entro con una clave temporal tambien la ve: la web necesita saber que tiene que cambiarla")
    void conClaveTemporal() throws Exception {
        jdbc.update("update usuarios set debe_cambiar_clave = true where email = ?", admin);

        mockMvc.perform(get(SESION).with(SesionEnCookies.conSesion(SesionEnCookies.acceso(entrar()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.debeCambiarClave").value(true));
    }

    private ResultActions entrar() throws Exception {
        return mockMvc.perform(SesionEnCookies.login().contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(admin, CLAVE))))
                .andExpect(status().isOk());
    }

    private JsonNode leer(ResultActions resultado) throws Exception {
        return jsonMapper.readTree(resultado.andReturn().getResponse().getContentAsString());
    }
}
