package com.facimus.procesos.ejecucion.controller;

import java.util.Objects;

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
import com.facimus.procesos.ejecucion.dto.request.CompletarTareaRequest;
import com.facimus.procesos.ejecucion.dto.request.SoltarTareaRequest;
import com.facimus.procesos.ejecucion.dto.request.TraspasoDeTareaRequest;
import com.facimus.procesos.ejecucion.dto.response.CargaDeTrabajoResponse;
import com.facimus.procesos.ejecucion.dto.response.FormularioDeTareaResponse;
import com.facimus.procesos.ejecucion.dto.response.TareaResponse;
import com.facimus.procesos.ejecucion.model.Bandeja;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.service.AsignacionDeTareaService;
import com.facimus.procesos.ejecucion.service.TareaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/** La bandeja: lo que los casos abiertos estan esperando que haga alguien. */
@Tag(name = "Tasks", description = "The trays: what open cases are waiting for someone to do, and who holds it")
@RestController
@RequestMapping("/api/v1/tareas")
@RequiredArgsConstructor
public class TareaController {

    private final TareaService tareaService;
    private final AsignacionDeTareaService asignacionDeTareaService;

    @Operation(summary = "List the tasks in a tray",
            description = "Tasks waiting for someone, oldest first. bandeja picks the tray: TODAS, the whole "
                    + "organization; MIAS, the ones the caller holds; DE_MIS_ROLES, the ones of the process roles the "
                    + "caller belongs to, whoever holds them (someone with no roles gets an empty tray); SIN_ASIGNAR, "
                    + "the ones nobody holds. mias=true is the same as bandeja=DE_MIS_ROLES. Filters by process role, "
                    + "by process and by state, to look at what has already been done. Any role.")
    @ApiResponse(responseCode = "200", description = "One page of tasks")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping
    public ResponseEntity<PageResponse<TareaResponse>> bandeja(
            @Parameter(description = "Which tray: TODAS by default, MIAS, DE_MIS_ROLES or SIN_ASIGNAR")
            @RequestParam(required = false) Bandeja bandeja,
            @Parameter(description = "Same as bandeja=DE_MIS_ROLES; bandeja wins when both are sent")
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
        Bandeja cual = Objects.requireNonNullElse(bandeja, mias ? Bandeja.DE_MIS_ROLES : Bandeja.TODAS);
        return ResponseEntity.ok(tareaService.bandeja(principal.empresaId(), principal.usuarioId(), cual,
                rolProcesoId, procesoId, estado, Paginacion.de(pagina, tamano)));
    }

    @Operation(summary = "Get the workload",
            description = "How the waiting tasks are spread: how many each person holds, the busiest first, and how "
                    + "many nobody holds. Optionally of one process. Test runs of a draft do not count. Any role.")
    @ApiResponse(responseCode = "200", description = "The workload")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping("/carga")
    public ResponseEntity<CargaDeTrabajoResponse> carga(
            @Parameter(description = "Only the tasks of cases of this process")
            @RequestParam(required = false) Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(tareaService.carga(principal.empresaId(), procesoId));
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

    @Operation(summary = "Get the form of a task",
            description = "The form frozen in the version of the task's case, with its conditions compiled for the "
                    + "web to evaluate, the value offered in each field (from the case variable its porDefecto "
                    + "names) and the case variables the conditions may read. Any role.")
    @ApiResponse(responseCode = "200", description = "The form of the task; empty when it has none")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/{id}/formulario")
    public ResponseEntity<FormularioDeTareaResponse> formulario(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(tareaService.formulario(principal.empresaId(), id));
    }

    @Operation(summary = "Complete a task",
            description = "Completes it and lets the case move on. Only its holder completes it: a task nobody "
                    + "holds is claimed by completing it, by whoever could claim it (403 otherwise), and one another "
                    + "person holds answers 409, unless the caller is an administrator and the store lets "
                    + "administrators complete on someone's behalf, which the audit trail records. Whatever data is "
                    + "handed over lands in the case "
                    + "variables under tarea.<taskNameInCamel>, so a later gateway can ask for it. When the task has "
                    + "a form, the data are its answers: they are checked against it and stored with the type of "
                    + "each field, and a wrong answer is a 400 with each field under datos. Without a form, free "
                    + "data are taken only in PRUEBA mode. A task is completed once. Administrators and editors.")
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

    @Operation(summary = "Claim a task",
            description = "Takes a task nobody holds: from then on only the caller completes it. Whoever belongs to "
                    + "the process role of its lane can claim it, and an administrator can claim any. When two "
                    + "people claim it at once, one gets it and the other gets 409. Goes to the case timeline and the "
                    + "audit trail. Administrators and editors.")
    @ApiResponse(responseCode = "200", description = "The task, held by the caller")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/{id}/reclamar")
    public ResponseEntity<TareaResponse> reclamar(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(asignacionDeTareaService.reclamar(principal.empresaId(), principal.usuarioId(), id));
    }

    @Operation(summary = "Release a task",
            description = "Its holder, or an administrator, lets it go: it goes back to the tray of its role, or, when "
                    + "the holder had it delegated, back to whoever delegated it. The reason is optional. Goes to the "
                    + "case timeline and the audit trail. Administrators and editors.")
    @ApiResponse(responseCode = "200", description = "The task, released")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/{id}/soltar")
    public ResponseEntity<TareaResponse> soltar(@PathVariable Long id,
            @Validated @RequestBody(required = false) SoltarTareaRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(asignacionDeTareaService.soltar(principal.empresaId(), principal.usuarioId(), id,
                request == null ? null : request.motivo()));
    }

    @Operation(summary = "Reassign a task",
            description = "Its holder, or an administrator, hands it to another person with a reason: an active "
                    + "administrator or editor of the organization. An administrator can also give a task nobody "
                    + "holds. An open delegation ends: the task no longer goes back to whoever delegated it. Goes to "
                    + "the case timeline and the audit trail. Administrators and editors.")
    @ApiResponse(responseCode = "200", description = "The task, with its new holder")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/{id}/reasignar")
    public ResponseEntity<TareaResponse> reasignar(@PathVariable Long id,
            @Validated @RequestBody TraspasoDeTareaRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(asignacionDeTareaService.reasignar(principal.empresaId(), principal.usuarioId(), id,
                request.usuarioId(), request.motivo()));
    }

    @Operation(summary = "Delegate a task",
            description = "Its holder hands it to another person for a while, with a reason: the delegate completes "
                    + "it, or releases it and it goes back to the holder. A delegated task is not delegated again. "
                    + "Goes to the case timeline and the audit trail. Administrators and editors.")
    @ApiResponse(responseCode = "200", description = "The task, held by the delegate")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/{id}/delegar")
    public ResponseEntity<TareaResponse> delegar(@PathVariable Long id,
            @Validated @RequestBody TraspasoDeTareaRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(asignacionDeTareaService.delegar(principal.empresaId(), principal.usuarioId(), id,
                request.usuarioId(), request.motivo()));
    }
}
