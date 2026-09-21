package com.facimus.procesos.gestion.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.gestion.controller.dto.ActualizarUsuarioRequest;
import com.facimus.procesos.gestion.controller.dto.CrearUsuarioRequest;
import com.facimus.procesos.gestion.controller.dto.UsuarioResponse;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** HU-02: administracion de colaboradores de la empresa (solo administrador). */
@Tag(name = "Users", description = "The store's collaborators and their access roles. Administrators only.")
@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @Operation(summary = "List active users")
    @ApiResponse(responseCode = "200", description = "The store's active users")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @GetMapping
    public ResponseEntity<List<UsuarioResponse>> listar(@AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        List<UsuarioResponse> usuarios = usuarioService.listarPorEmpresa(empresaId).stream()
                .map(UsuarioResponse::of)
                .toList();
        return ResponseEntity.ok(usuarios);
    }

    @Operation(summary = "Create a user", description = "The email cannot belong to a user of any store.")
    @ApiResponse(responseCode = "201", description = "User created",
            headers = @Header(name = HttpHeaders.LOCATION, description = "URL of the new user"))
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(@Validated @RequestBody CrearUsuarioRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Usuario usuario = usuarioService.crearColaborador(empresaId, request.nombre(), request.email(),
                request.password(), request.rolAcceso());
        return ResponseEntity.created(URI.create("/api/v1/usuarios/" + usuario.getId()))
                .body(UsuarioResponse.of(usuario));
    }

    @Operation(summary = "Get a user")
    @ApiResponse(responseCode = "200", description = "The user")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponse> obtener(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Usuario usuario = usuarioService.obtener(empresaId, id);
        return ResponseEntity.ok(UsuarioResponse.of(usuario));
    }

    @Operation(summary = "Change the access role or the status of a user")
    @ApiResponse(responseCode = "200", description = "User updated")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @PatchMapping("/{id}")
    public ResponseEntity<UsuarioResponse> actualizar(@PathVariable Long id,
            @Validated @RequestBody ActualizarUsuarioRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Usuario usuario = usuarioService.actualizar(empresaId, id, request.rolAcceso(), request.activo());
        return ResponseEntity.ok(UsuarioResponse.of(usuario));
    }

    @Operation(summary = "Deactivate a user",
            description = "The user can no longer log in, and the tokens already issued stop working.")
    @ApiResponse(responseCode = "204", description = "User deactivated")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        usuarioService.desactivar(empresaId, id);
        return ResponseEntity.noContent().build();
    }
}
