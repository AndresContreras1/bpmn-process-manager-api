package com.facimus.procesos.security.controller;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.SESION;
import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static com.facimus.procesos.security.SesionEnCookies.conCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.common.DemasiadosIntentosException;
import com.facimus.procesos.common.SesionInvalidaException;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.dto.response.SesionIniciada;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.service.SesionService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.security.CookiesDeSesion;
import com.facimus.procesos.security.JwtService;
import com.facimus.procesos.security.LoginAuthenticator;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(AuthController.class)
@Import(CookiesDeSesion.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private UsuarioService usuarioService;

    @MockitoBean
    private LoginAuthenticator loginAuthenticator;

    @MockitoBean
    private SesionService sesionService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    @DisplayName("POST /api/v1/auth/login - credenciales validas abren la sesion en dos cookies HttpOnly, y el cuerpo "
            + "no lleva tokens (200)")
    void AuthController_login_credencialesValidas_abreLaSesionEnCookies() throws Exception {
        UsuarioResponse usuario = usuario();
        given(loginAuthenticator.autenticar("juan@acme.com", "harbor lights at dusk", "127.0.0.1")).willReturn(usuario);
        given(sesionService.iniciar(1L, 10L)).willReturn(new SesionIniciada("refresh-de-prueba", "sesion-1", usuario));
        given(jwtService.generarToken(any(ApiPrincipal.class))).willReturn("token-de-prueba");
        given(jwtService.getExpirationSeconds()).willReturn(900L);

        MockHttpServletResponse respuesta = mockMvc.perform(post("/api/v1/auth/login").with(conCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest("juan@acme.com", "harbor lights at dusk"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.usuario.email").value("juan@acme.com"))
                .andExpect(jsonPath("$.usuario.rolAcceso").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn().getResponse();

        assertThat(respuesta.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(cookie -> assertThat(cookie).matches(comoPatron("__Host-acceso=token-de-prueba; Path=/; "
                        + "Max-Age=900; Expires=*; Secure; HttpOnly; SameSite=Lax")))
                .anySatisfy(cookie -> assertThat(cookie).matches(comoPatron("__Secure-refresco=refresh-de-prueba; "
                        + "Path=/api/v1/auth; Max-Age=604800; Expires=*; Secure; HttpOnly; SameSite=Lax")));
        then(jwtService).should()
                .generarToken(new ApiPrincipal(10L, 1L, RolAcceso.ADMINISTRADOR, "juan@acme.com", "sesion-1", false));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - sin el token CSRF responde 403 sin mirar las credenciales")
    void AuthController_login_sinTokenCsrf_devuelve403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest("juan@acme.com", "harbor lights at dusk"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Sin token CSRF"));

        then(loginAuthenticator).shouldHaveNoInteractions();
        then(sesionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - credenciales invalidas devuelven 401 generico sin abrir sesion")
    void AuthController_login_credencialesInvalidas_devuelve401() throws Exception {
        given(loginAuthenticator.autenticar("juan@acme.com", "mala", "127.0.0.1"))
                .willThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/v1/auth/login").with(conCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest("juan@acme.com", "mala"))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas o token ausente"));

        then(sesionService).shouldHaveNoInteractions();
        then(jwtService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - un correo bloqueado por fallos devuelve 429 con Retry-After")
    void AuthController_login_bloqueadoPorFallos_devuelve429() throws Exception {
        given(loginAuthenticator.autenticar("juan@acme.com", "harbor lights at dusk", "127.0.0.1"))
                .willThrow(new DemasiadosIntentosException(Duration.ofSeconds(90)));

        mockMvc.perform(post("/api/v1/auth/login").with(conCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest("juan@acme.com", "harbor lights at dusk"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "90"))
                .andExpect(jsonPath("$.title").value("Demasiados intentos"))
                .andExpect(jsonPath("$.detail")
                        .value("Demasiados intentos fallidos de inicio de sesión. Intenta de nuevo en 2 minutos."));

        then(sesionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - campos vacios devuelven 400")
    void AuthController_login_camposVacios_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").with(conCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest("", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh - la cookie de refresco vigente se cambia por cookies nuevas (200)")
    void AuthController_refresh_cookieVigente_devuelveCookiesNuevas() throws Exception {
        UsuarioResponse usuario = usuario();
        given(sesionService.renovar("refresh-viejo"))
                .willReturn(new SesionIniciada("refresh-nuevo", "sesion-1", usuario));
        given(jwtService.generarToken(any(ApiPrincipal.class))).willReturn("token-nuevo");
        given(jwtService.getExpirationSeconds()).willReturn(900L);

        MockHttpServletResponse respuesta = mockMvc.perform(post("/api/v1/auth/refresh").with(conCsrf())
                        .cookie(new Cookie(CookiesDeSesion.REFRESCO, "refresh-viejo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.usuario.id").value(10))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn().getResponse();

        assertThat(respuesta.getCookie(CookiesDeSesion.ACCESO).getValue()).isEqualTo("token-nuevo");
        assertThat(respuesta.getCookie(CookiesDeSesion.REFRESCO).getValue()).isEqualTo("refresh-nuevo");
        then(jwtService).should()
                .generarToken(new ApiPrincipal(10L, 1L, RolAcceso.ADMINISTRADOR, "juan@acme.com", "sesion-1", false));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh - un refresh token que no sirve devuelve 401 y borra las cookies")
    void AuthController_refresh_sesionInvalida_devuelve401YBorraLasCookies() throws Exception {
        given(sesionService.renovar("refresh-usado")).willThrow(new SesionInvalidaException());

        MockHttpServletResponse respuesta = mockMvc.perform(post("/api/v1/auth/refresh").with(conCsrf())
                        .cookie(new Cookie(CookiesDeSesion.REFRESCO, "refresh-usado")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.title").value("Sesión no válida"))
                .andExpect(jsonPath("$.detail").value("La sesión expiró o se cerró. Inicia sesión de nuevo."))
                .andReturn().getResponse();

        assertThat(borradas(respuesta)).containsExactlyInAnyOrder(CookiesDeSesion.ACCESO, CookiesDeSesion.REFRESCO);
        then(jwtService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh - sin la cookie de refresco devuelve 401 sin tocar las sesiones")
    void AuthController_refresh_sinCookie_devuelve401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").with(conCsrf()))
                .andExpect(status().isUnauthorized());

        then(sesionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh - con la cookie pero sin el token CSRF responde 403 y no renueva nada")
    void AuthController_refresh_sinTokenCsrf_devuelve403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(CookiesDeSesion.REFRESCO, "refresh-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Sin token CSRF"));

        then(sesionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout - cierra la sesion del access token y borra las cookies (204)")
    void AuthController_logout_conSesion_cierraLaSesionYBorraLasCookies() throws Exception {
        MockHttpServletResponse respuesta = mockMvc.perform(post("/api/v1/auth/logout")
                        .with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();

        then(sesionService).should().cerrar(1L, 1L, SESION);
        then(sesionService).should(never()).cerrarConToken(anyString());
        assertThat(borradas(respuesta)).containsExactlyInAnyOrder(CookiesDeSesion.ACCESO, CookiesDeSesion.REFRESCO);
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout - con la cookie de refresco tambien cierra su sesion (204)")
    void AuthController_logout_conCookieDeRefresco_cierraTambienSuSesion() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").with(principal(RolAcceso.EDITOR)).with(conCsrf())
                        .cookie(new Cookie(CookiesDeSesion.REFRESCO, "refresh-1")))
                .andExpect(status().isNoContent());

        then(sesionService).should().cerrar(1L, 1L, SESION);
        then(sesionService).should().cerrarConToken("refresh-1");
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout - sin sesion tambien responde 204 y borra las cookies, para poder salir")
    void AuthController_logout_sinSesion_devuelve204() throws Exception {
        MockHttpServletResponse respuesta = mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();

        then(sesionService).shouldHaveNoInteractions();
        assertThat(borradas(respuesta)).containsExactlyInAnyOrder(CookiesDeSesion.ACCESO, CookiesDeSesion.REFRESCO);
    }

    @Test
    @DisplayName("GET /api/v1/auth/csrf - deja el token CSRF en una cookie que el JavaScript de la web puede leer (204)")
    void AuthController_csrf_dejaLaCookieDelToken() throws Exception {
        MockHttpServletResponse respuesta = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();

        Cookie token = respuesta.getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();
        assertThat(token.getValue()).isNotBlank();
        assertThat(token.isHttpOnly()).isFalse();
        assertThat(token.getSecure()).isTrue();
        assertThat(token.getAttribute("SameSite")).isEqualTo("Lax");
    }

    /** Los nombres de las cookies que la respuesta vencio: vacias y con Max-Age=0. */
    private static List<String> borradas(MockHttpServletResponse respuesta) {
        return respuesta.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> cookie.contains("Max-Age=0"))
                .map(cookie -> cookie.substring(0, cookie.indexOf('=')))
                .toList();
    }

    /** Un Set-Cookie esperado como patron: la fecha de Expires cambia con el reloj, y ahi va un comodin. */
    private static String comoPatron(String esperada) {
        return Pattern.quote(esperada).replace("*", "\\E[^;]+\\Q");
    }

    private UsuarioResponse usuario() {
        return new UsuarioResponse(10L, "Juan", "juan@acme.com", RolAcceso.ADMINISTRADOR, true, 1L, 0L, null, null,
                null, null, false, null);
    }
}
