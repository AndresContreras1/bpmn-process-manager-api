package com.facimus.procesos.ejecucion.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.ejecucion.dto.request.AbrirCasoRequest;
import com.facimus.procesos.ejecucion.dto.request.VariablesRequest;
import com.facimus.procesos.ejecucion.dto.response.CasoDetalleResponse;
import com.facimus.procesos.ejecucion.dto.response.CasoResponse;
import com.facimus.procesos.ejecucion.dto.response.EventoCasoResponse;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.service.CasoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

/** Los casos: cada uno es una ejecucion de una version publicada, es decir un pedido. */
@Tag(name = "Cases", description = "Running a published process: opening orders, watching them and closing them")
@RestController
@RequiredArgsConstructor
public class CasoController {

    /** Los campos por los que se puede ordenar el listado; cualquier otro es un 400 y no una consulta rara. */
    private static final String ORDEN = "^(id|referencia|estado)(,(asc|desc))?$";

    private final CasoService casoService;

    @Operation(summary = "Open a case on a process",
            description = "Opens it on the version in force and runs it until it stops, normally at the first task "
                    + "of a tray. The process has to start with a plain start event: one that starts with a message "
                    + "is opened by sending that message. Administrators and editors.")
    @ApiResponse(responseCode = "201", description = "Case opened",
            headers = @Header(name = HttpHeaders.LOCATION, description = "URL of the new case"))
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/api/v1/procesos/{procesoId}/casos")
    public ResponseEntity<CasoResponse> abrir(@PathVariable Long procesoId,
            @Validated @RequestBody AbrirCasoRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        CasoResponse caso = casoService.abrir(principal.empresaId(), principal.usuarioId(), procesoId,
                request.referencia(), request.variables());
        return ResponseEntity.created(URI.create("/api/v1/casos/" + caso.id())).body(caso);
    }

    @Operation(summary = "List the cases of the store",
            description = "Newest first. Filters by process, state and the reference of the order. Any role.")
    @ApiResponse(responseCode = "200", description = "One page of cases")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping("/api/v1/casos")
    public ResponseEntity<PageResponse<CasoResponse>> listar(
            @Parameter(description = "Only the cases of this process") @RequestParam(required = false) Long procesoId,
            @Parameter(description = "Only the cases in this state") @RequestParam(required = false) EstadoCaso estado,
            @Parameter(description = "The exact reference of an order", example = "ORD-1001")
            @RequestParam(required = false) String referencia,
            @Parameter(description = "Page number, starting at 0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGINA_INVALIDA) int pagina,
            @Parameter(description = "Items per page, from 1 to 50")
            @RequestParam(defaultValue = "10") @Min(value = 1, message = Paginacion.TAMANO_INVALIDO)
            @Max(value = Paginacion.TAMANO_MAXIMO, message = Paginacion.TAMANO_INVALIDO) int tamano,
            @Parameter(description = "Field and direction: id, referencia or estado, then asc or desc",
                    example = "id,desc")
            @RequestParam(defaultValue = "id,desc") @Pattern(regexp = ORDEN,
                    message = "Orden no permitido. Use id, referencia o estado, con ,asc o ,desc.") String orden,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(casoService.listar(principal.empresaId(), procesoId, estado, referencia,
                Paginacion.de(pagina, tamano, orden)));
    }

    @Operation(summary = "Get a case",
            description = "With the steps it went through and the variables its gateways are deciding with. "
                    + "Any role.")
    @ApiResponse(responseCode = "200", description = "The case")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/api/v1/casos/{id}")
    public ResponseEntity<CasoDetalleResponse> obtener(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(casoService.obtener(principal.empresaId(), id));
    }

    @Operation(summary = "Get the timeline of a case",
            description = "Every decision, task and error, in the order they happened. Any role.")
    @ApiResponse(responseCode = "200", description = "The timeline")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/api/v1/casos/{id}/eventos")
    public ResponseEntity<List<EventoCasoResponse>> eventos(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(casoService.eventos(principal.empresaId(), id));
    }

    @Operation(summary = "Cancel a case",
            description = "Closes it before its time: its live tokens are switched off and it stops moving. "
                    + "Nothing is deleted. Administrators and editors.")
    @ApiResponse(responseCode = "200", description = "The case, now cancelled")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/api/v1/casos/{id}/cancelar")
    public ResponseEntity<CasoResponse> cancelar(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(casoService.cancelar(principal.empresaId(), principal.usuarioId(), id));
    }

    @Operation(summary = "Replace the variables of a case",
            description = "What a gateway that found no path was missing is usually here. Administrators only.")
    @ApiResponse(responseCode = "200", description = "The case, with its variables replaced")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PatchMapping("/api/v1/casos/{id}/variables")
    public ResponseEntity<CasoResponse> variables(@PathVariable Long id,
            @Validated @RequestBody VariablesRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(casoService.corregirVariables(principal.empresaId(), id, request.variables(),
                request.version()));
    }

    @Operation(summary = "Retry a case that is in error",
            description = "Evaluates again what left it without a path, with the variables it has now. "
                    + "Administrators only.")
    @ApiResponse(responseCode = "200", description = "The case, after trying again")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/api/v1/casos/{id}/reintentar")
    public ResponseEntity<CasoResponse> reintentar(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(casoService.reintentar(principal.empresaId(), principal.usuarioId(), id));
    }
}
