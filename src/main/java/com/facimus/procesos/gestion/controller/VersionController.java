package com.facimus.procesos.gestion.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.gestion.dto.response.VersionResponse;
import com.facimus.procesos.gestion.service.VersionService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** D2: lo que se publico de un proceso, que no cambia aunque su modelo siga editandose. */
@Tag(name = "Process versions",
        description = "What was published of a process: the diagram frozen the day it was published")
@RestController
@RequestMapping("/api/v1/procesos/{procesoId}/versiones")
@RequiredArgsConstructor
public class VersionController {

    private final VersionService versionService;

    @Operation(summary = "List the versions of a process",
            description = "Newest first. Any role can read them.")
    @ApiResponse(responseCode = "200", description = "The versions of the process")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping
    public ResponseEntity<List<VersionResponse>> listar(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(versionService.listar(principal.empresaId(), procesoId));
    }

    @Operation(summary = "Get one version of a process")
    @ApiResponse(responseCode = "200", description = "The version")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/{numero}")
    public ResponseEntity<VersionResponse> obtener(@PathVariable Long procesoId, @PathVariable int numero,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(versionService.obtener(principal.empresaId(), procesoId, numero));
    }

    @Operation(summary = "Get the diagram of one version",
            description = "The diagram exactly as it was published, in the same shape as "
                    + "GET /procesos/{id}/diagrama. It is stored as it was sent, so it is answered as it is: the "
                    + "proceso block is the process as it was that day, not as it is now.")
    @ApiResponse(responseCode = "200", description = "The published diagram",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(type = "object", description = "A whole BPMN diagram, as it was published")))
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping(value = "/{numero}/diagrama", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> diagrama(@PathVariable Long procesoId, @PathVariable int numero,
            @AuthenticationPrincipal ApiPrincipal principal) {
        // Se devuelve el JSON guardado tal cual: leerlo para volver a escribirlo igual no anade nada.
        return ResponseEntity.ok(versionService.definicion(principal.empresaId(), procesoId, numero));
    }
}
