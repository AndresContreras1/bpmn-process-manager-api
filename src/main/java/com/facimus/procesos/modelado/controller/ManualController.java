package com.facimus.procesos.modelado.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.modelado.dto.response.EnlaceDeDescargaResponse;
import com.facimus.procesos.modelado.dto.response.ManualResponse;
import com.facimus.procesos.modelado.service.ManualService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** PR 62: el manual de una version publicada, armado en la cola y bajado con un enlace firmado. */
@Tag(name = "Documentation")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ManualController {

    private final ManualService manualService;

    @Operation(summary = "Ask for the manual of a published version",
            description = "The version in force, or the one in version. The job queue builds it in the language of "
                    + "whoever asks, as HTML and PDF: the sheet, the drawing the web app sent when publishing, the "
                    + "documentation of the process and of each step lane by lane, and the documents available.")
    @ApiResponse(responseCode = "202", description = "Manual asked for; it starts PENDIENTE",
            headers = @Header(name = "Location", description = "URL of the manual"))
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/procesos/{procesoId}/manuales")
    public ResponseEntity<ManualResponse> pedir(@PathVariable Long procesoId,
            @Parameter(description = "Version number; the one in force if left out", example = "3")
            @RequestParam(required = false) Integer version,
            @AuthenticationPrincipal ApiPrincipal principal) {
        ManualResponse manual = manualService.pedir(principal.empresaId(), principal.usuarioId(), procesoId, version);
        return ResponseEntity.accepted().location(URI.create("/api/v1/manuales/" + manual.id())).body(manual);
    }

    @Operation(summary = "List the manuals of a process", description = "Newest first.")
    @ApiResponse(responseCode = "200", description = "The manuals")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/procesos/{procesoId}/manuales")
    public ResponseEntity<List<ManualResponse>> listar(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(manualService.listar(principal.empresaId(), procesoId));
    }

    @Operation(summary = "Get a manual and whether it is ready")
    @ApiResponse(responseCode = "200", description = "The manual")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/manuales/{id}")
    public ResponseEntity<ManualResponse> obtener(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(manualService.obtener(principal.empresaId(), id));
    }

    @Operation(summary = "Get a link to download a manual",
            description = "A link that works without a session for five minutes, for the PDF or the HTML. A manual "
                    + "still being built, or one that failed, has no link.")
    @ApiResponse(responseCode = "200", description = "The signed link and when it expires")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @GetMapping("/manuales/{id}/enlace")
    public ResponseEntity<EnlaceDeDescargaResponse> enlace(@PathVariable Long id,
            @Parameter(description = "pdf or html", example = "pdf") @RequestParam(defaultValue = "pdf")
            String formato, @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(manualService.enlace(principal.empresaId(), id, formato));
    }
}
