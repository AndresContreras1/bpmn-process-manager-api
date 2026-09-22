package com.facimus.procesos.modelado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.UsuarioService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Auditoria de punta a punta: cada recurso editable dice quien lo creo y quien guardo el ultimo cambio, y cuando. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuditoriaIntegracionTest {

    private static final String ADMIN = "admin@auditoria.com";
    private static final String EDITORA = "editora@auditoria.com";
    private static final String CLAVE = "clave12345";

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

    private Long adminId;
    private Long editoraId;
    private String tokenAdmin;
    private String tokenEditora;

    @BeforeAll
    void registrarTienda() throws Exception {
        Long empresaId = empresaService.registrar("Tienda de auditoria", "900444555-6", "contacto@auditoria.com",
                "Administradora", ADMIN, CLAVE).id();
        adminId = usuarioRepository.findByEmail(ADMIN).orElseThrow().getId();
        editoraId = usuarioService.crearColaborador(empresaId, "Editora", EDITORA, CLAVE, RolAcceso.EDITOR).id();
        tokenAdmin = login(ADMIN);
        tokenEditora = login(EDITORA);
    }

    @Test
    @DisplayName("Crear anota al autor y la fecha, y editar anota a quien guardo el cambio sin tocar al autor")
    void crearYEditar_anotanQuienYCuando() throws Exception {
        JsonNode creado = json(conToken(post("/api/v1/procesos"), tokenAdmin, Map.of("nombre", "Returns",
                "descripcion", "Return to refund", "categoria", "After-sales")), 201);
        assertThat(creado.get("creadoPor").asLong()).isEqualTo(adminId);
        assertThat(creado.get("modificadoPor").asLong()).isEqualTo(adminId);
        LocalDateTime creadoEn = LocalDateTime.parse(creado.get("fechaCreacion").asString());
        assertThat(creado.get("fechaModificacion").asString()).isEqualTo(creado.get("fechaCreacion").asString());

        JsonNode editado = json(conToken(put("/api/v1/procesos/{id}", creado.get("id").asLong()), tokenEditora,
                Map.of("nombre", "Returns", "descripcion", "Return, inspection and refund", "categoria", "After-sales",
                        "version", creado.get("version").asLong())), 200);

        assertThat(editado.get("creadoPor").asLong()).isEqualTo(adminId);
        // La base guarda microsegundos: la fecha releida puede perder los decimales de mas
        assertThat(LocalDateTime.parse(editado.get("fechaCreacion").asString()))
                .isCloseTo(creadoEn, within(1, ChronoUnit.MILLIS));
        assertThat(editado.get("modificadoPor").asLong()).isEqualTo(editoraId);
        assertThat(LocalDateTime.parse(editado.get("fechaModificacion").asString())).isAfterOrEqualTo(creadoEn);
    }

    @Test
    @DisplayName("Lo que crea el sistema, como el administrador de una tienda nueva, no tiene autor")
    void loCreadoSinToken_notieneAutor() throws Exception {
        JsonNode administradora = json(conToken(get("/api/v1/usuarios/{id}", adminId), tokenAdmin, null), 200);
        JsonNode editora = json(conToken(get("/api/v1/usuarios/{id}", editoraId), tokenAdmin, null), 200);

        assertThat(administradora.get("creadoPor").isNull()).isTrue();
        assertThat(administradora.get("fechaCreacion").isNull()).isFalse();
        // La editora la creo un servicio sin token en este test; por la API la crearia la administradora
        assertThat(editora.get("creadoPor").isNull()).isTrue();

        JsonNode colaborador = json(conToken(post("/api/v1/usuarios"), tokenAdmin, Map.of("nombre", "Lector",
                "email", "lector@auditoria.com", "password", CLAVE, "rolAcceso", "SOLO_LECTURA")), 201);
        assertThat(colaborador.get("creadoPor").asLong()).isEqualTo(adminId);
    }

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token,
            Map<String, Object> cuerpo) throws Exception {
        peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return cuerpo == null ? peticion
                : peticion.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(cuerpo));
    }

    private JsonNode json(MockHttpServletRequestBuilder peticion, int estado) throws Exception {
        return jsonMapper.readTree(mockMvc.perform(peticion)
                .andExpect(status().is(estado))
                .andReturn().getResponse().getContentAsString());
    }

    private String login(String email) throws Exception {
        return json(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, CLAVE))), 200)
                .get("accessToken").asString();
    }
}
