package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.service.EmpresaService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * F14: cada persona ve sus sesiones abiertas y las cierra: una que dejo en otro equipo, o todas menos esta. Las de
 * otra persona no las ve ni las toca, aunque sea de su organizacion.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SesionesAbiertasIntegracionTest {

    private static final String CLAVE = "marea-violeta-del-sur";
    private static final String SESIONES = "/api/v1/auth/sesiones";
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
        admin = "admin" + numero + "@sesiones.test";
        empresaService.registrar("Tienda de sesiones " + numero, "9057" + numero + "000-1", admin, "Sol Sesiones",
                admin, CLAVE);
    }

    @Test
    @DisplayName("Dos equipos, dos sesiones; la de quien pregunta va marcada, y cerrar la otra la deja sin servir")
    void verYCerrarOtra() throws Exception {
        String portatil = entrar(admin);
        String telefono = entrar(admin);

        JsonNode lista = leer(mockMvc.perform(get(SESIONES).with(SesionEnCookies.conSesion(portatil)))
                .andExpect(status().isOk()));
        assertThat(lista).hasSize(2);
        JsonNode otra = null;
        for (JsonNode sesion : lista) {
            assertThat(sesion.get("factores").get(0).asString()).isEqualTo("PASSWORD");
            if (!sesion.get("actual").asBoolean()) {
                otra = sesion;
            }
        }
        assertThat(otra).as("solo una es la actual").isNotNull();

        mockMvc.perform(delete(SESIONES + "/" + otra.get("id").asLong()).with(SesionEnCookies.conSesion(portatil)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(telefono)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(SESIONES).with(SesionEnCookies.conSesion(portatil)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].actual").value(true));
    }

    @Test
    @DisplayName("Cerrar las demas deja solo la de quien pregunta")
    void cerrarLasDemas() throws Exception {
        String esta = entrar(admin);
        String otra = entrar(admin);
        String tercera = entrar(admin);

        mockMvc.perform(delete(SESIONES).with(SesionEnCookies.conSesion(esta))).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(otra)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(tercera)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(SESIONES).with(SesionEnCookies.conSesion(esta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("Las sesiones de otra persona de la organizacion ni se ven ni se cierran; un rol de solo lectura "
            + "tambien maneja las suyas")
    void lasDeOtraPersona() throws Exception {
        String acceso = entrar(admin);
        String lectora = "lectora-" + UUID.randomUUID() + "@sesiones.test";
        Long lectoraId = leer(mockMvc.perform(post("/api/v1/usuarios").with(SesionEnCookies.conSesion(acceso))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("nombre", "Lia Lectora", "email", lectora,
                                "password", CLAVE, "rolAcceso", "SOLO_LECTURA"))))
                .andExpect(status().isCreated())).get("id").asLong();
        jdbc.update("update usuarios set debe_cambiar_clave = false where id = ?", lectoraId);
        String deLectora = entrar(lectora);
        long sesionDeLectora = leer(mockMvc.perform(get(SESIONES).with(SesionEnCookies.conSesion(deLectora)))
                .andExpect(status().isOk())).get(0).get("id").asLong();

        mockMvc.perform(get(SESIONES).with(SesionEnCookies.conSesion(acceso)))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(delete(SESIONES + "/" + sesionDeLectora).with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(deLectora)))
                .andExpect(status().isOk());

        mockMvc.perform(delete(SESIONES).with(SesionEnCookies.conSesion(deLectora)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Cerrar la propia por su id es salir: se borran sus cookies")
    void cerrarLaPropia() throws Exception {
        String acceso = entrar(admin);
        long id = leer(mockMvc.perform(get(SESIONES).with(SesionEnCookies.conSesion(acceso)))).get(0).get("id")
                .asLong();

        mockMvc.perform(delete(SESIONES + "/" + id).with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getCookie(CookiesDeSesion.ACCESO).getMaxAge())
                        .isZero());
        mockMvc.perform(get(SESIONES).with(SesionEnCookies.conSesion(acceso))).andExpect(status().isUnauthorized());
    }

    private String entrar(String email) throws Exception {
        return SesionEnCookies.acceso(mockMvc.perform(SesionEnCookies.login().contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, CLAVE))))
                .andExpect(status().isOk()));
    }

    private JsonNode leer(ResultActions resultado) throws Exception {
        return jsonMapper.readTree(resultado.andReturn().getResponse().getContentAsString());
    }
}
