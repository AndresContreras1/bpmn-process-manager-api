package com.facimus.procesos.gestion.controller;

import java.util.List;

import java.net.URI;
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

import com.facimus.procesos.gestion.controller.dto.RolProcesoRequest;
import com.facimus.procesos.gestion.controller.dto.RolProcesoVistaResponse;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.security.ApiPrincipal;

import lombok.RequiredArgsConstructor;

/** HU-17 a HU-20: roles de proceso (solo administrador crea/edita/elimina). */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RolProcesoController {

    private final RolProcesoService rolProcesoService;

    @GetMapping
    public ResponseEntity<List<RolProcesoVistaResponse>> listar(@AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        List<RolProcesoVistaResponse> roles = rolProcesoService.listarConUso(empresaId).stream()
                .map(RolProcesoVistaResponse::of)
                .toList();
        return ResponseEntity.ok(roles);
    }

    @PostMapping
    public ResponseEntity<RolProcesoVistaResponse> crear(@Validated @RequestBody RolProcesoRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        RolProceso rol = rolProcesoService.crear(empresaId, request.nombre(), request.descripcion());
        return ResponseEntity.created(URI.create("/api/v1/roles/" + rol.getId()))
                .body(new RolProcesoVistaResponse(rol.getId(), rol.getNombre(), rol.getDescripcion(), 0, false));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RolProcesoVistaResponse> obtener(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        RolProceso rol = rolProcesoService.obtener(empresaId, id);
        long usos = rolProcesoService.contarUsos(empresaId, id);
        return ResponseEntity.ok(new RolProcesoVistaResponse(
                rol.getId(), rol.getNombre(), rol.getDescripcion(), usos, usos > 0));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RolProcesoVistaResponse> editar(@PathVariable Long id,
            @Validated @RequestBody RolProcesoRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        RolProceso rol = rolProcesoService.editar(empresaId, id, request.nombre(), request.descripcion());
        long usos = rolProcesoService.contarUsos(empresaId, id);
        return ResponseEntity.ok(
                new RolProcesoVistaResponse(rol.getId(), rol.getNombre(), rol.getDescripcion(), usos, usos > 0));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        rolProcesoService.eliminar(empresaId, id);
        return ResponseEntity.noContent().build();
    }
}
