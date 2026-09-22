package com.facimus.procesos.gestion.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.dto.response.LoginResponse;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.security.ApiPrincipal;
import com.facimus.procesos.security.JwtService;
import com.facimus.procesos.security.LoginAuthenticator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** HU-03: inicio y cierre de sesion con JWT. */
@Tag(name = "Authentication", description = "Login and logout with stateless JWT access tokens")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String TIPO_TOKEN = "Bearer";

    private final LoginAuthenticator loginAuthenticator;
    private final JwtService jwtService;

    public AuthController(LoginAuthenticator loginAuthenticator, JwtService jwtService) {
        this.loginAuthenticator = loginAuthenticator;
        this.jwtService = jwtService;
    }

    @Operation(summary = "Log in", description = "Checks the email and password and returns a signed access token. "
            + "A wrong password and an unknown email get the same answer.")
    @ApiResponse(responseCode = "200", description = "Access token and the user's profile")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @SecurityRequirements()
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Validated @RequestBody LoginRequest request) {
        // Si falla, ApiExceptionHandler responde 401 con el mismo mensaje generico, exista o no el correo (HU-03).
        UsuarioResponse usuario = loginAuthenticator.autenticar(request.email(), request.password());
        String token = jwtService.generarToken(ApiPrincipal.of(usuario));
        return ResponseEntity.ok(new LoginResponse(token, TIPO_TOKEN, jwtService.getExpirationSeconds(), usuario));
    }

    /** Sin estado en el servidor: cerrar sesion es que el cliente descarte su token. */
    @Operation(summary = "Log out", description = "Tokens are stateless, so the client discards its token. "
            + "The endpoint gives clients a single logout call.")
    @ApiResponse(responseCode = "204", description = "Logged out")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }
}
