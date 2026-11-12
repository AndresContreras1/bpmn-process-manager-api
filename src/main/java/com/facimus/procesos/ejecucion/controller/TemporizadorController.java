package com.facimus.procesos.ejecucion.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.ejecucion.dto.response.TemporizadorResponse;
import com.facimus.procesos.ejecucion.service.TemporizadorService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * PR 68: los temporizadores, para mirar cuando salta cada uno. Los arma el motor con lo que el diagrama dice desde el
 * PR 55 (tipo, expresion, actividad y si interrumpe): aqui solo se leen.
 */
@Tag(name = "Timers", description = "Timers armed by cases and timer starts of processes: when each one fires")
@RestController
@RequiredArgsConstructor
public class TemporizadorController {

    private final TemporizadorService temporizadorService;

    @Operation(summary = "List the timers of a case",
            description = "Waits, boundary timers of the activities it waited on, in the order they were armed, with "
                    + "when they are due and whether they fired. Any role.")
    @ApiResponse(responseCode = "200", description = "The timers")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/api/v1/casos/{id}/temporizadores")
    public ResponseEntity<List<TemporizadorResponse>> delCaso(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(temporizadorService.delCaso(principal.empresaId(), id));
    }

    @Operation(summary = "List the timer starts of a process",
            description = "The timers that open cases on their own: the one armed for the version in force, and the "
                    + "ones of earlier versions, cancelled, newest first. Any role that can see the process.")
    @ApiResponse(responseCode = "200", description = "The timer starts")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/api/v1/procesos/{procesoId}/temporizadores")
    public ResponseEntity<List<TemporizadorResponse>> delProceso(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(temporizadorService.delProceso(principal.empresaId(), procesoId));
    }
}
