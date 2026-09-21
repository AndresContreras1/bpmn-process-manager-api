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

import com.facimus.procesos.modelado.dto.request.LaneRequest;
import com.facimus.procesos.modelado.dto.response.LaneResponse;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** HU-22 y HU-24: lanes (divisiones internas de un pool). */
@Tag(name = "Lanes", description = "Divisions of a pool, each one assigned to a process role")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LaneController {

    private final LaneService laneService;

    @Operation(summary = "List the lanes of a pool", description = "In diagram order.")
    @ApiResponse(responseCode = "200", description = "Lanes of the pool")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/pools/{poolId}/lanes")
    public ResponseEntity<List<LaneResponse>> listar(@PathVariable Long poolId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        List<LaneResponse> lanes = laneService.listarPorPool(empresaId, poolId).stream()
                .map(LaneResponse::of)
                .toList();
        return ResponseEntity.ok(lanes);
    }

    @Operation(summary = "Get a lane")
    @ApiResponse(responseCode = "200", description = "The lane")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/lanes/{id}")
    public ResponseEntity<LaneResponse> detalle(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Lane lane = laneService.obtener(empresaId, id);
        return ResponseEntity.ok(LaneResponse.of(lane));
    }

    @Operation(summary = "Add a lane to a pool", description = "The lane goes after the existing ones.")
    @ApiResponse(responseCode = "201", description = "Lane created",
            headers = @Header(name = HttpHeaders.LOCATION, description = "URL of the new lane"))
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @PostMapping("/pools/{poolId}/lanes")
    public ResponseEntity<LaneResponse> crear(@PathVariable Long poolId,
            @Validated @RequestBody LaneRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Lane lane = laneService.crear(empresaId, poolId, request.nombre(), request.rolProcesoId());
        return ResponseEntity.created(URI.create("/api/v1/lanes/" + lane.getId()))
                .body(LaneResponse.of(lane));
    }

    @Operation(summary = "Edit a lane", description = "Replaces its name and process role.")
    @ApiResponse(responseCode = "200", description = "Lane updated")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @PutMapping("/lanes/{id}")
    public ResponseEntity<LaneResponse> editar(@PathVariable Long id,
            @Validated @RequestBody LaneRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Lane lane = laneService.editar(empresaId, id, request.nombre(), request.rolProcesoId());
        return ResponseEntity.ok(LaneResponse.of(lane));
    }

    @Operation(summary = "Delete a lane",
            description = "Only an empty lane can be deleted. Administrators only.")
    @ApiResponse(responseCode = "204", description = "Lane deleted")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @DeleteMapping("/lanes/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        laneService.eliminar(empresaId, id);
        return ResponseEntity.noContent().build();
    }
}
