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

import com.facimus.procesos.modelado.controller.dto.ActividadRequest;
import com.facimus.procesos.modelado.controller.dto.ActividadResponse;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.security.ApiPrincipal;

import lombok.RequiredArgsConstructor;

/** HU-08 a HU-10: actividades (tareas del proceso). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ActividadController {

    private final ActividadService actividadService;

    @PostMapping("/lanes/{laneId}/actividades")
    public ResponseEntity<ActividadResponse> crear(@PathVariable Long laneId,
            @Validated @RequestBody ActividadRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Actividad actividad = actividadService.crear(empresaId, laneId, request.nombre(), request.descripcion(),
                request.posicionX(), request.posicionY());
        return ResponseEntity.created(URI.create("/api/v1/actividades/" + actividad.getId()))
        .body(ActividadResponse.of(actividad));
    }

    @GetMapping("/actividades/{id}")
    public ResponseEntity<ActividadResponse> detalle(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Actividad actividad = actividadService.obtener(empresaId, id);
        return ResponseEntity.ok(ActividadResponse.of(actividad));
    }

    @GetMapping("/lanes/{laneId}/actividades")
    public ResponseEntity<List<ActividadResponse>> listar(@PathVariable Long laneId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        List<ActividadResponse> actividades = actividadService.listarPorLane(empresaId, laneId).stream()
                .map(ActividadResponse::of).toList();
        return ResponseEntity.ok(actividades);
    }

    @PutMapping("/actividades/{id}")
    public ResponseEntity<ActividadResponse> editar(@PathVariable Long id,
            @Validated @RequestBody ActividadRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Actividad actividad = actividadService.editar(empresaId, id, request.nombre(), request.descripcion(),
                request.posicionX(), request.posicionY());
        return ResponseEntity.ok(ActividadResponse.of(actividad));
    }

    @DeleteMapping("/actividades/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        actividadService.eliminar(empresaId, id);
        return ResponseEntity.noContent().build();
    }
}
