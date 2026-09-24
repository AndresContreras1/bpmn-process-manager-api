package com.facimus.procesos.gestion.controller;

import java.net.URI;
import java.util.List;

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

import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.request.CambiarEstadoProcesoRequest;
import com.facimus.procesos.gestion.dto.request.EditarProcesoRequest;
import com.facimus.procesos.gestion.dto.request.ProcesoRequest;
import com.facimus.procesos.gestion.dto.response.HistorialCambioResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoDetalleResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

/** HU-04 a HU-07: creacion, edicion, eliminacion logica y consulta de procesos. */
@Tag(name = "Processes", description = "The store's BPMN processes: drafts, publication, soft delete and change history")
@RestController
@RequestMapping("/api/v1/procesos")
@RequiredArgsConstructor
public class ProcesoController {

    private static final String ORDEN = "(nombre|categoria|estado|fechaCreacion|fechaModificacion)(,(asc|desc))?";

    private final ProcesoService procesoService;

    @Operation(summary = "List processes",
            description = "Pages of up to 50 processes, 10 by default, most recently modified first unless orden "
                    + "says otherwise. The filters are optional and can be combined.")
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
            @Parameter(description = "Also list the deleted processes; administrators only")
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            @Parameter(description = "Page number, starting at 0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGINA_INVALIDA) int pagina,
            @Parameter(description = "Items per page, from 1 to 50")
            @RequestParam(defaultValue = "10") @Min(value = 1, message = Paginacion.TAMANO_INVALIDO)
            @Max(value = Paginacion.TAMANO_MAXIMO, message = Paginacion.TAMANO_INVALIDO) int tamano,
            @Parameter(description = "Field and direction: nombre, categoria, estado, fechaCreacion or "
                    + "fechaModificacion, then asc or desc", example = "nombre,asc")
            @RequestParam(defaultValue = "fechaModificacion,desc") @Pattern(regexp = ORDEN,
                    message = "Orden no permitido. Use nombre, categoria, estado, fechaCreacion o fechaModificacion, "
                            + "con ,asc o ,desc.") String orden,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(procesoService.buscar(empresaId, nombre, estado, categoria,
                soloElAdministradorMiraLoEliminado(incluirInactivos, principal),
                Paginacion.de(pagina, tamano, orden)));
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
        ProcesoResponse proceso = procesoService.crear(empresaId, usuarioId, request.nombre(),
                request.descripcion(), request.categoria());
        return ResponseEntity.created(URI.create("/api/v1/procesos/" + proceso.id())).body(proceso);
    }

    @Operation(summary = "Get a process with its change history")
    @ApiResponse(responseCode = "200", description = "The process and its history")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/{id}")
    public ResponseEntity<ProcesoDetalleResponse> detalle(@PathVariable Long id,
            @Parameter(description = "Also read it when it is deleted; administrators only")
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(procesoService.obtenerDetalle(principal.empresaId(), id,
                soloElAdministradorMiraLoEliminado(incluirInactivos, principal)));
    }

    /**
     * HU-06.3: lo eliminado sigue en la base y el administrador puede consultarlo. A los demas no se les responde
     * 403, porque el endpoint si es suyo: lo que no pueden pedir es ese parametro.
     */
    private static boolean soloElAdministradorMiraLoEliminado(boolean pedido, ApiPrincipal principal) {
        if (pedido && principal.rol() != RolAcceso.ADMINISTRADOR) {
            throw new SolicitudInvalidaException("Solo un administrador puede consultar los procesos eliminados.");
        }
        return pedido;
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
        return ResponseEntity.ok(procesoService.editarDatos(empresaId, id, usuarioId, request.nombre(),
                request.descripcion(), request.categoria(), request.version()));
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
        return ResponseEntity.ok(procesoService.cambiarEstado(empresaId, id, usuarioId, request.estado(),
                request.version()));
    }

    @Operation(summary = "Get the change history of a process", description = "Newest change first.")
    @ApiResponse(responseCode = "200", description = "History entries")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/{id}/historial")
    public ResponseEntity<List<HistorialCambioResponse>> historial(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(procesoService.listarHistorial(principal.empresaId(), id));
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
