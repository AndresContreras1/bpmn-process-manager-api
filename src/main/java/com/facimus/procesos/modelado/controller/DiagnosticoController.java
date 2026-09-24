package com.facimus.procesos.modelado.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.modelado.dto.response.DiagnosticoResponse;
import com.facimus.procesos.modelado.service.DiagnosticoService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

/** Lo que el diagrama tiene mal antes de publicarlo, revisado contra las reglas de modelado. */
@Tag(name = "Diagnostics", description = "What a diagram gets wrong, checked against the modeling rules")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DiagnosticoController {

    /** El elemento cuyo borrado se simula, escrito TIPO:id. */
    private static final String ELEMENTO = "(POOL|LANE|ACTIVIDAD|GATEWAY|EVENTO|ARCO|MENSAJE):[0-9]+";

    private final DiagnosticoService diagnosticoService;

    @Operation(summary = "Diagnose a diagram",
            description = "Errors block publishing and warnings do not. The same diagram always answers the same "
                    + "findings, in the same order. With sinElemento it answers the diagram that would be left "
                    + "after deleting that element, which is what the editor asks before confirming a deletion. "
                    + "Any role can read it.")
    @ApiResponse(responseCode = "200", description = "The findings")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @GetMapping("/procesos/{procesoId}/diagnostico")
    public ResponseEntity<DiagnosticoResponse> diagnosticar(@PathVariable Long procesoId,
            @Parameter(description = "Simulate deleting this element, written TYPE:id", example = "GATEWAY:12")
            @RequestParam(required = false)
            @Pattern(regexp = ELEMENTO, message = "El elemento se escribe TIPO:id, por ejemplo GATEWAY:12.")
            String sinElemento,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(diagnosticoService.diagnosticar(empresaId, procesoId, sinElemento));
    }
}
