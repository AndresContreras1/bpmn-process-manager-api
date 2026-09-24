package com.facimus.procesos.modelado.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.modelado.dto.request.EditarGatewayRequest;
import com.facimus.procesos.modelado.dto.request.GatewayRequest;
import com.facimus.procesos.modelado.dto.response.GatewayResponse;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.service.GatewayService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** HU-14 a HU-16: gateways (puntos de decision). */
@Tag(name = "Gateways", description = "Decision and split points of the process, placed in a lane")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GatewayController {

    private final GatewayService gatewayService;

    @Operation(summary = "Add a gateway to a lane")
    @ApiResponse(responseCode = "201", description = "Gateway created",
            headers = @Header(name = HttpHeaders.LOCATION, description = "URL of the new gateway"))
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/lanes/{laneId}/gateways")
    public ResponseEntity<GatewayResponse> crear(@PathVariable Long laneId,
            @Validated @RequestBody GatewayRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        GatewayResponse gateway = gatewayService.crear(empresaId, principal.usuarioId(), laneId, request.nombre(),
                request.tipoGateway(), request.posicionX(), request.posicionY());
        return ResponseEntity.created(URI.create("/api/v1/gateways/" + gateway.id())).body(gateway);
    }

    @Operation(summary = "Get a gateway")
    @ApiResponse(responseCode = "200", description = "The gateway")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/gateways/{id}")
    public ResponseEntity<GatewayResponse> detalle(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(gatewayService.obtener(empresaId, id));
    }

    @Operation(summary = "List the gateways of a lane")
    @ApiResponse(responseCode = "200", description = "Gateways of the lane")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/lanes/{laneId}/gateways")
    public ResponseEntity<List<GatewayResponse>> listar(@PathVariable Long laneId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(gatewayService.listarPorLane(empresaId, laneId));
    }

    @Operation(summary = "Edit a gateway", description = "Replaces its name, type and position.")
    @ApiResponse(responseCode = "200", description = "Gateway updated")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/gateways/{id}")
    public ResponseEntity<GatewayResponse> editar(@PathVariable Long id,
            @Validated @RequestBody EditarGatewayRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(gatewayService.editar(empresaId, principal.usuarioId(), id, request.nombre(),
                request.tipoGateway(), request.laneId(), request.posicionX(), request.posicionY(),
                request.version()));
    }

    @Operation(summary = "Delete a gateway",
            description = "Also deletes the sequence flows that start or end at it. Administrators only.")
    @ApiResponse(responseCode = "204", description = "Gateway deleted")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @DeleteMapping("/gateways/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        gatewayService.eliminar(empresaId, principal.usuarioId(), id);
        return ResponseEntity.noContent().build();
    }
}
