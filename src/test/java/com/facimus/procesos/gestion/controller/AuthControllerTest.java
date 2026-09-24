package com.facimus.procesos.gestion.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.SESION;
import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.common.DemasiadosIntentosException;
import com.facimus.procesos.common.SesionInvalidaException;
import com.facimus.procesos.gestion.dto.request.CerrarSesionRequest;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.dto.request.RenovarTokenRequest;
import com.facimus.procesos.gestion.dto.response.SesionIniciada;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.service.SesionService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.security.ApiPrincipal;
import com.facimus.procesos.security.JwtService;
import com.facimus.procesos.security.LoginAuthenticator;

import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(AuthController.class)
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
    @DisplayName("POST /api/v1/auth/login - credenciales validas abren una sesion con sus dos tokens (200)")
    void AuthController_login_credencialesValidas_devuelveLosTokens() throws Exception {
        // Arrange
        UsuarioResponse usuario = usuario();
        given(loginAuthenticator.autenticar("juan@acme.com", "secret123", "127.0.0.1")).willReturn(usuario);
        given(sesionService.iniciar(1L, 10L)).willReturn(new SesionIniciada("refresh-de-prueba", "sesion-1", usuario));
        given(jwtService.generarToken(any(ApiPrincipal.class))).willReturn("token-de-prueba");
        given(jwtService.getExpirationSeconds()).willReturn(900L);

        // Act + Assert
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest("juan@acme.com", "secret123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token-de-prueba"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").value("refresh-de-prueba"))
                .andExpect(jsonPath("$.usuario.email").value("juan@acme.com"))
                .andExpect(jsonPath("$.usuario.rolAcceso").value("ADMINISTRADOR"));

        then(jwtService).should()
                .generarToken(new ApiPrincipal(10L, 1L, RolAcceso.ADMINISTRADOR, "juan@acme.com", "sesion-1", false));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - credenciales invalidas devuelven 401 generico sin abrir sesion")
    void AuthController_login_credencialesInvalidas_devuelve401() throws Exception {
        // Arrange
        given(loginAuthenticator.autenticar("juan@acme.com", "mala", "127.0.0.1"))
                .willThrow(new BadCredentialsException("Bad credentials"));

        // Act + Assert
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest("juan@acme.com", "mala"))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas o token ausente"));

        then(sesionService).shouldHaveNoInteractions();
        then(jwtService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - un correo bloqueado por fallos devuelve 429 con Retry-After")
    void AuthController_login_bloqueadoPorFallos_devuelve429() throws Exception {
        given(loginAuthenticator.autenticar("juan@acme.com", "secret123", "127.0.0.1"))
                .willThrow(new DemasiadosIntentosException(Duration.ofSeconds(90)));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest("juan@acme.com", "secret123"))))
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
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest("", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh - sin access token, cambia el refresh token por tokens nuevos (200)")
    void AuthController_refresh_tokenVigente_devuelveTokensNuevos() throws Exception {
        // Arrange
        UsuarioResponse usuario = usuario();
        given(sesionService.renovar("refresh-viejo"))
                .willReturn(new SesionIniciada("refresh-nuevo", "sesion-1", usuario));
        given(jwtService.generarToken(any(ApiPrincipal.class))).willReturn("token-nuevo");
        given(jwtService.getExpirationSeconds()).willReturn(900L);

        // Act + Assert
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new RenovarTokenRequest("refresh-viejo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token-nuevo"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").value("refresh-nuevo"))
                .andExpect(jsonPath("$.usuario.id").value(10));

        then(jwtService).should()
                .generarToken(new ApiPrincipal(10L, 1L, RolAcceso.ADMINISTRADOR, "juan@acme.com", "sesion-1", false));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh - un refresh token que no sirve devuelve 401 con WWW-Authenticate")
    void AuthController_refresh_sesionInvalida_devuelve401() throws Exception {
        given(sesionService.renovar("refresh-usado")).willThrow(new SesionInvalidaException());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new RenovarTokenRequest("refresh-usado"))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.title").value("Sesión no válida"))
                .andExpect(jsonPath("$.detail").value("La sesión expiró o se cerró. Inicia sesión de nuevo."));

        then(jwtService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh - sin refresh token devuelve 400 con el campo")
    void AuthController_refresh_sinToken_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.refreshToken").value("El refresh token es obligatorio."));

        then(sesionService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout - sin cuerpo cierra la sesion del access token (204)")
    void AuthController_logout_sinCuerpo_cierraLaSesionDelToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isNoContent());

        then(sesionService).should().cerrar(1L, 1L, SESION);
        then(sesionService).should(never()).cerrarConToken(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout - con un refresh token en el cuerpo tambien cierra su sesion (204)")
    void AuthController_logout_conRefreshToken_cierraTambienSuSesion() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CerrarSesionRequest("refresh-1"))))
                .andExpect(status().isNoContent());

        then(sesionService).should().cerrar(1L, 1L, SESION);
        then(sesionService).should().cerrarConToken(1L, 1L, "refresh-1");
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout - sin autenticar devuelve 401")
    void AuthController_logout_sinAutenticar_devuelve401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());

        then(sesionService).shouldHaveNoInteractions();
    }

    private UsuarioResponse usuario() {
        return new UsuarioResponse(10L, "Juan", "juan@acme.com", RolAcceso.ADMINISTRADOR, true, 1L, 0L, null, null,
                null, null, false, null);
    }
}
