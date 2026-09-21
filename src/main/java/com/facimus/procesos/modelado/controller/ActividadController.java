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

import com.facimus.procesos.modelado.dto.request.ActividadRequest;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** HU-08 a HU-10: actividades (tareas del proceso). */
@Tag(name = "Activities", description = "Tasks of the process, placed in a lane")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ActividadController {

    private final ActividadService actividadService;

    @Operation(summary = "Add an activity to a lane")
    @ApiResponse(responseCode = "201", description = "Activity created",
            headers = @Header(name = HttpHeaders.LOCATION, description = "URL of the new activity"))
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/lanes/{laneId}/actividades")
    public ResponseEntity<ActividadResponse> crear(@PathVariable Long laneId,
            @Validated @RequestBody ActividadRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        ActividadResponse actividad = actividadService.crear(empresaId, laneId, request.nombre(), request.descripcion(),
                request.posicionX(), request.posicionY());
        return ResponseEntity.created(URI.create("/api/v1/actividades/" + actividad.id())).body(actividad);
    }

    @Operation(summary = "Get an activity")
    @ApiResponse(responseCode = "200", description = "The activity")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/actividades/{id}")
    public ResponseEntity<ActividadResponse> detalle(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(actividadService.obtener(empresaId, id));
    }

    @Operation(summary = "List the activities of a lane")
    @ApiResponse(responseCode = "200", description = "Activities of the lane")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/lanes/{laneId}/actividades")
    public ResponseEntity<List<ActividadResponse>> listar(@PathVariable Long laneId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(actividadService.listarPorLane(empresaId, laneId));
    }

    @Operation(summary = "Edit an activity", description = "Replaces its name, description and position.")
    @ApiResponse(responseCode = "200", description = "Activity updated")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @PutMapping("/actividades/{id}")
    public ResponseEntity<ActividadResponse> editar(@PathVariable Long id,
            @Validated @RequestBody ActividadRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(actividadService.editar(empresaId, id, request.nombre(), request.descripcion(),
                request.posicionX(), request.posicionY()));
    }

    @Operation(summary = "Delete an activity",
            description = "Also deletes the sequence flows that start or end at it. Administrators only.")
    @ApiResponse(responseCode = "204", description = "Activity deleted")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @DeleteMapping("/actividades/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        actividadService.eliminar(empresaId, id);
        return ResponseEntity.noContent().build();
    }
}
