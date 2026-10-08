package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.facimus.procesos.common.api.IdDePeticionFilter;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.dto.request.ProcesoRequest;
import com.facimus.procesos.gestion.dto.request.RegistroEmpresaRequest;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.UsuarioService;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.json.JsonMapper;

/** Escenarios de seguridad con la aplicacion completa: SecurityConfig, filtro JWT y base de datos reales. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SeguridadIntegracionTest {

    private static final String ADMIN = "admin@seguridad.com";
    private static final String CLAVE = "marea-violeta-del-sur";
    private static final String BLOQUEADO = "bloqueo@seguridad.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long empresaId;
    private Long adminId;

    @BeforeAll
    void registrarTienda() {
        empresaId = empresaService.registrar("Tienda Seguridad", "900222333-4", "contacto@seguridad.com",
                "Administrador", ADMIN, CLAVE).id();
        adminId = usuarioService.buscarCredenciales(ADMIN).orElseThrow().usuario().id();
    }

    @Test
    @DisplayName("Sin token, un endpoint protegido responde 401 con ProblemDetail y WWW-Authenticate: Bearer")
    void Seguridad_endpointProtegido_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/procesos"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.instance").value("/api/v1/procesos"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/v1/lanes//actividades", "/api/v1/procesos;jsessionid=abc"})
    @DisplayName("Una URL que rechaza el firewall de Spring Security responde 400 con ProblemDetail")
    void Seguridad_urlRechazadaPorElFirewall_devuelve400ConProblemDetail(String url) throws Exception {
        // MockMvc normaliza "//" al armar la URI, asi que la URL cruda se fija directamente en la peticion
        mockMvc.perform(get("/").with(peticion -> {
                    peticion.setRequestURI(url);
                    return peticion;
                }))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Solicitud rechazada"));
    }

    @Test
    @DisplayName("Con un token invalido en la cookie de acceso responde 401")
    void Seguridad_endpointProtegido_tokenInvalido_devuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/procesos").cookie(new Cookie(CookiesDeSesion.ACCESO, "no-es-un-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("D29: un access token valido en Authorization: Bearer no abre nada; la sesion va en la cookie")
    void Seguridad_endpointProtegido_tokenEnBearer_devuelve401() throws Exception {
        String token = login(ADMIN, CLAVE);

        mockMvc.perform(get("/api/v1/procesos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("D29: lo que cambia algo con la cookie de sesion y sin el token CSRF responde 403 sin hacerlo")
    void Seguridad_cambioConCookieYSinCsrf_devuelve403() throws Exception {
        String token = login(ADMIN, CLAVE);

        mockMvc.perform(post("/api/v1/procesos")
                        .cookie(new Cookie(CookiesDeSesion.ACCESO, token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new ProcesoRequest("Sin token CSRF", "No tiene que crearse", "Ventas"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Sin token CSRF"));

        assertThat(jdbcTemplate.queryForObject("select count(*) from procesos where nombre = 'Sin token CSRF'",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("Con clave incorrecta el login responde 401 con WWW-Authenticate: Bearer")
    void Seguridad_login_claveIncorrecta_devuelve401() throws Exception {
        mockMvc.perform(SesionEnCookies.login()
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(ADMIN, "clave-mala"))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    @DisplayName("HU-03: un correo desconocido, un usuario desactivado y una clave mala reciben el mismo 401")
    void Seguridad_login_cualquierFallo_respondeLoMismo() throws Exception {
        UsuarioResponse baja = crearColaborador("baja.login@seguridad.com", "hojas-secas-de-otono", RolAcceso.EDITOR);
        usuarioService.desactivar(baja.empresaId(), adminId, baja.id());

        String claveMala = loginFallido(ADMIN, "clave-mala");

        assertThat(loginFallido("nadie@seguridad.com", CLAVE)).isEqualTo(claveMala);
        assertThat(loginFallido("baja.login@seguridad.com", "hojas-secas-de-otono")).isEqualTo(claveMala);
    }

    @ParameterizedTest(name = "emailAdmin = {0}")
    @CsvSource({ "admin@seguridad.com, 901000111", "ADMIN@Seguridad.com, 901000222" })
    @DisplayName("Registrar una empresa con el correo de otro usuario responde 409 y no le bloquea el login")
    void Seguridad_registroConCorreoAjeno_devuelve409YElDuenoSigueEntrando(String correoAjeno, String nit)
            throws Exception {
        mockMvc.perform(post("/api/v1/empresas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new RegistroEmpresaRequest("Otra Tienda", nit,
                                "contacto@otra.com", "Otro Admin", correoAjeno, "nubes-de-algodon-gris"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Regla de negocio violada"));

        login(ADMIN, CLAVE);
        // La empresa rechazada no quedo a medio crear: su NIT sigue libre.
        mockMvc.perform(post("/api/v1/empresas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new RegistroEmpresaRequest("Otra Tienda", nit,
                                "contacto@otra.com", "Otro Admin", "admin-" + nit + "@otra.com", "nubes-de-algodon-gris"))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Una contrasena de mas de 72 caracteres, que BCrypt no acepta, responde 400 y no 500")
    void Seguridad_contrasenaDeMasDe72Caracteres_devuelve400() throws Exception {
        String larga = "x".repeat(73);

        mockMvc.perform(post("/api/v1/empresas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new RegistroEmpresaRequest("Tienda Larga", "901000333",
                                "contacto@larga.com", "Admin Largo", "admin@larga.com", larga))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.passwordAdmin").value("La contrasena no puede superar 72 caracteres."));

        mockMvc.perform(SesionEnCookies.login()
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(ADMIN, larga))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("La contrasena no puede superar 72 caracteres."));
    }

    @Test
    @DisplayName("La cookie del login real permite consultar los procesos de la empresa, y el cuerpo no trae tokens")
    void Seguridad_endpointProtegido_tokenDelLogin_devuelve200() throws Exception {
        ResultActions entrada = mockMvc.perform(SesionEnCookies.login()
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(ADMIN, CLAVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
        String token = SesionEnCookies.acceso(entrada);

        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(token)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un usuario de solo lectura no puede crear procesos: 403")
    void Seguridad_crearProceso_soloLectura_devuelve403() throws Exception {
        crearColaborador("lector@seguridad.com", "silencio-en-la-biblioteca", RolAcceso.SOLO_LECTURA);
        String token = login("lector@seguridad.com", "silencio-en-la-biblioteca");

        mockMvc.perform(post("/api/v1/procesos")
                        .with(SesionEnCookies.conSesion(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new ProcesoRequest("Vacaciones", "Solicitud de vacaciones", "Talento humano"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Desactivar a un usuario cierra sus sesiones: su access token y su refresh token dejan de servir")
    void Seguridad_endpointProtegido_usuarioDesactivado_devuelve401() throws Exception {
        UsuarioResponse editor = crearColaborador("editor.baja@seguridad.com", "cometa-sobre-el-trigal", RolAcceso.EDITOR);
        Tokens sesion = sesion("editor.baja@seguridad.com", "cometa-sobre-el-trigal");

        usuarioService.desactivar(editor.empresaId(), adminId, editor.id());

        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(sesion.access())))
                .andExpect(status().isUnauthorized());
        renovar(sesion.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Cambiar el rol cierra las sesiones del usuario, que vuelve a entrar con el rol nuevo")
    void Seguridad_cambioDeRol_cierraSesionesYElNuevoLoginTraeElRolNuevo() throws Exception {
        UsuarioResponse editor = crearColaborador("editor.rol@seguridad.com", "cometa-sobre-el-trigal", RolAcceso.EDITOR);
        Tokens antes = sesion("editor.rol@seguridad.com", "cometa-sobre-el-trigal");

        usuarioService.actualizar(editor.empresaId(), adminId, editor.id(), null, RolAcceso.SOLO_LECTURA, null,
                editor.version());

        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(antes.access())))
                .andExpect(status().isUnauthorized());
        renovar(antes.refresh()).andExpect(status().isUnauthorized());
        Tokens despues = sesion("editor.rol@seguridad.com", "cometa-sobre-el-trigal");
        mockMvc.perform(post("/api/v1/procesos")
                        .with(SesionEnCookies.conSesion(despues.access()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new ProcesoRequest("Devoluciones", "Cambios y devoluciones", "Posventa"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Renovar entrega tokens nuevos de la misma sesion, y el refresh token nuevo vuelve a renovar")
    void Seguridad_refresh_rotaLosTokensDeLaMismaSesion() throws Exception {
        Tokens login = sesion(ADMIN, CLAVE);

        Tokens renovados = tokens(renovar(login.refresh())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.usuario.email").value(ADMIN)));

        assertThat(renovados.refresh()).isNotEqualTo(login.refresh());
        assertThat(sesionDe(renovados.access())).isEqualTo(sesionDe(login.access()));
        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(renovados.access())))
                .andExpect(status().isOk());
        renovar(renovados.refresh()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Reusar un refresh token ya usado cierra la sesion: ya no sirven el refresh token nuevo ni el access")
    void Seguridad_refreshReutilizado_cierraLaSesion() throws Exception {
        Tokens login = sesion(ADMIN, CLAVE);
        Tokens renovados = tokens(renovar(login.refresh()).andExpect(status().isOk()));

        renovar(login.refresh())
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.title").value("Sesión no válida"));

        renovar(renovados.refresh()).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(renovados.access())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Cerrar sesion invalida al instante sus tokens y no toca las otras sesiones del usuario")
    void Seguridad_logout_cierraSoloEsaSesion() throws Exception {
        Tokens celular = sesion(ADMIN, CLAVE);
        Tokens portatil = sesion(ADMIN, CLAVE);

        mockMvc.perform(post("/api/v1/auth/logout").with(SesionEnCookies.conSesion(celular.access())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(celular.access())))
                .andExpect(status().isUnauthorized());
        renovar(celular.refresh()).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(portatil.access())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Con el access token ya vencido, la cookie de refresco del logout todavia cierra su sesion")
    void Seguridad_logoutSoloConLaCookieDeRefresco_cierraSuSesion() throws Exception {
        Tokens sesion = sesion(ADMIN, CLAVE);

        mockMvc.perform(post("/api/v1/auth/logout").with(SesionEnCookies.conRefresco(sesion.refresh())))
                .andExpect(status().isNoContent());

        renovar(sesion.refresh()).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(sesion.access())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un refresh token vencido no renueva, y la base guarda solo su hash SHA-256")
    void Seguridad_refreshVencido_devuelve401() throws Exception {
        Tokens login = sesion(ADMIN, CLAVE);
        assertThat(filasConHash(login.refresh())).isZero();
        assertThat(filasConHash(sha256(login.refresh()))).isOne();

        jdbcTemplate.update("update refresh_tokens set fecha_expiracion = ? where token_hash = ?",
                LocalDateTime.now().minusMinutes(1), sha256(login.refresh()));

        renovar(login.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Defensa en profundidad: un usuario inactivo no renueva aunque su sesion haya quedado abierta")
    void Seguridad_refreshDeUsuarioInactivo_devuelve401() throws Exception {
        crearColaborador("editor.inactivo@seguridad.com", "cometa-sobre-el-trigal", RolAcceso.EDITOR);
        Tokens sesion = sesion("editor.inactivo@seguridad.com", "cometa-sobre-el-trigal");

        // Directo en la base, sin pasar por el service que cierra sus sesiones
        jdbcTemplate.update("update usuarios set activo = false where email = ?", "editor.inactivo@seguridad.com");

        renovar(sesion.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("El AccessDeniedHandler responde 403 con ProblemDetail")
    void Seguridad_accessDeniedHandler_respondeProblemDetail403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/procesos/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAccessDeniedHandler.handle(request, response, new AccessDeniedException("rol insuficiente"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("\"title\":\"Sin permisos\"", "\"status\":403");
    }

    @Test
    @DisplayName("El Location del registro lleva a la tienda creada, que su administrador puede consultar")
    void Seguridad_locationDelRegistro_llevaALaTiendaCreada() throws Exception {
        String location = mockMvc.perform(post("/api/v1/empresas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new RegistroEmpresaRequest("Tienda del Location",
                                "900333444-5", "contacto@location.com", "Administradora", "admin@location.com",
                                CLAVE))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        mockMvc.perform(get(location).with(SesionEnCookies.conSesion(login("admin@location.com", CLAVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nit").value("900333444-5"));
    }

    @Test
    @DisplayName("CORS deja que el frontend lea el Location, el Retry-After y el Idempotent-Replayed de las respuestas")
    void Seguridad_cors_exponeLasCabecerasDeLaApi() throws Exception {
        mockMvc.perform(get("/api/v1/empresas/actual")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .with(SesionEnCookies.conSesion(login(ADMIN, CLAVE))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        "Location, Retry-After, Idempotent-Replayed, X-Request-Id"));
    }

    @Test
    @DisplayName("5 fallos de un correo desde una IP dejan su login en 429 aun con la clave buena; desde otra IP no")
    void Seguridad_loginConDemasiadosFallos_devuelve429ConRetryAfter() throws Exception {
        crearColaborador(BLOQUEADO, "relojes-de-arena-fina", RolAcceso.EDITOR);
        for (String correo : new String[] {"bloqueo@seguridad.com", "BLOQUEO@seguridad.com", " Bloqueo@Seguridad.com",
                "bloqueo@seguridad.com", "bloqueo@SEGURIDAD.com"}) {
            loginFallido(correo, "clave-mala");
        }

        String retryAfter = mockMvc.perform(SesionEnCookies.login()
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(BLOQUEADO, "relojes-de-arena-fina"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Demasiados intentos"))
                .andExpect(jsonPath("$.detail")
                        .value("Demasiados intentos fallidos de inicio de sesión. Intenta de nuevo en 15 minutos."))
                .andReturn().getResponse().getHeader(HttpHeaders.RETRY_AFTER);

        // Los cinco fallos llegaron hace menos de un minuto: la espera es casi toda la ventana
        assertThat(Long.parseLong(retryAfter)).isBetween(840L, 900L);
        mockMvc.perform(SesionEnCookies.login()
                        .with(desdeLaIp("10.0.0.2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(BLOQUEADO, "relojes-de-arena-fina"))))
                .andExpect(status().isOk());
    }

    private String login(String email, String password) throws Exception {
        return sesion(email, password).access();
    }

    /** Los dos tokens de una sesion recien abierta con el login real. */
    private Tokens sesion(String email, String password) throws Exception {
        return tokens(mockMvc.perform(SesionEnCookies.login()
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk()));
    }

    private ResultActions renovar(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").with(SesionEnCookies.conRefresco(refreshToken)));
    }

    /** Los dos tokens que la respuesta dejo en sus cookies. */
    private Tokens tokens(ResultActions respuesta) {
        return new Tokens(SesionEnCookies.acceso(respuesta), SesionEnCookies.refresco(respuesta));
    }

    private String sesionDe(String accessToken) {
        return jwtService.validar(accessToken).orElseThrow().sesion();
    }

    private int filasConHash(String tokenHash) {
        return jdbcTemplate.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Integer.class,
                tokenHash);
    }

    private static String sha256(String texto) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
    }

    private static RequestPostProcessor desdeLaIp(String ip) {
        return peticion -> {
            peticion.setRemoteAddr(ip);
            return peticion;
        };
    }

    private record Tokens(String access, String refresh) {
    }

    /**
     * El cuerpo del 401, que no puede cambiar segun el motivo del fallo. Los intentos llevan el mismo id de peticion y
     * la misma traza: son lo unico que cambiaria entre dos respuestas, y los dos los pone quien llama. Con los mismos,
     * dos fallos tienen que responder exactamente lo mismo.
     */
    private String loginFallido(String email, String password) throws Exception {
        return mockMvc.perform(SesionEnCookies.login()
                        .header(IdDePeticionFilter.CABECERA, "intento-de-login")
                        .header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andReturn().getResponse().getContentAsString();
    }

    private UsuarioResponse crearColaborador(String email, String password, RolAcceso rol) {
        return usuarioService.crearColaborador(empresaId, null, "Colaborador de prueba", email, password, rol);
    }
}
