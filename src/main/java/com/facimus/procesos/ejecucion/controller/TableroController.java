package com.facimus.procesos.ejecucion.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.ejecucion.dto.response.TableroResponse;
import com.facimus.procesos.ejecucion.service.TableroService;
import com.facimus.procesos.gestion.service.Actor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Como va la operacion: cuantos pedidos hay, cuanto tardan, quien tiene trabajo y que no salio bien. */
@Tag(name = "Dashboard", description = "How operations are going, for one process or for the whole store")
@RestController
@RequiredArgsConstructor
public class TableroController {

    private final TableroService tableroService;

    @Operation(summary = "The dashboard of one process",
            description = "Cases by state, how long a finished order takes in ticks, the work waiting in each "
                    + "role's tray, the messages sent and received, what did not go as expected, and how deadlines "
                    + "were kept. Any role that can see the process.")
    @ApiResponse(responseCode = "200", description = "The dashboard of the process")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/api/v1/procesos/{procesoId}/tablero")
    public ResponseEntity<TableroResponse> deUnProceso(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(tableroService.de(quien(principal), procesoId));
    }

    @Operation(summary = "The dashboard of the whole store",
            description = "The same numbers, over every process of the store at once. Any role; the deadline "
                    + "compliance leaves out the processes the caller cannot see.")
    @ApiResponse(responseCode = "200", description = "The dashboard of the store")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping("/api/v1/empresas/actual/tablero")
    public ResponseEntity<TableroResponse> deLaTienda(@AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(tableroService.de(quien(principal), null));
    }

    private static Actor quien(ApiPrincipal principal) {
        return new Actor(principal.usuarioId(), principal.empresaId(), principal.rol());
    }
}
