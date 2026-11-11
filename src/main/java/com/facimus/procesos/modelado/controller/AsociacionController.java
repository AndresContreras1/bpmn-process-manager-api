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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.modelado.dto.request.AsociacionRequest;
import com.facimus.procesos.modelado.dto.response.AsociacionResponse;
import com.facimus.procesos.modelado.service.AsociacionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** PR 55: asociaciones y asociaciones de datos. */
@Tag(name = "Associations", description = "Lines that join an artifact to another element, and data that goes into "
        + "or out of an activity")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AsociacionController {

    private final AsociacionService asociacionService;

    @Operation(summary = "Join two elements of a process",
            description = "The ends are bpmnIds. An ASOCIACION has an artifact on one side at least; ENTRADA_DATOS "
                    + "goes from a data object or store to an activity, and SALIDA_DATOS the other way.")
    @ApiResponse(responseCode = "201", description = "Association created",
            headers = @Header(name = HttpHeaders.LOCATION, description = "URL of the association list"))
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/procesos/{procesoId}/asociaciones")
    public ResponseEntity<AsociacionResponse> crear(@PathVariable Long procesoId,
            @Validated @RequestBody AsociacionRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        AsociacionResponse asociacion = asociacionService.crear(empresaId, principal.usuarioId(), procesoId,
                request.tipo(), request.origen().strip(), request.destino().strip(), request.direccion());
        return ResponseEntity.created(URI.create("/api/v1/procesos/" + procesoId + "/asociaciones"))
                .body(asociacion);
    }

    @Operation(summary = "List the associations of a process")
    @ApiResponse(responseCode = "200", description = "Associations of the process")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/procesos/{procesoId}/asociaciones")
    public ResponseEntity<List<AsociacionResponse>> listar(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(asociacionService.listar(empresaId, procesoId));
    }

    @Operation(summary = "Delete an association", description = "Administrators only.")
    @ApiResponse(responseCode = "204", description = "Association deleted")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @DeleteMapping("/asociaciones/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        asociacionService.eliminar(empresaId, principal.usuarioId(), id);
        return ResponseEntity.noContent().build();
    }
}
