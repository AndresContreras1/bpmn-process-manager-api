package com.facimus.procesos.gestion.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.controller.dto.LoginRequest;
import com.facimus.procesos.gestion.controller.dto.LoginResponse;
import com.facimus.procesos.gestion.controller.dto.UsuarioResponse;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.security.ApiPrincipal;
import com.facimus.procesos.security.JwtService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

/** HU-03: inicio y cierre de sesion con JWT. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String TIPO_TOKEN = "Bearer";

    private final UsuarioService usuarioService;
    private final JwtService jwtService;

    public AuthController(UsuarioService usuarioService, JwtService jwtService) {
        this.usuarioService = usuarioService;
        this.jwtService = jwtService;
    }

    @SecurityRequirements()
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Validated @RequestBody LoginRequest request) {
        Usuario usuario;
        try {
            usuario = usuarioService.autenticar(request.email(), request.password());
        } catch (ReglaNegocioException e) {
            // Sin la causa a proposito: Spring MVC buscaria un handler para ella y responderia 409.
            // Asi lo resuelve JwtAuthEntryPoint con 401 y el mismo mensaje generico, exista o no el correo (HU-03).
            throw new BadCredentialsException(e.getMessage());
        }
        String token = jwtService.generarToken(ApiPrincipal.of(usuario));
        return ResponseEntity.ok(new LoginResponse(token, TIPO_TOKEN, jwtService.getExpirationSeconds(),
                UsuarioResponse.of(usuario)));
    }

    /** Sin estado en el servidor: cerrar sesion es que el cliente descarte su token. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }
}
