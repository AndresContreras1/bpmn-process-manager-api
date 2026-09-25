package com.facimus.procesos.ejecucion.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.ejecucion.dto.request.TickRequest;
import com.facimus.procesos.ejecucion.dto.response.PanelDeSimulacionResponse;
import com.facimus.procesos.ejecucion.service.SimulacionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** El reloj de la tienda: donde esta su simulacion y como se mueve. */
@Tag(name = "Simulation", description = "The store's clock: where its simulation is and how it moves")
@RestController
@RequiredArgsConstructor
public class SimulacionController {

    private final SimulacionService simulacionService;

    @Operation(summary = "Where the store's simulation is",
            description = "The clock, who moves it, and what is still in the trays: messages sent and not "
                    + "delivered yet, grouped by the kind of partner waiting for them, and messages that arrived "
                    + "before anyone expected them. Administrators only.")
    @ApiResponse(responseCode = "200", description = "The simulation dashboard")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @GetMapping("/api/v1/simulacion")
    public ResponseEntity<PanelDeSimulacionResponse> panel(@AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(simulacionService.panel(principal.empresaId()));
    }

    @Operation(summary = "Move the store's clock",
            description = "Advances the clock and delivers what is now due: each message goes to the simulated "
                    + "partner of its participant, which decides whether it arrived and what it answers, and the "
                    + "cases move on. Every message is delivered in its own transaction, so twenty orders move one "
                    + "after another. Administrators only.")
    @ApiResponse(responseCode = "200", description = "The dashboard after moving the clock")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/api/v1/simulacion/tick")
    public ResponseEntity<PanelDeSimulacionResponse> tick(@Validated @RequestBody TickRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(simulacionService.tick(principal.empresaId(), request.ticks()));
    }
}
