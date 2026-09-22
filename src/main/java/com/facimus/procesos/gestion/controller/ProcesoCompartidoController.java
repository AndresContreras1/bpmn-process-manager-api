package com.facimus.procesos.gestion.controller;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.gestion.dto.request.CompartirProcesoRequest;
import com.facimus.procesos.gestion.dto.response.EmpresaInvitadaResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoRecibidoResponse;
import com.facimus.procesos.gestion.service.ProcesoCompartidoService;
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

/** HU-23: procesos compartidos en solo lectura con otras empresas. */
@Tag(name = "Process sharing",
        description = "Read-only access to a process for other stores (HU-23), the only exception to store isolation")
@RestController
@RequestMapping("/api/v1/procesos")
@RequiredArgsConstructor
public class ProcesoCompartidoController {

    private static final String ORDEN = "(nombre|categoria|fechaModificacion)(,(asc|desc))?";

    private final ProcesoCompartidoService procesoCompartidoService;

    @Operation(summary = "Share a process with another store",
            description = "The other store, found by its NIT, can read the process and its diagram but never change "
                    + "them. Administrators only.")
    @ApiResponse(responseCode = "201", description = "Process shared",
            headers = @Header(name = HttpHeaders.LOCATION, description = "URL of the new share"))
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/{procesoId}/compartidos")
    public ResponseEntity<EmpresaInvitadaResponse> compartir(@PathVariable Long procesoId,
            @Validated @RequestBody CompartirProcesoRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        EmpresaInvitadaResponse invitada = procesoCompartidoService.compartir(principal.empresaId(), procesoId,
                principal.usuarioId(), request.nit());
        return ResponseEntity.created(URI.create("/api/v1/procesos/" + procesoId + "/compartidos/"
                + invitada.empresaId())).body(invitada);
    }

    @Operation(summary = "List the stores a process is shared with")
    @ApiResponse(responseCode = "200", description = "The stores that can read the process")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/{procesoId}/compartidos")
    public ResponseEntity<List<EmpresaInvitadaResponse>> listar(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(procesoCompartidoService.listarInvitadas(principal.empresaId(), procesoId));
    }

    @Operation(summary = "Get a store a process is shared with")
    @ApiResponse(responseCode = "200", description = "The share")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/{procesoId}/compartidos/{empresaInvitadaId}")
    public ResponseEntity<EmpresaInvitadaResponse> detalle(@PathVariable Long procesoId,
            @PathVariable Long empresaInvitadaId, @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(procesoCompartidoService.obtenerInvitada(principal.empresaId(), procesoId,
                empresaInvitadaId));
    }

    @Operation(summary = "Stop sharing a process with a store",
            description = "The process history keeps when it was shared and when it stopped. Administrators only.")
    @ApiResponse(responseCode = "204", description = "The store can no longer read the process")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @DeleteMapping("/{procesoId}/compartidos/{empresaInvitadaId}")
    public ResponseEntity<Void> dejarDeCompartir(@PathVariable Long procesoId, @PathVariable Long empresaInvitadaId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        procesoCompartidoService.dejarDeCompartir(principal.empresaId(), procesoId, empresaInvitadaId,
                principal.usuarioId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List the processes other stores share with the caller",
            description = "Read-only: open each one with GET /api/v1/procesos/{id}/diagrama.")
    @ApiResponse(responseCode = "200", description = "One page of shared processes")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping("/compartidos-conmigo")
    public ResponseEntity<PageResponse<ProcesoRecibidoResponse>> recibidos(
            @Parameter(description = "Page number, starting at 0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGINA_INVALIDA) int pagina,
            @Parameter(description = "Items per page, from 1 to 50")
            @RequestParam(defaultValue = "10") @Min(value = 1, message = Paginacion.TAMANO_INVALIDO)
            @Max(value = Paginacion.TAMANO_MAXIMO, message = Paginacion.TAMANO_INVALIDO) int tamano,
            @Parameter(description = "Field and direction: nombre, categoria or fechaModificacion, then asc or desc",
                    example = "nombre,asc")
            @RequestParam(defaultValue = "fechaModificacion,desc") @Pattern(regexp = ORDEN,
                    message = "Orden no permitido. Use nombre, categoria o fechaModificacion, con ,asc o ,desc.")
            String orden,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(procesoCompartidoService.buscarRecibidos(principal.empresaId(),
                Paginacion.de(pagina, tamano, orden)));
    }
}
