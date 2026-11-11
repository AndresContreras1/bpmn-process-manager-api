package com.facimus.procesos.gestion.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.gestion.dto.request.ConcesionRequest;
import com.facimus.procesos.gestion.dto.response.ConcesionResponse;
import com.facimus.procesos.gestion.dto.response.PermisosResponse;
import com.facimus.procesos.gestion.service.Actor;
import com.facimus.procesos.gestion.service.ConcesionService;
import com.facimus.procesos.gestion.service.Permisos;
import com.facimus.procesos.gestion.service.Recurso;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** PR 60: quien puede hacer que sobre una carpeta o un proceso. */
@Tag(name = "Permissions", description = "Actions granted on folders and processes")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ConcesionController {

    private final ConcesionService concesionService;
    private final Permisos permisos;

    @Operation(summary = "What you can do with a process")
    @ApiResponse(responseCode = "200", description = "The actions")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/procesos/{procesoId}/permisos")
    public ResponseEntity<PermisosResponse> permisos(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(new PermisosResponse(permisos.acciones(actor(principal), Recurso.proceso(procesoId))));
    }

    @Operation(summary = "List what is granted on a process")
    @ApiResponse(responseCode = "200", description = "The grants")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/procesos/{procesoId}/concesiones")
    public ResponseEntity<List<ConcesionResponse>> delProceso(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(concesionService.listar(principal.empresaId(), Recurso.proceso(procesoId)));
    }

    @Operation(summary = "Grant an action on a process",
            description = "From then on that action is only for those who have it, and administrators.")
    @ApiResponse(responseCode = "201", description = "Granted")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/procesos/{procesoId}/concesiones")
    public ResponseEntity<ConcesionResponse> concederEnProceso(@PathVariable Long procesoId,
            @Validated @RequestBody ConcesionRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return creada(concesionService.conceder(actor(principal), Recurso.proceso(procesoId), request.accion(),
                request.sujeto(), request.usuarioId(), request.rolProcesoId()));
    }

    @Operation(summary = "List what is granted on a folder")
    @ApiResponse(responseCode = "200", description = "The grants")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/carpetas/{carpetaId}/concesiones")
    public ResponseEntity<List<ConcesionResponse>> deCarpeta(@PathVariable Long carpetaId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(concesionService.listar(principal.empresaId(), Recurso.carpeta(carpetaId)));
    }

    @Operation(summary = "Grant an action on a folder", description = "What is inside the folder inherits it.")
    @ApiResponse(responseCode = "201", description = "Granted")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/carpetas/{carpetaId}/concesiones")
    public ResponseEntity<ConcesionResponse> concederEnCarpeta(@PathVariable Long carpetaId,
            @Validated @RequestBody ConcesionRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return creada(concesionService.conceder(actor(principal), Recurso.carpeta(carpetaId), request.accion(),
                request.sujeto(), request.usuarioId(), request.rolProcesoId()));
    }

    @Operation(summary = "Take a grant back")
    @ApiResponse(responseCode = "204", description = "Taken back")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @DeleteMapping("/concesiones/{id}")
    public ResponseEntity<Void> retirar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        concesionService.retirar(actor(principal), id);
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<ConcesionResponse> creada(ConcesionResponse concesion) {
        return ResponseEntity.created(URI.create("/api/v1/concesiones/" + concesion.id())).body(concesion);
    }

    private static Actor actor(ApiPrincipal principal) {
        return new Actor(principal.usuarioId(), principal.empresaId(), principal.rol());
    }
}
