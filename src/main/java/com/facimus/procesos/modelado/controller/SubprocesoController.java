package com.facimus.procesos.modelado.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.modelado.dto.request.ContenedorRequest;
import com.facimus.procesos.modelado.dto.request.LlamadaRequest;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.service.SubprocesoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** PR 56: que contiene un subproceso y que proceso corre una actividad de llamada. */
@Tag(name = "Sub-processes", description = "Nodes inside a sub-process, and the process a call activity runs")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SubprocesoController {

    private final SubprocesoService subprocesoService;

    @Operation(summary = "Put a node inside a sub-process, or take it out",
            description = "Flows do not cross the border of a sub-process, so a node with flows stays where it is.")
    @ApiResponse(responseCode = "204", description = "Node moved")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/nodos/{id}/contenedor")
    public ResponseEntity<Void> mover(@PathVariable Long id, @Validated @RequestBody ContenedorRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        subprocesoService.mover(principal.empresaId(), principal.usuarioId(), id, request.padreId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Choose the process a call activity runs",
            description = "A process of the same store. Circular calls are rejected when publishing.")
    @ApiResponse(responseCode = "200", description = "The activity")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/actividades/{id}/llamada")
    public ResponseEntity<ActividadResponse> llamarA(@PathVariable Long id,
            @Validated @RequestBody LlamadaRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(subprocesoService.llamarA(principal.empresaId(), principal.usuarioId(), id,
                request.procesoId(), request.version()));
    }
}
