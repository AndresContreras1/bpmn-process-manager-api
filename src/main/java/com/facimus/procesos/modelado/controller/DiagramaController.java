package com.facimus.procesos.modelado.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.service.DiagramaService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** El diagrama BPMN completo de un proceso, para que un cliente lo dibuje con una sola peticion. */
@Tag(name = "Diagrams", description = "A whole BPMN diagram in one request, ready to draw")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DiagramaController {

    private final DiagramaService diagramaService;

    @Operation(summary = "Get the whole diagram of a process",
            description = "The process with its pools, lanes, activities, gateways, sequence flows, message flows and "
                    + "correlation keys. Any role can read it.")
    @ApiResponse(responseCode = "200", description = "The diagram")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/procesos/{procesoId}/diagrama")
    public ResponseEntity<DiagramaResponse> obtener(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(diagramaService.obtener(empresaId, procesoId));
    }
}
