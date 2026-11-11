package com.facimus.procesos.modelado.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.api.Descargas;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.modelado.dto.response.ImportacionResponse;
import com.facimus.procesos.modelado.dto.response.RevisionBorradorResponse;
import com.facimus.procesos.modelado.model.MotivoDeRevision;
import com.facimus.procesos.modelado.service.GuardadoDelModeloService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** PR 40: los puntos de guardado del borrador. */
@Tag(name = "Draft revisions", description = "Save points of a draft to go back to; a revision is not a version")
@RestController
@RequestMapping("/api/v1/procesos/{procesoId}/revisiones")
@RequiredArgsConstructor
public class RevisionBorradorController {

    private final GuardadoDelModeloService guardadoDelModeloService;

    @Operation(summary = "List the save points of a draft", description = "Newest first.")
    @ApiResponse(responseCode = "200", description = "The save points")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping
    public ResponseEntity<List<RevisionBorradorResponse>> listar(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(guardadoDelModeloService.revisiones(principal.empresaId(), procesoId));
    }

    @Operation(summary = "Make a save point now", description = "What the editor calls when closing. If the draft "
            + "did not change since the last one, it answers that one.")
    @ApiResponse(responseCode = "200", description = "The save point")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @PostMapping
    public ResponseEntity<RevisionBorradorResponse> crear(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(guardadoDelModeloService.puntoDeGuardado(principal.empresaId(), principal.usuarioId(),
                procesoId, MotivoDeRevision.CIERRE));
    }

    @Operation(summary = "Download a save point as BPMN")
    @ApiResponse(responseCode = "200", description = "The BPMN file (application/xml)")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/{revisionId}/modelo")
    public ResponseEntity<byte[]> modelo(@PathVariable Long procesoId, @PathVariable Long revisionId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return Descargas.xml("revision-" + revisionId + ".bpmn",
                guardadoDelModeloService.modelo(principal.empresaId(), procesoId, revisionId));
    }

    @Operation(summary = "Go back to a save point",
            description = "The draft becomes what it was; what it is now stays in another save point. The draft's "
                    + "fingerprint is the save point's again. Follows the permissions of importing a file.")
    @ApiResponse(responseCode = "200", description = "What changed")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/{revisionId}/restaurar")
    public ResponseEntity<ImportacionResponse> restaurar(@PathVariable Long procesoId, @PathVariable Long revisionId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        ImportacionResponse restaurado = guardadoDelModeloService.restaurar(principal.empresaId(),
                principal.usuarioId(), principal.rol(), procesoId, revisionId);
        return ResponseEntity.ok().eTag(restaurado.huella()).body(restaurado);
    }
}
