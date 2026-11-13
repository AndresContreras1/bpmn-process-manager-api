package com.facimus.procesos.gestion.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.gestion.dto.request.PoliticaDePublicacionRequest;
import com.facimus.procesos.gestion.dto.response.PoliticaDePublicacionResponse;
import com.facimus.procesos.gestion.service.DatosDePolitica;
import com.facimus.procesos.gestion.service.PoliticaDePublicacionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** PR 64: como publica la tienda: directo o con aprobacion (D32). */
@Tag(name = "Review and approval", description = "Publishing through a review: the store's policy, the requests to "
        + "approve and the periodic recertification of each process")
@RestController
@RequestMapping("/api/v1/empresas/actual/politica-de-publicacion")
@RequiredArgsConstructor
public class PoliticaDePublicacionController {

    private final PoliticaDePublicacionService politicaService;

    @Operation(summary = "Get how the store publishes",
            description = "DIRECTA unless the store chose otherwise. Any role can read it: the web decides with it "
                    + "whether to offer publishing or asking for a review.")
    @ApiResponse(responseCode = "200", description = "The policy")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping
    public ResponseEntity<PoliticaDePublicacionResponse> obtener(@AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(politicaService.obtener(principal.empresaId()));
    }

    @Operation(summary = "Change how the store publishes",
            description = "With CON_APROBACION, publishing directly answers 409 for everyone, administrators "
                    + "included, and a process is published by asking for a review that N other people approve. "
                    + "They come from a list of people or from a process role, never both, and there have to be at "
                    + "least N of them. Requests already open keep the rules they were opened with. Administrators "
                    + "only; it goes to the audit trail.")
    @ApiResponse(responseCode = "200", description = "The policy, as saved")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping
    public ResponseEntity<PoliticaDePublicacionResponse> guardar(
            @Validated @RequestBody PoliticaDePublicacionRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(politicaService.guardar(principal.empresaId(), principal.usuarioId(),
                new DatosDePolitica(request.modo(), request.aprobacionesNecesarias(), request.rolProcesoId(),
                        request.aprobadores(), request.version())));
    }
}
