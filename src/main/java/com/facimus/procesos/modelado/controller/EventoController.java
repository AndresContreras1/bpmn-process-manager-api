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

import com.facimus.procesos.modelado.dto.request.EditarEventoRequest;
import com.facimus.procesos.modelado.dto.request.EventoRequest;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** HU-04 y HU-27: eventos de inicio, de fin y de mensaje. */
@Tag(name = "Events", description = "Start, end and message events of the process, placed in a lane")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EventoController {

    private final EventoService eventoService;

    @Operation(summary = "Add an event to a lane")
    @ApiResponse(responseCode = "201", description = "Event created",
            headers = @Header(name = HttpHeaders.LOCATION, description = "URL of the new event"))
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/lanes/{laneId}/eventos")
    public ResponseEntity<EventoResponse> crear(@PathVariable Long laneId,
            @Validated @RequestBody EventoRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        EventoResponse evento = eventoService.crear(empresaId, principal.usuarioId(), laneId, request.nombre(),
                request.tipoEvento(), request.posicionX(), request.posicionY());
        return ResponseEntity.created(URI.create("/api/v1/eventos/" + evento.id())).body(evento);
    }

    @Operation(summary = "Get an event")
    @ApiResponse(responseCode = "200", description = "The event")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/eventos/{id}")
    public ResponseEntity<EventoResponse> detalle(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(eventoService.obtener(empresaId, id));
    }

    @Operation(summary = "List the events of a lane")
    @ApiResponse(responseCode = "200", description = "Events of the lane")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/lanes/{laneId}/eventos")
    public ResponseEntity<List<EventoResponse>> listar(@PathVariable Long laneId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(eventoService.listarPorLane(empresaId, laneId));
    }

    @Operation(summary = "Edit an event", description = "Replaces its name, type and position.")
    @ApiResponse(responseCode = "200", description = "Event updated")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/eventos/{id}")
    public ResponseEntity<EventoResponse> editar(@PathVariable Long id,
            @Validated @RequestBody EditarEventoRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(eventoService.editar(empresaId, principal.usuarioId(), id, request.nombre(),
                request.tipoEvento(), request.laneId(), request.posicionX(), request.posicionY(),
                request.version()));
    }

    @Operation(summary = "Delete an event",
            description = "Also deletes the sequence flows that start or end at it. Administrators only.")
    @ApiResponse(responseCode = "204", description = "Event deleted")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @DeleteMapping("/eventos/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        eventoService.eliminar(empresaId, principal.usuarioId(), id);
        return ResponseEntity.noContent().build();
    }
}
