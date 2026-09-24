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

import com.facimus.procesos.modelado.dto.request.ArcoRequest;
import com.facimus.procesos.modelado.dto.request.EditarArcoRequest;
import com.facimus.procesos.modelado.dto.response.ArcoResponse;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.DatosDeArco;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** HU-11 a HU-13: arcos (flujo entre nodos dentro de un pool). */
@Tag(name = "Sequence flows", description = "Arrows between the activities and gateways of one pool")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ArcoController {

    private final ArcoService arcoService;

    @Operation(summary = "Connect two flow nodes",
            description = "Both nodes must be in the same pool, and a pair of nodes is connected only once.")
    @ApiResponse(responseCode = "201", description = "Sequence flow created",
            headers = @Header(name = HttpHeaders.LOCATION, description = "URL of the new sequence flow"))
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/arcos")
    public ResponseEntity<ArcoResponse> crear(@Validated @RequestBody ArcoRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        ArcoResponse arco = arcoService.crear(empresaId, principal.usuarioId(),
                new DatosDeArco(request.origenId(), request.destinoId(), request.etiqueta(), request.condicion(),
                        Boolean.TRUE.equals(request.porDefecto()), orden(request.orden())));
        return ResponseEntity.created(URI.create("/api/v1/arcos/" + arco.id())).body(arco);
    }

    @Operation(summary = "Get a sequence flow")
    @ApiResponse(responseCode = "200", description = "The sequence flow")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/arcos/{id}")
    public ResponseEntity<ArcoResponse> detalle(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(arcoService.obtener(empresaId, id));
    }

    @Operation(summary = "List the sequence flows of a pool")
    @ApiResponse(responseCode = "200", description = "Sequence flows of the pool")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/pools/{poolId}/arcos")
    public ResponseEntity<List<ArcoResponse>> listar(@PathVariable Long poolId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(arcoService.listarPorPool(empresaId, poolId));
    }

    @Operation(summary = "Edit a sequence flow",
            description = "Replaces its label, its condition, whether it is the default flow of its gateway and "
                    + "the order in which the gateway evaluates it. Moving one of its nodes goes through the same "
                    + "rules as connecting them for the first time.")
    @ApiResponse(responseCode = "200", description = "Sequence flow updated")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/arcos/{id}")
    public ResponseEntity<ArcoResponse> editar(@PathVariable Long id,
            @Validated @RequestBody EditarArcoRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(arcoService.editar(empresaId, principal.usuarioId(), id,
                new DatosDeArco(request.origenId(), request.destinoId(), request.etiqueta(), request.condicion(),
                        Boolean.TRUE.equals(request.porDefecto()), orden(request.orden())),
                request.version()));
    }

    /** El orden es opcional: sin el, todas las salidas comparten el 0 y el gateway las evalua por id. */
    private static int orden(Integer orden) {
        return orden == null ? 0 : orden;
    }

    @Operation(summary = "Delete a sequence flow", description = "Administrators only.")
    @ApiResponse(responseCode = "204", description = "Sequence flow deleted")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @DeleteMapping("/arcos/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        arcoService.eliminar(empresaId, principal.usuarioId(), id);
        return ResponseEntity.noContent().build();
    }
}
