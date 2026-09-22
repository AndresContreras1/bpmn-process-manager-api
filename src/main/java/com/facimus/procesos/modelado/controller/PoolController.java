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

import com.facimus.procesos.modelado.dto.request.EditarPoolRequest;
import com.facimus.procesos.modelado.dto.request.PoolRequest;
import com.facimus.procesos.modelado.dto.response.PoolResponse;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.service.PoolService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** HU-21 y HU-23: pools (participantes del proceso). */
@Tag(name = "Pools",
        description = "Participants of a process: the store and outside parties such as customers or carriers")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PoolController {

    private final PoolService poolService;

    @Operation(summary = "List the pools of a process", description = "In diagram order; the store's pool first.")
    @ApiResponse(responseCode = "200", description = "Pools of the process")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/procesos/{procesoId}/pools")
    public ResponseEntity<List<PoolResponse>> listar(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(poolService.listarPorProceso(empresaId, procesoId));
    }

    @Operation(summary = "Get a pool")
    @ApiResponse(responseCode = "200", description = "The pool")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/pools/{id}")
    public ResponseEntity<PoolResponse> detalle(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(poolService.obtener(empresaId, id));
    }

    @Operation(summary = "Add a pool to a process", description = "The pool goes after the existing ones.")
    @ApiResponse(responseCode = "201", description = "Pool created",
            headers = @Header(name = HttpHeaders.LOCATION, description = "URL of the new pool"))
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @PostMapping("/procesos/{procesoId}/pools")
    public ResponseEntity<PoolResponse> crear(@PathVariable Long procesoId,
            @Validated @RequestBody PoolRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        PoolResponse pool = poolService.crear(empresaId, procesoId, request.nombre(), request.tipoParticipante(),
                request.cajaNegra());
        return ResponseEntity.created(URI.create("/api/v1/pools/" + pool.id())).body(pool);
    }

    @Operation(summary = "Edit a pool", description = "Replaces its name and participant type.")
    @ApiResponse(responseCode = "200", description = "Pool updated")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/pools/{id}")
    public ResponseEntity<PoolResponse> editar(@PathVariable Long id,
            @Validated @RequestBody EditarPoolRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(poolService.editar(empresaId, id, request.nombre(), request.tipoParticipante(),
                request.version()));
    }

    @Operation(summary = "Delete a pool",
            description = "Deletes its lanes too. A pool whose lanes hold activities or gateways cannot be deleted. "
                    + "Administrators only.")
    @ApiResponse(responseCode = "204", description = "Pool deleted")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @DeleteMapping("/pools/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        poolService.eliminar(empresaId, id);
        return ResponseEntity.noContent().build();
    }
}
