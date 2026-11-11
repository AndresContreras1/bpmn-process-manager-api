package com.facimus.procesos.modelado.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.modelado.dto.response.BloqueoResponse;
import com.facimus.procesos.modelado.service.EnVivoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/** PR 58: quien esta en un proceso y quien lo edita. */
@Tag(name = "Presence", description = "Who is looking at a process, who is editing it, and what happens meanwhile")
@RestController
@RequestMapping("/api/v1/procesos/{procesoId}")
@RequiredArgsConstructor
public class EnVivoController {

    private final EnVivoService enVivoService;

    @Operation(summary = "Follow what happens in a process",
            description = "Server-sent events: presencia, bloqueo, guardado, publicado and control-tomado, with a "
                    + "heartbeat comment every 25 seconds.")
    @ApiResponse(responseCode = "200", description = "The event stream (text/event-stream)", content = @Content)
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
    @GetMapping("/en-vivo")
    public SseEmitter enVivo(@PathVariable Long procesoId, @AuthenticationPrincipal ApiPrincipal principal,
            HttpServletResponse respuesta) {
        // Sin esto, un proxy guardaria los eventos hasta juntar un bloque
        respuesta.setHeader("X-Accel-Buffering", "no");
        return enVivoService.conectar(principal.empresaId(), principal.usuarioId(), procesoId);
    }

    @Operation(summary = "See who is editing a process")
    @ApiResponse(responseCode = "200", description = "The lock")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/bloqueo")
    public ResponseEntity<BloqueoResponse> estado(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(enVivoService.estado(principal.empresaId(), principal.usuarioId(), procesoId));
    }

    @Operation(summary = "Start editing a process",
            description = "The first who edits has it, and loses it after 60 seconds without a heartbeat. If someone "
                    + "else has it, the answer says who.")
    @ApiResponse(responseCode = "200", description = "The lock")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @PostMapping("/bloqueo")
    public ResponseEntity<BloqueoResponse> adquirir(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(enVivoService.adquirir(principal.empresaId(), principal.usuarioId(), procesoId));
    }

    @Operation(summary = "Keep editing", description = "Answers 409 if someone took control.")
    @ApiResponse(responseCode = "200", description = "The lock")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/bloqueo/latido")
    public ResponseEntity<BloqueoResponse> latir(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(enVivoService.latir(principal.empresaId(), principal.usuarioId(), procesoId));
    }

    @Operation(summary = "Stop editing")
    @ApiResponse(responseCode = "200", description = "The lock")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @PostMapping("/bloqueo/soltar")
    public ResponseEntity<BloqueoResponse> soltar(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(enVivoService.soltar(principal.empresaId(), principal.usuarioId(), procesoId));
    }

    @Operation(summary = "Take control from whoever is editing",
            description = "It stays in the audit log, and the previous editor is told.")
    @ApiResponse(responseCode = "200", description = "The lock")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @PostMapping("/bloqueo/tomar-control")
    public ResponseEntity<BloqueoResponse> tomarControl(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(enVivoService.tomarControl(principal.empresaId(), principal.usuarioId(), procesoId));
    }
}
