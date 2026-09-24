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

import com.facimus.procesos.modelado.dto.request.EditarLaneRequest;
import com.facimus.procesos.modelado.dto.request.OrdenRequest;
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
        return ResponseEntity.ok(laneService.listarPorPool(empresaId, poolId));
    }

    @Operation(summary = "Get a lane")
    @ApiResponse(responseCode = "200", description = "The lane")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/lanes/{id}")
    public ResponseEntity<LaneResponse> detalle(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(laneService.obtener(empresaId, id));
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
        LaneResponse lane = laneService.crear(empresaId, principal.usuarioId(), poolId, request.nombre(),
                request.rolProcesoId());
        return ResponseEntity.created(URI.create("/api/v1/lanes/" + lane.id())).body(lane);
    }

    @Operation(summary = "Edit a lane", description = "Replaces its name and process role.")
    @ApiResponse(responseCode = "200", description = "Lane updated")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/lanes/{id}")
    public ResponseEntity<LaneResponse> editar(@PathVariable Long id,
            @Validated @RequestBody EditarLaneRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(laneService.editar(empresaId, principal.usuarioId(), id, request.nombre(),
                request.rolProcesoId(), request.version()));
    }

    @Operation(summary = "Reorder the lanes of a pool",
            description = "The body carries every lane of the pool, exactly once, in the order they should be "
                    + "drawn. Sending the whole list is what lets the editor save a drag without the server having "
                    + "to guess what happened to the rest.")
    @ApiResponse(responseCode = "200", description = "Lanes of the pool, in their new order")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/pools/{poolId}/lanes/orden")
    public ResponseEntity<List<LaneResponse>> reordenar(@PathVariable Long poolId,
            @Validated @RequestBody OrdenRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(laneService.reordenar(empresaId, principal.usuarioId(), poolId, request.ids()));
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
        laneService.eliminar(empresaId, principal.usuarioId(), id);
        return ResponseEntity.noContent().build();
    }
}
