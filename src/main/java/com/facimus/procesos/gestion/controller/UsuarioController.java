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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.gestion.dto.request.ActualizarUsuarioRequest;
import com.facimus.procesos.gestion.dto.request.CrearUsuarioRequest;
import com.facimus.procesos.gestion.dto.request.RolesDeUsuarioRequest;
import com.facimus.procesos.gestion.dto.response.RolDeUsuarioResponse;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.service.MembresiaRolService;
import com.facimus.procesos.gestion.service.UsuarioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

/** HU-02: administracion de colaboradores de la empresa (solo administrador). */
@Tag(name = "Users", description = "The store's collaborators and their access roles. Administrators only.")
@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private static final String ORDEN = "(nombre|email|rolAcceso)(,(asc|desc))?";

    private final UsuarioService usuarioService;
    private final MembresiaRolService membresiaRolService;

    @Operation(summary = "List users",
            description = "Pages of the store's users, by name by default. Only the active ones, unless "
                    + "incluirInactivos asks for the deactivated ones too, which is how one is reactivated.")
    @ApiResponse(responseCode = "200", description = "One page of the store's users")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @GetMapping
    public ResponseEntity<PageResponse<UsuarioResponse>> listar(
            @Parameter(description = "Part of the name, ignoring case and surrounding spaces", example = "ana")
            @RequestParam(required = false) String nombre,
            // Todo este controlador es de administradores (SecurityConfig), asi que aqui no hay que volver a
            // preguntar quien mira lo desactivado: no llega nadie mas.
            @Parameter(description = "Also list the deactivated users")
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            @Parameter(description = "Page number, starting at 0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGINA_INVALIDA) int pagina,
            @Parameter(description = "Items per page, from 1 to 50")
            @RequestParam(defaultValue = "10") @Min(value = 1, message = Paginacion.TAMANO_INVALIDO)
            @Max(value = Paginacion.TAMANO_MAXIMO, message = Paginacion.TAMANO_INVALIDO) int tamano,
            @Parameter(description = "Field and direction: nombre, email or rolAcceso, then asc or desc",
                    example = "email,asc")
            @RequestParam(defaultValue = "nombre,asc") @Pattern(regexp = ORDEN,
                    message = "Orden no permitido. Use nombre, email o rolAcceso, con ,asc o ,desc.") String orden,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(usuarioService.buscar(principal.empresaId(), nombre, incluirInactivos,
                Paginacion.de(pagina, tamano, orden)));
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
        UsuarioResponse usuario = usuarioService.crearColaborador(empresaId, principal.usuarioId(),
                request.nombre(), request.email(), request.password(), request.rolAcceso());
        return ResponseEntity.created(URI.create("/api/v1/usuarios/" + usuario.id())).body(usuario);
    }

    @Operation(summary = "Get a user")
    @ApiResponse(responseCode = "200", description = "The user")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponse> obtener(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(usuarioService.obtener(principal.empresaId(), id));
    }

    @Operation(summary = "Give a user a new temporary password",
            description = "Answers it once in claveTemporal and closes every session of that user. Whoever signs in "
                    + "with it can only change it. Administrators only.")
    @ApiResponse(responseCode = "200", description = "The user and the temporary password, answered only here")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @PostMapping("/{id}/restablecer-clave")
    public ResponseEntity<UsuarioResponse> restablecerClave(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(usuarioService.restablecerClave(principal.empresaId(), principal.usuarioId(), id));
    }

    @Operation(summary = "Change the name, the access role or the status of a user",
            description = "Whatever is left out stays as it was. A store always keeps an active administrator, and "
                    + "nobody can deactivate their own account.")
    @ApiResponse(responseCode = "200", description = "User updated")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PatchMapping("/{id}")
    public ResponseEntity<UsuarioResponse> actualizar(@PathVariable Long id,
            @Validated @RequestBody ActualizarUsuarioRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(usuarioService.actualizar(principal.empresaId(), principal.usuarioId(), id,
                request.nombre(), request.rolAcceso(), request.activo(), request.version()));
    }

    @Operation(summary = "Deactivate a user",
            description = "The user can no longer log in, and the tokens already issued stop working. Nobody can "
                    + "deactivate their own account.")
    @ApiResponse(responseCode = "204", description = "User deactivated")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        usuarioService.desactivar(principal.empresaId(), principal.usuarioId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List the process roles of a user",
            description = "Which process roles this person belongs to, which is what lets them ask for their own "
                    + "tray. It gives no permissions: what someone can do is still their access role. "
                    + "Administrators only.")
    @ApiResponse(responseCode = "200", description = "The process roles of the user")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/{id}/roles-proceso")
    public ResponseEntity<List<RolDeUsuarioResponse>> rolesDeProceso(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(membresiaRolService.rolesDe(principal.empresaId(), id));
    }

    @Operation(summary = "Replace the process roles of a user",
            description = "Takes the whole list: whatever is sent is what the person ends up with, and an empty "
                    + "list leaves them with none. A retired role is not handed out. Administrators only.")
    @ApiResponse(responseCode = "200", description = "The process roles the user now has")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @PutMapping("/{id}/roles-proceso")
    public ResponseEntity<List<RolDeUsuarioResponse>> reemplazarRolesDeProceso(@PathVariable Long id,
            @Validated @RequestBody RolesDeUsuarioRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(membresiaRolService.reemplazar(principal.empresaId(), principal.usuarioId(), id,
                request.rolesProcesoIds()));
    }
}
