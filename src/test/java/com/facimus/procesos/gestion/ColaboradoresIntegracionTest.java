package com.facimus.procesos.gestion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.UsuarioService;

import tools.jackson.databind.json.JsonMapper;

/**
 * El equipo de la tienda visto desde la pantalla que lo administra: buscar a alguien por su nombre, dar de baja a
 * quien ya no trabaja aqui y volver a darle de alta.
 *
 * <p>Una baja no borra a nadie: el colaborador sigue firmando el historial que firmo y sus tareas siguen contando.
 * Lo que hace es cerrarle la puerta -deja de poder entrar- y quitarlo de la vista de todos los dias. Por eso
 * reactivarlo tiene que ser posible, y por eso el listado tiene que poder ensenar lo desactivado cuando se lo piden.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ColaboradoresIntegracionTest {

    private static final String CLAVE = "clave12345";
    private static final String ADMIN = "admin@equipo.com";
    private static final String BRUNO = "bruno@equipo.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioService usuarioService;

    private String token;
    private Long brunoId;

    @BeforeAll
    void unaTiendaConTresPersonas() throws Exception {
        Long empresaId = empresaService.registrar("Tienda del equipo", "900181818-1", "contacto@equipo.com",
                "Ana", ADMIN, CLAVE).id();
        brunoId = usuarioService.crearColaborador(empresaId, null, "Bruno", BRUNO, CLAVE, RolAcceso.EDITOR).id();
        usuarioService.crearColaborador(empresaId, null, "Carlota", "carlota@equipo.com", CLAVE, RolAcceso.EDITOR);
        token = iniciarSesion(ADMIN);
    }

    @Test
    @DisplayName("HU-02: el listado busca por una parte del nombre, sin distinguir mayusculas")
    void listado_buscaPorNombre() throws Exception {
        pedir(get("/api/v1/usuarios"), null).andExpect(jsonPath("$.totalElements").value(3));

        // Una parte del nombre, y sin distinguir mayusculas: quien busca escribe lo que recuerda.
        pedir(get("/api/v1/usuarios").param("nombre", "BRU"), null)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value(BRUNO));
        // Ana y Carlota llevan una "a"; Bruno no. Un filtro tiene que dejar gente fuera o no esta filtrando.
        pedir(get("/api/v1/usuarios").param("nombre", "a"), null)
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(content().string(not(containsString(BRUNO))));
        pedir(get("/api/v1/usuarios").param("nombre", "nadie"), null)
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("Dar de baja a alguien y volver a darle de alta: sale del listado, deja de entrar y vuelve")
    void baja_yAlta() throws Exception {
        pedir(post("/api/v1/auth/login"), new LoginRequest(BRUNO, CLAVE)).andExpect(status().isOk());

        pedir(delete("/api/v1/usuarios/{id}", brunoId), null).andExpect(status().isNoContent());

        // Fuera del listado de todos los dias, dentro del que ensena lo desactivado, y sin poder entrar.
        pedir(get("/api/v1/usuarios"), null).andExpect(jsonPath("$.totalElements").value(2));
        String desactivados = pedir(get("/api/v1/usuarios").param("incluirInactivos", "true"), null)
                .andExpect(jsonPath("$.totalElements").value(3))
                .andReturn().getResponse().getContentAsString();
        pedir(post("/api/v1/auth/login"), new LoginRequest(BRUNO, CLAVE)).andExpect(status().isUnauthorized());

        pedir(patch("/api/v1/usuarios/{id}", brunoId), Map.of("activo", true, "version", versionDeBruno(desactivados)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));

        pedir(get("/api/v1/usuarios"), null).andExpect(jsonPath("$.totalElements").value(3));
        pedir(post("/api/v1/auth/login"), new LoginRequest(BRUNO, CLAVE)).andExpect(status().isOk());
        pedir(get("/api/v1/empresas/actual/historial").param("tamano", "50"), null)
                .andExpect(content().string(containsString("reactivado")));
    }

    private long versionDeBruno(String pagina) {
        for (var usuario : jsonMapper.readTree(pagina).get("content")) {
            if (BRUNO.equals(usuario.get("email").asString())) {
                return usuario.get("version").asLong();
            }
        }
        throw new AssertionError("Bruno no sale ni pidiendo los desactivados");
    }

    private String iniciarSesion(String email) throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, CLAVE))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(respuesta).get("accessToken").asString();
    }

    private ResultActions pedir(MockHttpServletRequestBuilder peticion, Object cuerpo) throws Exception {
        if (token != null) {
            peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(cuerpo));
        }
        return mockMvc.perform(peticion);
    }
}
