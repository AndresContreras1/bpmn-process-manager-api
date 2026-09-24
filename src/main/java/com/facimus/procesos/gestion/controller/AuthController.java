package com.facimus.procesos.gestion.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.gestion.dto.request.CerrarSesionRequest;
import com.facimus.procesos.gestion.dto.request.CambiarClaveRequest;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.dto.request.RenovarTokenRequest;
import com.facimus.procesos.gestion.dto.response.LoginResponse;
import com.facimus.procesos.gestion.dto.response.SesionIniciada;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.service.SesionService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.security.ApiPrincipal;
import com.facimus.procesos.security.JwtService;
import com.facimus.procesos.security.LoginAuthenticator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/** HU-03: inicio, renovacion y cierre de sesion con un access token corto y un refresh token que rota. */
@Tag(name = "Authentication", description = "Login, token renewal and logout: short-lived JWT access tokens and "
        + "single-use refresh tokens")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String TIPO_TOKEN = "Bearer";

    private final LoginAuthenticator loginAuthenticator;
    private final SesionService sesionService;
    private final UsuarioService usuarioService;
    private final JwtService jwtService;

    public AuthController(LoginAuthenticator loginAuthenticator, SesionService sesionService,
            UsuarioService usuarioService, JwtService jwtService) {
        this.loginAuthenticator = loginAuthenticator;
        this.sesionService = sesionService;
        this.usuarioService = usuarioService;
        this.jwtService = jwtService;
    }

    @Operation(summary = "Log in", description = "Checks the email and password and opens a session: a signed access "
            + "token that lasts 15 minutes and a refresh token that renews it. A wrong password and an unknown email "
            + "get the same answer. After 5 failed attempts for an email from the same address within 15 minutes, "
            + "the login answers 429 until the oldest attempt leaves that window.")
    @ApiResponse(responseCode = "200", description = "The tokens of the new session and the user's profile")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
    @SecurityRequirements()
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Validated @RequestBody LoginRequest request,
            HttpServletRequest peticion) {
        // Si falla, ApiExceptionHandler responde 401 con el mismo mensaje generico, exista o no el correo (HU-03).
        UsuarioResponse usuario = loginAuthenticator.autenticar(request.email(), request.password(),
                peticion.getRemoteAddr());
        return ResponseEntity.ok(tokens(sesionService.iniciar(usuario.empresaId(), usuario.id())));
    }

    @Operation(summary = "Renew the tokens", description = "Trades a refresh token for a new access token and a new "
            + "refresh token of the same session. Each refresh token works once: sending one that was already used "
            + "closes the session, because a copy of it is going around.")
    @ApiResponse(responseCode = "200", description = "New tokens of the session and the user's profile")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @SecurityRequirements()
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> renovar(@Validated @RequestBody RenovarTokenRequest request) {
        return ResponseEntity.ok(tokens(sesionService.renovar(request.refreshToken())));
    }

    @Operation(summary = "Log out", description = "Closes the session of the access token: the token and the refresh "
            + "token of the session stop working right away. A refresh token in the body closes its session too.")
    @ApiResponse(responseCode = "204", description = "Session closed")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Validated @RequestBody(required = false) CerrarSesionRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        sesionService.cerrar(principal.empresaId(), principal.usuarioId(), principal.sesion());
        if (request != null) {
            sesionService.cerrarConToken(principal.empresaId(), principal.usuarioId(), request.refreshToken());
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Change your own password",
            description = "Checks the password in use and replaces it. Every session of the user is closed, this "
                    + "one included, and the answer opens a new one: the tokens that come back are the ones to "
                    + "keep. A user who signed in with a temporary password can only call this, logout and refresh "
                    + "until the password is changed.")
    @ApiResponse(responseCode = "200", description = "New tokens of a new session and the user's profile")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @PostMapping("/password")
    public ResponseEntity<LoginResponse> cambiarClave(@Validated @RequestBody CambiarClaveRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Long usuarioId = principal.usuarioId();
        usuarioService.cambiarClavePropia(empresaId, usuarioId, request.actual(), request.nueva());
        // Cambiar la contrasena echa a quien la estuviera usando en otro sitio; quien la cambio sigue con una nueva.
        sesionService.cerrarTodas(empresaId, usuarioId);
        return ResponseEntity.ok(tokens(sesionService.iniciar(empresaId, usuarioId)));
    }

    private LoginResponse tokens(SesionIniciada sesion) {
        String accessToken = jwtService.generarToken(ApiPrincipal.of(sesion.usuario(), sesion.sesion()));
        return new LoginResponse(accessToken, TIPO_TOKEN, jwtService.getExpirationSeconds(), sesion.refreshToken(),
                sesion.usuario());
    }
}
