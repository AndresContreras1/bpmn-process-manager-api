package com.facimus.procesos.modelado.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.modelado.dto.request.EscalamientoRequest;
import com.facimus.procesos.modelado.dto.request.PlazoRequest;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.service.PlazoDelNodoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** PR 71: el plazo de una tarea o de un caso y el escalamiento de un temporizador de borde, en el borrador. */
@Tag(name = "Deadlines", description = "Deadlines of user tasks and cases, and what a boundary timer escalates")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PlazoController {

    private static final String CUENTA = " Hours are wall-clock hours; business days are counted with the "
            + "organization's calendar, without weekends or holidays. The task is due soon in the last quarter of its "
            + "deadline, and its holder and the process owner are told then and when it is overdue. It is frozen "
            + "with the version when it is published. Administrators and editors.";

    private final PlazoDelNodoService plazoDelNodoService;

    @Operation(summary = "Save the deadline of a user task",
            description = "From the moment a case reaches the task. Without cantidad and unidad the task has none."
                    + CUENTA)
    @ApiResponse(responseCode = "200", description = "The activity, with its deadline")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/actividades/{id}/plazo")
    public ResponseEntity<ActividadResponse> deLaActividad(@PathVariable Long id,
            @Validated @RequestBody PlazoRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(plazoDelNodoService.deLaActividad(principal.empresaId(), principal.usuarioId(), id,
                request.cantidad(), request.unidad(), request.version()));
    }

    @Operation(summary = "Save the deadline of the whole case on a start event",
            description = "From the moment the case opens through that event. Without cantidad and unidad the case "
                    + "has none." + CUENTA)
    @ApiResponse(responseCode = "200", description = "The event, with the deadline of its cases")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/eventos/{id}/plazo")
    public ResponseEntity<EventoResponse> delEvento(@PathVariable Long id,
            @Validated @RequestBody PlazoRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(plazoDelNodoService.delEvento(principal.empresaId(), principal.usuarioId(), id,
                request.cantidad(), request.unidad(), request.version()));
    }

    @Operation(summary = "Save what a non-interrupting boundary timer escalates",
            description = "When the timer fires on its task, besides opening its path: AVISAR notifies the holder "
                    + "and the supervisor, REASIGNAR hands the task to the supervisor (only on a user task). The "
                    + "supervisor is usuarioId, an active administrator or editor, or the process owner when it is "
                    + "empty. Without accion the timer only opens its path. An interrupting timer already takes "
                    + "the task out by its own path (409). Administrators and editors.")
    @ApiResponse(responseCode = "200", description = "The event, with its escalation")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/eventos/{id}/escalamiento")
    public ResponseEntity<EventoResponse> escalamiento(@PathVariable Long id,
            @Validated @RequestBody EscalamientoRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(plazoDelNodoService.escalamiento(principal.empresaId(), principal.usuarioId(), id,
                request.accion(), request.usuarioId(), request.version()));
    }
}
