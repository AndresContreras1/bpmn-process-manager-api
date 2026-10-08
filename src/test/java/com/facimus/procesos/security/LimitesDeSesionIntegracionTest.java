package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.service.EmpresaService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 35 (NIST SP 800-63B-4, AAL2): cada tienda decide cuanto aguanta una sesion sin renovarse y cuanto dura desde el
 * login, y ni el refresh token ni el acceso pasan de ahi. El tiempo se mueve en la base, que es donde la sesion lo lee,
 * y en UTC, que es como Hibernate guarda las fechas (hibernate.jdbc.time_zone).
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class LimitesDeSesionIntegracionTest {

    private static final String CLAVE = "marea-violeta-del-sur";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private JdbcTemplate jdbc;

    private static int tiendas;

    private String admin;

    @BeforeEach
    void registrarTienda() {
        int numero = ++tiendas;
        admin = "admin" + numero + "@limites.test";
        empresaService.registrar("Tienda con limites " + numero, "9008" + numero + "000-1", admin, "Administradora",
                admin, CLAVE);
    }

    @Test
    @DisplayName("Una tienda nueva cierra la sesion tras una hora sin renovarse y a las 24 horas del login")
    void deFabrica_unaHoraYUnDia() throws Exception {
        ResultActions login = entrar();

        MockHttpServletResponse respuesta = login.andReturn().getResponse();
        assertThat(respuesta.getCookie(CookiesDeSesion.REFRESCO).getMaxAge()).isEqualTo(3600);
        assertThat(respuesta.getCookie(CookiesDeSesion.ACCESO).getMaxAge()).isEqualTo(900);
        configuracion(SesionEnCookies.acceso(login))
                .andExpect(jsonPath("$.inactividadSesionMinutos").value(60))
                .andExpect(jsonPath("$.duracionSesionHoras").value(24));
        assertThat(vidaGuardada(SesionEnCookies.refresco(login))).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("Con 30 minutos de inactividad, el refresh token y su cookie viven 30 minutos")
    void inactividadDeLaTienda_esLaVidaDelRefresco() throws Exception {
        editarLimites(SesionEnCookies.acceso(entrar()), 30, null).andExpect(status().isOk());

        ResultActions login = entrar();

        assertThat(login.andReturn().getResponse().getCookie(CookiesDeSesion.REFRESCO).getMaxAge()).isEqualTo(1800);
        assertThat(vidaGuardada(SesionEnCookies.refresco(login))).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("A las horas de la tienda desde el login hay que volver a entrar, aunque la sesion se haya renovado")
    void pasadaLaDuracion_noRenuevaYCierraLaSesion() throws Exception {
        ResultActions login = entrar();
        String sesion = sesionDe(SesionEnCookies.refresco(login));
        jdbc.update("update sesiones set fecha_inicio = ? where codigo = ?",
                ahora().minusHours(24).minusMinutes(1), sesion);

        renovar(SesionEnCookies.refresco(login)).andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("select fecha_cierre from sesiones where codigo = ?", LocalDateTime.class,
                sesion)).isNotNull();
        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(SesionEnCookies.acceso(login))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A diez minutos del fin, el refresh token, su cookie y el acceso duran diez minutos y no mas")
    void cercaDelFin_nadaPasaDelFin() throws Exception {
        ResultActions login = entrar();
        jdbc.update("update sesiones set fecha_inicio = ? where codigo = ?",
                ahora().minusHours(24).plusMinutes(10), sesionDe(SesionEnCookies.refresco(login)));

        ResultActions renovada = renovar(SesionEnCookies.refresco(login)).andExpect(status().isOk());

        MockHttpServletResponse respuesta = renovada.andReturn().getResponse();
        JsonNode cuerpo = jsonMapper.readTree(respuesta.getContentAsString());
        assertThat(cuerpo.get("expiresIn").asLong()).isBetween(590L, 600L);
        assertThat(respuesta.getCookie(CookiesDeSesion.ACCESO).getMaxAge()).isBetween(590, 600);
        assertThat(respuesta.getCookie(CookiesDeSesion.REFRESCO).getMaxAge()).isBetween(590, 600);
        assertThat(vidaGuardada(SesionEnCookies.refresco(renovada)))
                .isBetween(Duration.ofSeconds(590), Duration.ofSeconds(600));
    }

    @Test
    @DisplayName("Acortar la duracion alcanza a las sesiones abiertas en su proxima renovacion, y queda en el historial")
    void acortarLaDuracion_alcanzaALasAbiertas() throws Exception {
        ResultActions login = entrar();
        String acceso = SesionEnCookies.acceso(login);
        jdbc.update("update sesiones set fecha_inicio = ? where codigo = ?", ahora().minusHours(2),
                sesionDe(SesionEnCookies.refresco(login)));

        editarLimites(acceso, null, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inactividadSesionMinutos").value(60))
                .andExpect(jsonPath("$.duracionSesionHoras").value(1));

        renovar(SesionEnCookies.refresco(login)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from historial_cambios where descripcion_cambio = ?", Integer.class,
                "Las sesiones se cierran tras 60 minutos sin renovarse y una hora después del login.")).isOne();
    }

    @Test
    @DisplayName("Fuera de 30 a 60 minutos o de 1 a 24 horas responde 400 y no cambia nada")
    void fueraDeLosLimites_esUnaSolicitudInvalida() throws Exception {
        String acceso = SesionEnCookies.acceso(entrar());

        editarLimites(acceso, 29, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.inactividadSesionMinutos")
                        .value("Una sesión aguanta sin renovarse de 30 a 60 minutos."));
        editarLimites(acceso, 61, null).andExpect(status().isBadRequest());
        editarLimites(acceso, null, 0).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.duracionSesionHoras").value("Una sesión dura de 1 a 24 horas."));
        editarLimites(acceso, null, 25).andExpect(status().isBadRequest());
        configuracion(acceso)
                .andExpect(jsonPath("$.inactividadSesionMinutos").value(60))
                .andExpect(jsonPath("$.duracionSesionHoras").value(24));
    }

    @Test
    @DisplayName("Una edicion sin los limites, como la de la simulacion, deja los que habia")
    void sinLosLimites_quedanLosQueHabia() throws Exception {
        String acceso = SesionEnCookies.acceso(entrar());
        editarLimites(acceso, 45, 8).andExpect(status().isOk());

        editarLimites(acceso, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inactividadSesionMinutos").value(45))
                .andExpect(jsonPath("$.duracionSesionHoras").value(8));
    }

    private ResultActions entrar() throws Exception {
        return mockMvc.perform(SesionEnCookies.login().contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(admin, CLAVE))))
                .andExpect(status().isOk());
    }

    private ResultActions renovar(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").with(SesionEnCookies.conRefresco(refreshToken)));
    }

    private ResultActions configuracion(String acceso) throws Exception {
        return mockMvc.perform(get("/api/v1/empresas/actual/configuracion").with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isOk());
    }

    /** La politica de estructura y la version se mandan como estan; los limites nulos no viajan. */
    private ResultActions editarLimites(String acceso, Integer inactividad, Integer duracion) throws Exception {
        JsonNode actual = jsonMapper.readTree(configuracion(acceso).andReturn().getResponse().getContentAsString());
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("politicaEstructura", actual.get("politicaEstructura").asString());
        cuerpo.put("version", actual.get("version").asLong());
        if (inactividad != null) {
            cuerpo.put("inactividadSesionMinutos", inactividad);
        }
        if (duracion != null) {
            cuerpo.put("duracionSesionHoras", duracion);
        }
        return mockMvc.perform(put("/api/v1/empresas/actual/configuracion").with(SesionEnCookies.conSesion(acceso))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(cuerpo)));
    }

    /** Lo que la base le dio de vida al refresh token: de su emision a su vencimiento. */
    private Duration vidaGuardada(String refreshToken) {
        return jdbc.queryForObject("select fecha_emision, fecha_expiracion from refresh_tokens where token_hash = ?",
                (fila, numero) -> Duration.between(fila.getObject(1, LocalDateTime.class),
                        fila.getObject(2, LocalDateTime.class)).truncatedTo(ChronoUnit.SECONDS),
                Huella.de(refreshToken));
    }

    /** El momento de ahora como lo guarda Hibernate: en UTC. */
    private static LocalDateTime ahora() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private String sesionDe(String refreshToken) {
        return jdbc.queryForObject("select s.codigo from sesiones s join refresh_tokens t on t.sesion_id = s.id "
                + "where t.token_hash = ?", String.class, Huella.de(refreshToken));
    }
}
