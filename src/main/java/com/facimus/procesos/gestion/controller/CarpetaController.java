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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.gestion.dto.request.CarpetaRequest;
import com.facimus.procesos.gestion.dto.request.EditarCarpetaRequest;
import com.facimus.procesos.gestion.dto.response.CarpetaResponse;
import com.facimus.procesos.gestion.service.CarpetaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** PR 59: el arbol de carpetas del repositorio de procesos. */
@Tag(name = "Folders", description = "The tree of folders where processes are filed")
@RestController
@RequestMapping("/api/v1/carpetas")
@RequiredArgsConstructor
public class CarpetaController {

    private final CarpetaService carpetaService;

    @Operation(summary = "List the folders", description = "All of them, each with its parent, to draw the tree.")
    @ApiResponse(responseCode = "200", description = "The folders")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping
    public ResponseEntity<List<CarpetaResponse>> listar(@AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(carpetaService.listar(principal.empresaId()));
    }

    @Operation(summary = "Create a folder")
    @ApiResponse(responseCode = "201", description = "Folder created")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping
    public ResponseEntity<CarpetaResponse> crear(@Validated @RequestBody CarpetaRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        CarpetaResponse carpeta = carpetaService.crear(principal.empresaId(), request.nombre(), request.padreId());
        return ResponseEntity.created(URI.create("/api/v1/carpetas/" + carpeta.id())).body(carpeta);
    }

    @Operation(summary = "Rename or move a folder")
    @ApiResponse(responseCode = "200", description = "Folder updated")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/{id}")
    public ResponseEntity<CarpetaResponse> editar(@PathVariable Long id,
            @Validated @RequestBody EditarCarpetaRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(carpetaService.editar(principal.empresaId(), id, request.nombre(), request.padreId(),
                request.version()));
    }

    @Operation(summary = "Delete an empty folder")
    @ApiResponse(responseCode = "204", description = "Folder deleted")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        carpetaService.eliminar(principal.empresaId(), id);
        return ResponseEntity.noContent().build();
    }
}
