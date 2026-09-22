package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.dto.response.ReservaIdempotencia;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.IdempotenciaService;
import com.facimus.procesos.gestion.service.UsuarioService;

import tools.jackson.databind.json.JsonMapper;

/** Idempotency-Key de punta a punta: reintentar un POST con la misma clave no crea el recurso dos veces. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdempotenciaIntegracionTest {

    private static final String ADMIN = "admin@idempotencia.com";
    private static final String EDITORA = "editora@idempotencia.com";
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

    @Autowired
    private IdempotenciaService idempotenciaService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long empresaId;
    private Long adminId;
    private String tokenAdmin;
    private String tokenEditora;

    @BeforeAll
    void registrarTienda() throws Exception {
        empresaId = empresaService.registrar("Tienda idempotente", "900999111-2", "contacto@idempotencia.com",
                "Administradora", ADMIN, CLAVE).id();
        adminId = usuarioRepository.findByEmail(ADMIN).orElseThrow().getId();
        usuarioService.crearColaborador(empresaId, "Editora", EDITORA, CLAVE, RolAcceso.EDITOR);
        tokenAdmin = login(ADMIN);
        tokenEditora = login(EDITORA);
    }

    @Test
    @DisplayName("Reintentar con la misma clave devuelve la respuesta de la primera vez y no crea otro proceso")
    void reintento_devuelveLaMismaRespuestaSinDuplicar() throws Exception {
        MockHttpServletResponse primera = crearProceso(tokenAdmin, "reintento-1", "Refunds")
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(IdempotencyFilter.REPETIDA))
                .andReturn().getResponse();

        MockHttpServletResponse segunda = crearProceso(tokenAdmin, "reintento-1", "Refunds")
                .andExpect(status().isCreated())
                .andExpect(header().string(IdempotencyFilter.REPETIDA, "true"))
                .andReturn().getResponse();

        assertThat(segunda.getContentAsString()).isEqualTo(primera.getContentAsString());
        assertThat(segunda.getHeader(HttpHeaders.LOCATION)).isEqualTo(primera.getHeader(HttpHeaders.LOCATION));
        assertThat(procesosLlamados("Refunds")).isOne();
    }

    @Test
    @DisplayName("La misma clave con otra peticion responde 422 y no crea nada")
    void mismaClaveConOtroCuerpo_devuelve422() throws Exception {
        crearProceso(tokenAdmin, "reutilizada", "Exchanges").andExpect(status().isCreated());

        crearProceso(tokenAdmin, "reutilizada", "Gift cards")
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.title").value("Idempotency-Key reutilizada"))
                .andExpect(jsonPath("$.detail").value("Esta Idempotency-Key ya se usó con otra petición."));
        assertThat(procesosLlamados("Gift cards")).isZero();
    }

    @Test
    @DisplayName("Una peticion que falla no gasta la clave: se puede corregir y reintentar con la misma")
    void peticionFallida_liberaLaClave() throws Exception {
        crearProceso(tokenAdmin, null, "Chargebacks").andExpect(status().isCreated());

        crearProceso(tokenAdmin, "tras-un-error", "Chargebacks").andExpect(status().isConflict());
        crearProceso(tokenAdmin, "tras-un-error", "Chargeback disputes").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Cada usuario tiene sus claves: la misma clave de otro usuario no devuelve su respuesta")
    void mismaClaveDeOtroUsuario_seEjecuta() throws Exception {
        crearProceso(tokenAdmin, "compartida", "Loyalty points").andExpect(status().isCreated());

        crearProceso(tokenEditora, "compartida", "Loyalty tiers")
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(IdempotencyFilter.REPETIDA));
        assertThat(procesosLlamados("Loyalty tiers")).isOne();
    }

    @Test
    @DisplayName("Una clave vacia o de mas de 100 caracteres responde 400")
    void claveInvalida_devuelve400() throws Exception {
        crearProceso(tokenAdmin, "x".repeat(101), "Too long").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Idempotency-Key inválida"));
        crearProceso(tokenAdmin, " ", "Blank").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Mientras la primera sigue en curso la misma peticion responde 409; si quedo abandonada, se retoma")
    void peticionEnCurso_devuelve409YUnaAbandonadaSeRetoma() throws Exception {
        String cuerpo = cuerpoDeProceso("Store credit");
        ReservaIdempotencia primera = idempotenciaService.reservar(empresaId, adminId, "en-curso",
                huella("POST", "/api/v1/procesos", cuerpo));
        assertThat(primera).isInstanceOf(ReservaIdempotencia.Nueva.class);

        crearProceso(tokenAdmin, "en-curso", "Store credit")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Petición en curso"));

        jdbcTemplate.update("update claves_idempotencia set fecha_creacion = ? where usuario_id = ? and clave = ?",
                LocalDateTime.now().minusMinutes(2), adminId, "en-curso");
        crearProceso(tokenAdmin, "en-curso", "Store credit").andExpect(status().isCreated());
        assertThat(procesosLlamados("Store credit")).isOne();
    }

    @Test
    @DisplayName("CORS deja mandar la clave desde el navegador y leer si la respuesta fue repetida")
    void cors_permiteLaClaveYExponeLaRepeticion() throws Exception {
        mockMvc.perform(options("/api/v1/procesos")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "authorization, content-type, idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsStringIgnoringCase("idempotency-key")));
        mockMvc.perform(get("/api/v1/procesos")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        "Location, Retry-After, Idempotent-Replayed"));
    }

    private ResultActions crearProceso(String token, String clave, String nombre) throws Exception {
        var peticion = post("/api/v1/procesos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoDeProceso(nombre));
        if (clave != null) {
            peticion.header(IdempotencyFilter.CABECERA, clave);
        }
        return mockMvc.perform(peticion);
    }

    private String cuerpoDeProceso(String nombre) throws Exception {
        return jsonMapper.writeValueAsString(Map.of("nombre", nombre, "descripcion", "Idempotency test",
                "categoria", "After-sales"));
    }

    private long procesosLlamados(String nombre) {
        return jdbcTemplate.queryForObject("select count(*) from procesos where empresa_id = ? and nombre = ?",
                Long.class, empresaId, nombre);
    }

    /** La misma huella que calcula IdempotencyFilter: metodo, ruta y cuerpo. */
    private static String huella(String metodo, String ruta, String cuerpo) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        sha.update((metodo + " " + ruta + "\n").getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(sha.digest(cuerpo.getBytes(StandardCharsets.UTF_8)));
    }

    private String login(String email) throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, CLAVE))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(respuesta).get("accessToken").asString();
    }
}
