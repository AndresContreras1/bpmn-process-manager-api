package com.facimus.procesos.modelado.controller;

import java.net.URI;
import java.util.List;
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

import com.facimus.procesos.modelado.controller.dto.ArcoRequest;
import com.facimus.procesos.modelado.controller.dto.ArcoResponse;
import com.facimus.procesos.modelado.controller.dto.EditarArcoRequest;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.security.ApiPrincipal;

import lombok.RequiredArgsConstructor;

/** HU-11 a HU-13: arcos (flujo entre nodos dentro de un pool). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ArcoController {

    private final ArcoService arcoService;

    @PostMapping("/arcos")
    public ResponseEntity<ArcoResponse> crear(@Validated @RequestBody ArcoRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Arco arco = arcoService.crear(empresaId, request.origenId(), request.destinoId(), request.etiqueta(),
                request.condicion());
        return ResponseEntity.created(URI.create("/api/v1/arcos/" + arco.getId()))
                .body(ArcoResponse.of(arco));
    }

    @GetMapping("/arcos/{id}")
    public ResponseEntity<ArcoResponse> detalle(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Arco arco = arcoService.obtener(empresaId, id);
        return ResponseEntity.ok(ArcoResponse.of(arco));
    }

    @GetMapping("/pools/{poolId}/arcos")
    public ResponseEntity<List<ArcoResponse>> listar(@PathVariable Long poolId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        List<ArcoResponse> arcos = arcoService.listarPorPool(empresaId, poolId).stream()
                .map(ArcoResponse::of).toList();
        return ResponseEntity.ok(arcos);
    }

    @PutMapping("/arcos/{id}")
    public ResponseEntity<ArcoResponse> editar(@PathVariable Long id,
            @Validated @RequestBody EditarArcoRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Arco arco = arcoService.editar(empresaId, id, request.etiqueta(), request.condicion());
        return ResponseEntity.ok(ArcoResponse.of(arco));
    }

    @DeleteMapping("/arcos/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        arcoService.eliminar(empresaId, id);
        return ResponseEntity.noContent().build();
    }
}
