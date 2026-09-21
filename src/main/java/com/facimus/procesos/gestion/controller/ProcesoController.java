package com.facimus.procesos.gestion.controller;

import java.net.URI;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.dto.request.CambiarEstadoProcesoRequest;
import com.facimus.procesos.gestion.dto.request.EditarProcesoRequest;
import com.facimus.procesos.gestion.dto.response.HistorialCambioResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoDetalleResponse;
import com.facimus.procesos.gestion.dto.request.ProcesoRequest;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/** HU-04 a HU-07: creacion, edicion, eliminacion logica y consulta de procesos. */
@Tag(name = "Processes", description = "The store's BPMN processes: drafts, publication, soft delete and change history")
@RestController
@RequestMapping("/api/v1/procesos")
@RequiredArgsConstructor
public class ProcesoController {

    private static final int TAMANO_PAGINA = 10;

    private final ProcesoService procesoService;
    private final HistorialCambioService historialCambioService;

    @Operation(summary = "List processes",
            description = "Pages of 10 processes, most recently modified first. The filters are optional and can be "
                    + "combined.")
    @ApiResponse(responseCode = "200", description = "One page of processes")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping
    public ResponseEntity<PageResponse<ProcesoResponse>> listar(
            @Parameter(description = "Part of the name, case-insensitive", example = "order")
            @RequestParam(required = false) String nombre,
            @Parameter(description = "Only processes in this state")
            @RequestParam(required = false) EstadoProceso estado,
            @Parameter(description = "Exact category", example = "Fulfillment")
            @RequestParam(required = false) String categoria,
            @Parameter(description = "Page number, starting at 0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "La página no puede ser negativa.") int pagina,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Page<ProcesoResponse> procesos = procesoService.buscar(empresaId, nombre, estado, categoria,
                        PageRequest.of(pagina, TAMANO_PAGINA, Sort.by("fechaModificacion").descending()))
                .map(ProcesoResponse::of);
        return ResponseEntity.ok(PageResponse.from(procesos));
    }

    @Operation(summary = "Create a process",
            description = "The process starts as a draft, with a pool for the store itself.")
    @ApiResponse(responseCode = "201", description = "Process created",
            headers = @Header(name = HttpHeaders.LOCATION, description = "URL of the new process"))
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping
    public ResponseEntity<ProcesoResponse> crear(@Validated @RequestBody ProcesoRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Long usuarioId = principal.usuarioId();
        Proceso proceso = procesoService.crear(empresaId, usuarioId, request.nombre(), request.descripcion(),
                request.categoria());
        return ResponseEntity.created(URI.create("/api/v1/procesos/" + proceso.getId()))
                .body(ProcesoResponse.of(proceso));
    }

    @Operation(summary = "Get a process with its change history")
    @ApiResponse(responseCode = "200", description = "The process and its history")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/{id}")
    public ResponseEntity<ProcesoDetalleResponse> detalle(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Proceso proceso = procesoService.obtener(empresaId, id);
        List<HistorialCambioResponse> historial = historialCambioService.listarPorProceso(empresaId, id).stream()
                .map(HistorialCambioResponse::of)
                .toList();
        return ResponseEntity.ok(new ProcesoDetalleResponse(ProcesoResponse.of(proceso), historial));
    }

    @Operation(summary = "Edit a process",
            description = "Replaces the name, description and category, and records the change in the history.")
    @ApiResponse(responseCode = "200", description = "Process updated")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/{id}")
    public ResponseEntity<ProcesoResponse> editar(@PathVariable Long id,
            @Validated @RequestBody EditarProcesoRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Long usuarioId = principal.usuarioId();
        Proceso proceso = procesoService.editarDatos(empresaId, id, usuarioId, request.nombre(), request.descripcion(),
                request.categoria());
        return ResponseEntity.ok(ProcesoResponse.of(proceso));
    }

    @Operation(summary = "Change the state of a process",
            description = "Publishes a draft. A published process cannot go back to draft.")
    @ApiResponse(responseCode = "200", description = "Process in its new state")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PatchMapping("/{id}")
    public ResponseEntity<ProcesoResponse> cambiarEstado(@PathVariable Long id,
            @Validated @RequestBody CambiarEstadoProcesoRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Long usuarioId = principal.usuarioId();
        Proceso proceso = procesoService.cambiarEstado(empresaId, id, usuarioId, request.estado());
        return ResponseEntity.ok(ProcesoResponse.of(proceso));
    }

    @Operation(summary = "Get the change history of a process", description = "Newest change first.")
    @ApiResponse(responseCode = "200", description = "History entries")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/{id}/historial")
    public ResponseEntity<List<HistorialCambioResponse>> historial(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        procesoService.obtener(empresaId, id);
        return ResponseEntity.ok(historialCambioService.listarPorProceso(empresaId, id).stream()
                .map(HistorialCambioResponse::of)
                .toList());
    }

    @Operation(summary = "Delete a process",
            description = "Soft delete: the process leaves every query but keeps its history. Administrators only.")
    @ApiResponse(responseCode = "204", description = "Process deleted")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Long usuarioId = principal.usuarioId();
        procesoService.eliminarLogico(empresaId, id, usuarioId);
        return ResponseEntity.noContent().build();
    }
}
