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
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.gestion.dto.request.RolProcesoRequest;
import com.facimus.procesos.gestion.dto.response.RolProcesoVistaResponse;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** HU-17 a HU-20: roles de proceso (solo administrador crea/edita/elimina). */
@Tag(name = "Process roles",
        description = "Responsibilities that lanes represent, such as Sales or Warehouse. Only administrators change them.")
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RolProcesoController {

    private final RolProcesoService rolProcesoService;

    @Operation(summary = "List process roles", description = "Each role says how many processes use it.")
    @ApiResponse(responseCode = "200", description = "The store's process roles")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping
    public ResponseEntity<List<RolProcesoVistaResponse>> listar(@AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        List<RolProcesoVistaResponse> roles = rolProcesoService.listarConUso(empresaId).stream()
                .map(RolProcesoVistaResponse::of)
                .toList();
        return ResponseEntity.ok(roles);
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
        Long empresaId = principal.empresaId();
        RolProceso rol = rolProcesoService.crear(empresaId, request.nombre(), request.descripcion());
        return ResponseEntity.created(URI.create("/api/v1/roles/" + rol.getId()))
                .body(new RolProcesoVistaResponse(rol.getId(), rol.getNombre(), rol.getDescripcion(), 0, false));
    }

    @Operation(summary = "Get a process role")
    @ApiResponse(responseCode = "200", description = "The process role and its usage")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/{id}")
    public ResponseEntity<RolProcesoVistaResponse> obtener(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        RolProceso rol = rolProcesoService.obtener(empresaId, id);
        long usos = rolProcesoService.contarUsos(empresaId, id);
        return ResponseEntity.ok(new RolProcesoVistaResponse(
                rol.getId(), rol.getNombre(), rol.getDescripcion(), usos, usos > 0));
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
            @Validated @RequestBody RolProcesoRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        RolProceso rol = rolProcesoService.editar(empresaId, id, request.nombre(), request.descripcion());
        long usos = rolProcesoService.contarUsos(empresaId, id);
        return ResponseEntity.ok(
                new RolProcesoVistaResponse(rol.getId(), rol.getNombre(), rol.getDescripcion(), usos, usos > 0));
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
        rolProcesoService.eliminar(empresaId, id);
        return ResponseEntity.noContent().build();
    }
}
