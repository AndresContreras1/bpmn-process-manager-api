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
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.modelado.dto.request.ModoProcesoRequest;
import com.facimus.procesos.modelado.dto.response.ModoProcesoResponse;
import com.facimus.procesos.modelado.service.DiagnosticoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** PR 55: si un proceso se ejecuta o solo se documenta. */
@Tag(name = "Processes")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ModoProcesoController {

    private final ProcesoService procesoService;
    private final DiagnosticoService diagnosticoService;

    @Operation(summary = "Change whether a process runs or is only documented",
            description = "A DOCUMENTAL process is modelled, published and documented, but opens no cases, and its "
                    + "diagnosis only checks the structure. The mode is part of the model: it takes effect when the "
                    + "process is published again. Answers the process and the diagnosis of the new mode.")
    @ApiResponse(responseCode = "200", description = "The process in its new mode, and its diagnosis")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/procesos/{procesoId}/modo")
    public ResponseEntity<ModoProcesoResponse> cambiar(@PathVariable Long procesoId,
            @Validated @RequestBody ModoProcesoRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        ProcesoResponse proceso = procesoService.cambiarModo(empresaId, procesoId, principal.usuarioId(),
                request.modo(), request.version());
        return ResponseEntity.ok(new ModoProcesoResponse(proceso,
                diagnosticoService.diagnosticar(empresaId, procesoId, null)));
    }
}
