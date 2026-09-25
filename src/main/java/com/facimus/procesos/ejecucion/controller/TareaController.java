package com.facimus.procesos.ejecucion.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.ejecucion.dto.request.AsignarTareaRequest;
import com.facimus.procesos.ejecucion.dto.request.CompletarTareaRequest;
import com.facimus.procesos.ejecucion.dto.response.TareaResponse;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.service.TareaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/** La bandeja: lo que los casos abiertos estan esperando que haga alguien. */
@Tag(name = "Tasks", description = "The tray of a process role: what open cases are waiting for someone to do")
@RestController
@RequestMapping("/api/v1/tareas")
@RequiredArgsConstructor
public class TareaController {

    private final TareaService tareaService;

    @Operation(summary = "List the tasks in the tray",
            description = "Tasks waiting for someone, oldest first. Filters by process role, by process and by "
                    + "state, to look at what has already been done. With mias=true, only the tasks of the process "
                    + "roles the caller belongs to; someone with no roles gets an empty tray. Any role.")
    @ApiResponse(responseCode = "200", description = "One page of tasks")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping
    public ResponseEntity<PageResponse<TareaResponse>> bandeja(
            @Parameter(description = "Only the tasks of the process roles the caller belongs to")
            @RequestParam(defaultValue = "false") boolean mias,
            @Parameter(description = "Only the tasks of this process role")
            @RequestParam(required = false) Long rolProcesoId,
            @Parameter(description = "Only the tasks of cases of this process")
            @RequestParam(required = false) Long procesoId,
            @Parameter(description = "Which tasks to show; waiting ones by default")
            @RequestParam(required = false) EstadoActividadCaso estado,
            @Parameter(description = "Page number, starting at 0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGINA_INVALIDA) int pagina,
            @Parameter(description = "Items per page, from 1 to 50")
            @RequestParam(defaultValue = "10") @Min(value = 1, message = Paginacion.TAMANO_INVALIDO)
            @Max(value = Paginacion.TAMANO_MAXIMO, message = Paginacion.TAMANO_INVALIDO) int tamano,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(tareaService.bandeja(principal.empresaId(), principal.usuarioId(), mias,
                rolProcesoId, procesoId, estado, Paginacion.de(pagina, tamano)));
    }

    @Operation(summary = "Get a task", description = "With the case and the process it belongs to. Any role.")
    @ApiResponse(responseCode = "200", description = "The task")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/{id}")
    public ResponseEntity<TareaResponse> obtener(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(tareaService.obtener(principal.empresaId(), id));
    }

    @Operation(summary = "Complete a task",
            description = "Completes it and lets the case move on. Whatever data is handed over lands in the case "
                    + "variables under tarea.<taskNameInCamel>, so a later gateway can ask for it. A task is "
                    + "completed once. Administrators and editors.")
    @ApiResponse(responseCode = "200", description = "The task, now completed")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/{id}/completar")
    public ResponseEntity<TareaResponse> completar(@PathVariable Long id,
            @Validated @RequestBody CompletarTareaRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(tareaService.completar(principal.empresaId(), principal.usuarioId(), id,
                request.datos()));
    }

    @Operation(summary = "Take a task, or leave it free again",
            description = "A note for the team: the whole role keeps seeing it in the tray and completing it does "
                    + "not require having taken it. Administrators and editors.")
    @ApiResponse(responseCode = "200", description = "The task, with whoever took it")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/{id}/asignar")
    public ResponseEntity<TareaResponse> asignar(@PathVariable Long id,
            @Validated @RequestBody AsignarTareaRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(tareaService.asignar(principal.empresaId(), id, request.usuarioId()));
    }
}
