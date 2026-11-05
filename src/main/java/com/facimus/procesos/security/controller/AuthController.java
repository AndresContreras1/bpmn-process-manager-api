package com.facimus.procesos.security.controller;

import java.time.Duration;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.SesionInvalidaException;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.gestion.dto.request.CambiarClaveRequest;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.dto.response.SesionIniciada;
import com.facimus.procesos.gestion.dto.response.SesionResponse;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.service.SesionService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.security.CookiesDeSesion;
import com.facimus.procesos.security.JwtService;
import com.facimus.procesos.security.LoginAuthenticator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * HU-03 y D29: inicio, renovacion y cierre de sesion. Los tokens viajan en cookies que el navegador guarda y manda
 * solo, y que el JavaScript de la pagina no puede leer; el cuerpo de la respuesta dice quien entro y cuanto dura el
 * acceso, nunca un token.
 */
@Tag(name = "Authentication", description = "Login, renewal and logout. The session travels in two HttpOnly cookies: "
        + "a short-lived JWT for the whole API and a single-use refresh token for /api/v1/auth")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LoginAuthenticator loginAuthenticator;
    private final SesionService sesionService;
    private final UsuarioService usuarioService;
    private final JwtService jwtService;
    private final CookiesDeSesion cookies;

    public AuthController(LoginAuthenticator loginAuthenticator, SesionService sesionService,
            UsuarioService usuarioService, JwtService jwtService, CookiesDeSesion cookies) {
        this.loginAuthenticator = loginAuthenticator;
        this.sesionService = sesionService;
        this.usuarioService = usuarioService;
        this.jwtService = jwtService;
        this.cookies = cookies;
    }

    @Operation(summary = "Get the CSRF token", description = "Sets the XSRF-TOKEN cookie, whose value every request "
            + "that changes something with the session cookies sends back in the X-XSRF-TOKEN header. The web app "
            + "calls it when it starts; the login asks for the header too.")
    @ApiResponse(responseCode = "204", description = "The XSRF-TOKEN cookie is set")
    @SecurityRequirements()
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        // Leerlo es lo que lo crea y escribe su cookie: el token se carga cuando alguien lo pide.
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Log in", description = "Checks the email and password and opens a session: an access "
            + "cookie with a signed JWT that lasts 15 minutes and a refresh cookie that renews it. The refresh "
            + "cookie lasts the store's idle timeout, an hour unless the store shortened it, and the session ends "
            + "the store's maximum duration after the login, 24 hours unless shortened: then the user signs in "
            + "again. A wrong password and an unknown email get the same answer. After 5 failed attempts for an "
            + "email from the same address within 15 minutes, the login answers 429 until the oldest attempt leaves "
            + "that window.")
    @ApiResponse(responseCode = "200", description = "The session cookies, and in the body the user's profile")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
    @SecurityRequirements()
    @PostMapping("/login")
    public ResponseEntity<SesionResponse> login(@Validated @RequestBody LoginRequest request,
            HttpServletRequest peticion, HttpServletResponse respuesta) {
        // Si falla, ApiExceptionHandler responde 401 con el mismo mensaje generico, exista o no el correo (HU-03).
        UsuarioResponse usuario = loginAuthenticator.autenticar(request.email(), request.password(),
                peticion.getRemoteAddr());
        return ResponseEntity.ok(abrir(sesionService.iniciar(usuario.empresaId(), usuario.id()), respuesta));
    }

    @Operation(summary = "Renew the session", description = "Trades the refresh cookie for a new access cookie and a "
            + "new refresh cookie of the same session. Each refresh token works once: sending one that was already "
            + "used closes the session, because a copy of it is going around. Past the store's maximum duration "
            + "the session closes, and near it neither cookie outlives it. A refusal also clears the cookies.")
    @ApiResponse(responseCode = "200", description = "New cookies of the session, and in the body the user's profile")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @SecurityRequirements()
    @PostMapping("/refresh")
    public ResponseEntity<SesionResponse> renovar(HttpServletRequest peticion, HttpServletResponse respuesta) {
        try {
            String refreshToken = CookiesDeSesion.leer(peticion, CookiesDeSesion.REFRESCO)
                    .orElseThrow(SesionInvalidaException::new);
            return ResponseEntity.ok(abrir(sesionService.renovar(refreshToken), respuesta));
        } catch (SesionInvalidaException e) {
            // Una cookie que ya no renueva nada no tiene que seguir viajando en cada peticion.
            cookies.cerrar(respuesta);
            throw e;
        }
    }

    @Operation(summary = "Log out", description = "Closes the session of the cookies, the one the access token names "
            + "and the one the refresh token belongs to, and clears both cookies. It answers the same with an expired "
            + "access token or without a session, so a browser can always leave.")
    @ApiResponse(responseCode = "204", description = "Session closed and cookies cleared")
    @SecurityRequirements()
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal ApiPrincipal principal, HttpServletRequest peticion,
            HttpServletResponse respuesta) {
        if (principal != null) {
            sesionService.cerrar(principal.empresaId(), principal.usuarioId(), principal.sesion());
        }
        CookiesDeSesion.leer(peticion, CookiesDeSesion.REFRESCO).ifPresent(sesionService::cerrarConToken);
        cookies.cerrar(respuesta);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Change your own password",
            description = "Checks the password in use and replaces it. Every session of the user is closed, this "
                    + "one included, and the answer opens a new one with new cookies. A user who signed in with a "
                    + "temporary password can only call this, logout and refresh until the password is changed.")
    @ApiResponse(responseCode = "200", description = "The cookies of a new session, and in the body the user's profile")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @PostMapping("/password")
    public ResponseEntity<SesionResponse> cambiarClave(@Validated @RequestBody CambiarClaveRequest request,
            @AuthenticationPrincipal ApiPrincipal principal, HttpServletResponse respuesta) {
        Long empresaId = principal.empresaId();
        Long usuarioId = principal.usuarioId();
        usuarioService.cambiarClavePropia(empresaId, usuarioId, request.actual(), request.nueva());
        // Cambiar la contrasena echa a quien la estuviera usando en otro sitio; quien la cambio sigue con una nueva.
        sesionService.cerrarTodas(empresaId, usuarioId);
        return ResponseEntity.ok(abrir(sesionService.iniciar(empresaId, usuarioId), respuesta));
    }

    /**
     * Las cookies de la sesion, con su access token recien firmado; el cuerpo, sin tokens. El acceso no pasa del fin
     * de la sesion: a las 24 horas se vuelve a entrar, no a las 24 y cuarto.
     */
    private SesionResponse abrir(SesionIniciada sesion, HttpServletResponse respuesta) {
        Duration maximo = Duration.ofSeconds(jwtService.getExpirationSeconds());
        Duration vidaDelAcceso = sesion.vidaRestante().compareTo(maximo) < 0 ? sesion.vidaRestante() : maximo;
        String accessToken = jwtService.generarToken(sesion.usuario().comoPrincipal(sesion.sesion()), vidaDelAcceso);
        cookies.abrir(respuesta, accessToken, vidaDelAcceso, sesion.refreshToken(), sesion.vidaDelRefresco());
        return new SesionResponse(vidaDelAcceso.toSeconds(), sesion.usuario());
    }
}
