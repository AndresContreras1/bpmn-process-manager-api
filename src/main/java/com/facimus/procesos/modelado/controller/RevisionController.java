package com.facimus.procesos.modelado.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.modelado.dto.response.RevisionResponse;
import com.facimus.procesos.modelado.service.RevisionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Una segunda opinion sobre el diagrama de un proceso, pedida a un modelo de lenguaje. */
@Tag(name = "Reviews", description = "A second opinion on a diagram, asked of a language model")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RevisionController {

    private final RevisionService revisionService;

    @Operation(summary = "Review the diagram of a process",
            description = "Sends the diagram to the reviewer and answers with its findings, each one with a "
                    + "severity, the element it is about and a suggestion. It is advice: nothing is changed. "
                    + "Administrators and editors can ask for it, because every review costs a call.")
    @ApiResponse(responseCode = "200", description = "The review")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
    @ApiResponse(responseCode = "502", ref = "BadGateway")
    @ApiResponse(responseCode = "503", ref = "ServiceUnavailable")
    @PostMapping("/procesos/{procesoId}/revision")
    public ResponseEntity<RevisionResponse> revisar(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(revisionService.revisar(empresaId, procesoId));
    }
}
