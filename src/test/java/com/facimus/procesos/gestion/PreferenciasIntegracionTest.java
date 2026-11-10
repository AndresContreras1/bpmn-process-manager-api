package com.facimus.procesos.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.security.SesionEnCookies;

import tools.jackson.databind.json.JsonMapper;

/**
 * F12: el idioma y la zona horaria. La organizacion empieza en espanol de Colombia y en la hora de Bogota; cada persona
 * puede elegir otros, y lo que no elige lo sigue tomando de su organizacion, tambien cuando esta cambia.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PreferenciasIntegracionTest {

    private static final String CLAVE = "marea-violeta-del-sur";
    private static final String PROPIAS = "/api/v1/auth/preferencias";
    private static final String DE_LA_ORGANIZACION = "/api/v1/empresas/actual/preferencias";
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
        admin = "admin" + numero + "@preferencias.test";
        empresaService.registrar("Tienda con preferencias " + numero, "9051" + numero + "000-1", admin,
                "Iris Idiomas", admin, CLAVE);
    }

    @Test
    @DisplayName("Una organizacion nueva trabaja en espanol de Colombia y en la hora de Bogota, y su gente con ella")
    void porDefecto() throws Exception {
        mockMvc.perform(get(PROPIAS).with(SesionEnCookies.conSesion(acceso(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idioma").value("es-CO"))
                .andExpect(jsonPath("$.zonaHoraria").value("America/Bogota"))
                .andExpect(jsonPath("$.idiomaPropio").doesNotExist())
                .andExpect(jsonPath("$.zonaHorariaPropia").doesNotExist());
    }

    @Test
    @DisplayName("Cada persona elige los suyos, y vacios vuelve a seguir los de su organizacion")
    void propias() throws Exception {
        String acceso = acceso(admin);
        cambiar(PROPIAS, acceso, "en-AU", "Australia/Sydney").andExpect(status().isOk())
                .andExpect(jsonPath("$.idioma").value("en-AU"))
                .andExpect(jsonPath("$.zonaHoraria").value("Australia/Sydney"))
                .andExpect(jsonPath("$.idiomaDeLaOrganizacion").value("es-CO"));

        cambiar(PROPIAS, acceso, "", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.idioma").value("es-CO"))
                .andExpect(jsonPath("$.zonaHoraria").value("America/Bogota"))
                .andExpect(jsonPath("$.idiomaPropio").doesNotExist());
    }

    @Test
    @DisplayName("Se rechazan un idioma que la web no tiene, una zona que no existe y un desplazamiento fijo")
    void invalidas() throws Exception {
        String acceso = acceso(admin);
        cambiar(PROPIAS, acceso, "de-DE", null).andExpect(status().isBadRequest());
        cambiar(PROPIAS, acceso, null, "Mars/Olympus_Mons").andExpect(status().isBadRequest());
        cambiar(PROPIAS, acceso, null, "+05:00").andExpect(status().isBadRequest());
        cambiar(DE_LA_ORGANIZACION, acceso, "fr-FR", null).andExpect(status().isBadRequest());
        cambiar(PROPIAS, acceso, null, "UTC").andExpect(status().isOk())
                .andExpect(jsonPath("$.zonaHoraria").value("UTC"));
    }

    @Test
    @DisplayName("El administrador cambia los de la organizacion: quien no eligio los suyos los toma, y queda en el "
            + "historial; un editor no puede cambiarlos")
    void deLaOrganizacion() throws Exception {
        String acceso = acceso(admin);
        String editora = "editora-" + UUID.randomUUID() + "@preferencias.test";
        Long editoraId = jsonMapper.readTree(mockMvc.perform(post("/api/v1/usuarios")
                        .with(SesionEnCookies.conSesion(acceso)).contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("nombre", "Eva Editora", "email", editora,
                                "password", CLAVE, "rolAcceso", "EDITOR"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asLong();
        jdbc.update("update usuarios set debe_cambiar_clave = false where id = ?", editoraId);
        String deEditora = acceso(editora);

        cambiar(DE_LA_ORGANIZACION, acceso, "fr-FR", "Europe/Paris").andExpect(status().isOk());
        mockMvc.perform(get(PROPIAS).with(SesionEnCookies.conSesion(deEditora)))
                .andExpect(jsonPath("$.idioma").value("fr-FR"))
                .andExpect(jsonPath("$.zonaHoraria").value("Europe/Paris"));
        cambiar(DE_LA_ORGANIZACION, deEditora, "en-US", "America/New_York").andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from historial_cambios h join usuarios u "
                + "on u.empresa_id = h.empresa_id where u.email = ? and h.descripcion_cambio = ?", Integer.class,
                admin, "Idioma fr-FR y zona horaria Europe/Paris de la organización.")).isEqualTo(1);
    }

    @Test
    @DisplayName("Registrar desde un navegador en ingles de Australia deja la organizacion en ese idioma y en la hora "
            + "de Sidney; la web puede mandar los suyos, y una zona que no existe no registra nada")
    void alRegistrar() throws Exception {
        int numero = ++tiendas;
        String deAustralia = "au" + numero + "@preferencias.test";
        mockMvc.perform(registrar(deAustralia, "9052" + numero + "000-1", null, null)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-AU,en;q=0.8"))
                .andExpect(status().isCreated());
        mockMvc.perform(get(PROPIAS).with(SesionEnCookies.conSesion(acceso(deAustralia))))
                .andExpect(jsonPath("$.idiomaDeLaOrganizacion").value("en-AU"))
                .andExpect(jsonPath("$.zonaHorariaDeLaOrganizacion").value("Australia/Sydney"));

        String deFrancia = "fr" + numero + "@preferencias.test";
        mockMvc.perform(registrar(deFrancia, "9053" + numero + "000-1", "fr-FR", "America/Montreal")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "es-CO"))
                .andExpect(status().isCreated());
        mockMvc.perform(get(PROPIAS).with(SesionEnCookies.conSesion(acceso(deFrancia))))
                .andExpect(jsonPath("$.idioma").value("fr-FR"))
                .andExpect(jsonPath("$.zonaHoraria").value("America/Montreal"));

        mockMvc.perform(registrar("marte" + numero + "@preferencias.test", "9054" + numero + "000-1", null,
                        "Mars/Olympus_Mons"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(registrar("aleman" + numero + "@preferencias.test", "9055" + numero + "000-1", "de-DE",
                        null))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from usuarios where email in (?, ?)", Integer.class,
                "marte" + numero + "@preferencias.test", "aleman" + numero + "@preferencias.test")).isZero();
    }

    private MockHttpServletRequestBuilder registrar(String email, String nit, String idioma, String zonaHoraria) {
        Map<String, Object> cuerpo = new HashMap<>(Map.of("nombreEmpresa", "Organizacion " + email, "nit", nit,
                "correoContacto", email, "nombreAdmin", "Iris Idiomas", "emailAdmin", email, "passwordAdmin", CLAVE));
        cuerpo.put("idioma", idioma);
        cuerpo.put("zonaHoraria", zonaHoraria);
        return post("/api/v1/empresas").contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(cuerpo));
    }

    private ResultActions cambiar(String ruta, String acceso, String idioma, String zonaHoraria) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("idioma", idioma);
        cuerpo.put("zonaHoraria", zonaHoraria);
        return mockMvc.perform(put(ruta).with(SesionEnCookies.conSesion(acceso))
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(cuerpo)));
    }

    private String acceso(String email) throws Exception {
        return SesionEnCookies.acceso(mockMvc.perform(SesionEnCookies.login().contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, CLAVE))))
                .andExpect(status().isOk()));
    }
}
