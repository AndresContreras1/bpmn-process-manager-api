package com.facimus.procesos.gestion.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
import com.facimus.procesos.gestion.dto.request.EditarRolProcesoRequest;
import com.facimus.procesos.gestion.dto.request.RolProcesoRequest;
import com.facimus.procesos.gestion.dto.response.RolProcesoVistaResponse;
import com.facimus.procesos.gestion.service.RolProcesoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

/** HU-17 a HU-20: roles de proceso (solo administrador crea/edita/elimina). */
@Tag(name = "Process roles",
        description = "Responsibilities that lanes represent, such as Sales or Warehouse. Only administrators change them.")
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RolProcesoController {

    private static final String ORDEN = "nombre(,(asc|desc))?";

    private final RolProcesoService rolProcesoService;

    @Operation(summary = "List process roles",
            description = "Pages of process roles by name, which can be searched (HU-20). Each role says how many "
                    + "processes use it.")
    @ApiResponse(responseCode = "200", description = "One page of the store's process roles")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping
    public ResponseEntity<PageResponse<RolProcesoVistaResponse>> listar(
            @Parameter(description = "Part of the name, case-insensitive", example = "ware")
            @RequestParam(required = false) String nombre,
            @Parameter(description = "Page number, starting at 0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGINA_INVALIDA) int pagina,
            @Parameter(description = "Items per page, from 1 to 50")
            @RequestParam(defaultValue = "10") @Min(value = 1, message = Paginacion.TAMANO_INVALIDO)
            @Max(value = Paginacion.TAMANO_MAXIMO, message = Paginacion.TAMANO_INVALIDO) int tamano,
            @Parameter(description = "Direction by name: nombre,asc or nombre,desc", example = "nombre,asc")
            @RequestParam(defaultValue = "nombre,asc") @Pattern(regexp = ORDEN,
                    message = "Orden no permitido. Use nombre, con ,asc o ,desc.") String orden,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(rolProcesoService.buscar(principal.empresaId(), nombre,
                Paginacion.de(pagina, tamano, orden)));
    }

    @Operation(summary = "Create a process role")
    @ApiResponse(responseCode = "201", description = "Process role created",
            headers = @Header(name = HttpHeaders.LOCATION, description = "URL of the new process role"))
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping
    public ResponseEntity<RolProcesoVistaResponse> crear(@Validated @RequestBody RolProcesoRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        RolProcesoVistaResponse rol = rolProcesoService.crear(principal.empresaId(), principal.usuarioId(),
                request.nombre(), request.descripcion());
        return ResponseEntity.created(URI.create("/api/v1/roles/" + rol.id())).body(rol);
    }

    @Operation(summary = "Get a process role")
    @ApiResponse(responseCode = "200", description = "The process role and its usage")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/{id}")
    public ResponseEntity<RolProcesoVistaResponse> obtener(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(rolProcesoService.obtener(principal.empresaId(), id));
    }

    @Operation(summary = "Edit a process role")
    @ApiResponse(responseCode = "200", description = "Process role updated")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/{id}")
    public ResponseEntity<RolProcesoVistaResponse> editar(@PathVariable Long id,
            @Validated @RequestBody EditarRolProcesoRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(rolProcesoService.editar(principal.empresaId(), principal.usuarioId(), id,
                request.nombre(), request.descripcion(), request.version()));
    }

    @Operation(summary = "Delete a process role",
            description = "Soft delete. A role that a lane still uses cannot be deleted.")
    @ApiResponse(responseCode = "204", description = "Process role deleted")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        rolProcesoService.eliminar(empresaId, principal.usuarioId(), id);
        return ResponseEntity.noContent().build();
    }
}
