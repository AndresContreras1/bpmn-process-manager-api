package com.facimus.procesos.modelado.controller;

import java.io.InputStream;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.modelado.dto.response.ComparacionResponse;
import com.facimus.procesos.modelado.service.ComparacionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** PR 57: que cambia entre dos estados de un proceso. */
@Tag(name = "Diagrams", description = "A whole BPMN diagram in one request, ready to draw")
@RestController
@RequestMapping("/api/v1/procesos/{procesoId}/comparar")
@RequiredArgsConstructor
public class ComparacionController {

    private final ComparacionService comparacionService;

    @Operation(summary = "Compare two states of a process",
            description = "Each side is a version number, vigente or borrador. Answers what was added, removed, "
                    + "changed (and which fields) and only moved, with the bpmnIds to paint it.")
    @ApiResponse(responseCode = "200", description = "The differences")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @GetMapping
    public ResponseEntity<ComparacionResponse> comparar(@PathVariable Long procesoId,
            @RequestParam(defaultValue = "vigente") String de, @RequestParam(defaultValue = "borrador") String a,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(comparacionService.comparar(principal.empresaId(), procesoId, de, a));
    }

    @Operation(summary = "Compare a BPMN file with the draft, without saving it",
            description = "What the file would change: what someone sees when saving answers 412.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_XML_VALUE,
                            schema = @Schema(type = "string", description = "A BPMN 2.0 document"))))
    @ApiResponse(responseCode = "200", description = "The differences")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping(consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE, "application/bpmn+xml",
        MediaType.APPLICATION_OCTET_STREAM_VALUE})
    public ResponseEntity<ComparacionResponse> compararArchivo(@PathVariable Long procesoId, InputStream archivo,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(comparacionService.compararArchivo(principal.empresaId(), principal.usuarioId(),
                procesoId, archivo));
    }
}
