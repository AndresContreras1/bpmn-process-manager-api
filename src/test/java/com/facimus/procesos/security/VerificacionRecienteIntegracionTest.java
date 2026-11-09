package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Map;

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

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.common.security.Factor;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.service.EmpresaService;

import tools.jackson.databind.json.JsonMapper;

/**
 * PR 49: dar de baja la tienda pide haber confirmado la identidad hace menos de diez minutos. El token lleva cuando se
 * verifico por ultima vez; uno mas viejo responde 403 "Verificación requerida", y la reautenticacion con la clave lo
 * arregla sin cerrar la sesion.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class VerificacionRecienteIntegracionTest {

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
    private String nombre;

    @BeforeEach
    void registrarTienda() {
        int numero = ++tiendas;
        nombre = "Tienda que se verifica " + numero;
        admin = "admin" + numero + "@verificacion.test";
        empresaService.registrar(nombre, "9011" + numero + "000-1", admin, "Administradora", admin, CLAVE);
    }

    @Test
    @DisplayName("Recien entrado se puede dar de baja la tienda: el login es una verificacion")
    void recienEntrado_puedeDarDeBaja() throws Exception {
        baja(SesionEnCookies.acceso(login())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Con la ultima verificacion de hace once minutos responde 403, y la reautenticacion lo arregla")
    void verificacionVieja_pideReautenticarse() throws Exception {
        String vieja = conVerificacion(SesionEnCookies.acceso(login()), Instant.now().minus(11, ChronoUnit.MINUTES));

        baja(vieja).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Verificación requerida"))
                .andExpect(jsonPath("$.detail").value(containsString("/api/v1/auth/reautenticacion")));
        reautenticar(vieja, "no es la clave").andExpect(status().isUnauthorized());

        String nueva = SesionEnCookies.acceso(reautenticar(vieja, CLAVE).andExpect(status().isOk()));
        assertThat(jwtService.validarAcceso(nueva).orElseThrow().verificadoEn())
                .isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
        assertThat(jwtService.validar(nueva).orElseThrow().sesion())
                .isEqualTo(jwtService.validar(vieja).orElseThrow().sesion());
        baja(nueva).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Renovar la sesion no la verifica: el token nuevo lleva la ultima verificacion de la base")
    void renovar_conservaLaVerificacion() throws Exception {
        ResultActions login = login();
        String sesion = jwtService.validar(SesionEnCookies.acceso(login)).orElseThrow().sesion();
        // En UTC, como Hibernate guarda las fechas (hibernate.jdbc.time_zone)
        jdbc.update("update sesiones set verificada_en = ? where codigo = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(11), sesion);

        String renovada = SesionEnCookies.acceso(mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(SesionEnCookies.conRefresco(SesionEnCookies.refresco(login))))
                .andExpect(status().isOk()));

        baja(renovada).andExpect(status().isForbidden()).andExpect(jsonPath("$.title").value("Verificación requerida"));
        reautenticar(renovada, CLAVE).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select verificada_en from sesiones where codigo = ?", LocalDateTime.class,
                sesion)).isCloseTo(LocalDateTime.now(ZoneOffset.UTC), within(1, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("Sin sesion no hay a quien reautenticar: 401")
    void sinSesion_noSeReautentica() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reautenticacion").with(SesionEnCookies.conCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("password", CLAVE))))
                .andExpect(status().isUnauthorized());
    }

    /** Un token de la misma sesion y la misma persona, con la ultima verificacion en ese momento. */
    private String conVerificacion(String acceso, Instant verificadoEn) {
        ApiPrincipal principal = jwtService.validar(acceso).orElseThrow();
        return jwtService.generarToken(principal, EnumSet.of(Factor.PASSWORD), verificadoEn, Duration.ofMinutes(15));
    }

    private ResultActions login() throws Exception {
        return mockMvc.perform(SesionEnCookies.login().contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(admin, CLAVE))))
                .andExpect(status().isOk());
    }

    private ResultActions baja(String acceso) throws Exception {
        return mockMvc.perform(post("/api/v1/empresas/actual/baja").with(SesionEnCookies.conSesion(acceso))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("confirmacion", nombre))));
    }

    private ResultActions reautenticar(String acceso, String clave) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/reautenticacion").with(SesionEnCookies.conSesion(acceso))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("password", clave))));
    }
}
