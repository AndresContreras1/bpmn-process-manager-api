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

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.modelado.dto.request.ArtefactoRequest;
import com.facimus.procesos.modelado.dto.request.EditarArtefactoRequest;
import com.facimus.procesos.modelado.dto.response.ArtefactoResponse;
import com.facimus.procesos.modelado.service.ArtefactoService;
import com.facimus.procesos.modelado.service.DatosDeArtefacto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** PR 55: anotaciones, grupos, objetos de datos y almacenes. */
@Tag(name = "Artifacts", description = "Text annotations, groups, data objects and data stores: they describe the "
        + "process without changing its flow")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ArtefactoController {

    private final ArtefactoService artefactoService;

    @Operation(summary = "Add an artifact to a process",
            description = "A data object or data store goes in a pool modelled inside, because it is data of that "
                    + "pool's process; an annotation or a group may stand alone.")
    @ApiResponse(responseCode = "201", description = "Artifact created",
            headers = @Header(name = HttpHeaders.LOCATION, description = "URL of the new artifact"))
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/procesos/{procesoId}/artefactos")
    public ResponseEntity<ArtefactoResponse> crear(@PathVariable Long procesoId,
            @Validated @RequestBody ArtefactoRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        ArtefactoResponse artefacto = artefactoService.crear(empresaId, principal.usuarioId(), procesoId,
                new DatosDeArtefacto(request.tipo(), request.texto(), request.poolId(), request.laneId(),
                        request.posicionX(), request.posicionY(), request.ancho(), request.alto()));
        return ResponseEntity.created(URI.create("/api/v1/artefactos/" + artefacto.id())).body(artefacto);
    }

    @Operation(summary = "List the artifacts of a process")
    @ApiResponse(responseCode = "200", description = "Artifacts of the process")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/procesos/{procesoId}/artefactos")
    public ResponseEntity<List<ArtefactoResponse>> listar(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(artefactoService.listar(empresaId, procesoId));
    }

    @Operation(summary = "Get an artifact")
    @ApiResponse(responseCode = "200", description = "The artifact")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/artefactos/{id}")
    public ResponseEntity<ArtefactoResponse> detalle(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(artefactoService.obtener(empresaId, id));
    }

    @Operation(summary = "Edit an artifact", description = "Replaces its text, place and size; the type stays.")
    @ApiResponse(responseCode = "200", description = "Artifact updated")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/artefactos/{id}")
    public ResponseEntity<ArtefactoResponse> editar(@PathVariable Long id,
            @Validated @RequestBody EditarArtefactoRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(artefactoService.editar(empresaId, principal.usuarioId(), id,
                new DatosDeArtefacto(null, request.texto(), request.poolId(), request.laneId(), request.posicionX(),
                        request.posicionY(), request.ancho(), request.alto()),
                request.version()));
    }

    @Operation(summary = "Delete an artifact", description = "Its associations go with it. Administrators only.")
    @ApiResponse(responseCode = "204", description = "Artifact deleted")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @DeleteMapping("/artefactos/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        artefactoService.eliminar(empresaId, principal.usuarioId(), id);
        return ResponseEntity.noContent().build();
    }
}
