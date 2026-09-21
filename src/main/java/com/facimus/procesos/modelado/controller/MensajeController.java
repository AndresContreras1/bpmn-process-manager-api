package com.facimus.procesos.modelado.controller;

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

import com.facimus.procesos.modelado.controller.dto.EditarMensajeRequest;
import com.facimus.procesos.modelado.controller.dto.MensajeRequest;
import com.facimus.procesos.modelado.controller.dto.MensajeResponse;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.service.MensajeService;
import com.facimus.procesos.security.ApiPrincipal;

import lombok.RequiredArgsConstructor;

/** HU-25 a HU-27: mensajes (comunicacion entre pools). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MensajeController {

    private final MensajeService mensajeService;

    @GetMapping("/procesos/{procesoId}/mensajes")
    public ResponseEntity<List<MensajeResponse>> listar(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        List<MensajeResponse> mensajes = mensajeService.listarPorProceso(empresaId, procesoId).stream()
                .map(MensajeResponse::of)
                .toList();
        return ResponseEntity.ok(mensajes);
    }

    @GetMapping("/mensajes/{id}")
    public ResponseEntity<MensajeResponse> detalle(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Mensaje mensaje = mensajeService.obtener(empresaId, id);
        return ResponseEntity.ok(MensajeResponse.of(mensaje));
    }

    @PostMapping("/procesos/{procesoId}/mensajes")
    public ResponseEntity<MensajeResponse> crear(@PathVariable Long procesoId,
            @Validated @RequestBody MensajeRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Mensaje mensaje = mensajeService.crear(empresaId, procesoId, request.nombre(), request.contenido(),
                request.poolOrigenId(), request.poolDestinoId());
        return ResponseEntity.created(URI.create("/api/v1/mensajes/" + mensaje.getId()))
        .body(MensajeResponse.of(mensaje));
    }

    @PutMapping("/mensajes/{id}")
    public ResponseEntity<MensajeResponse> editar(@PathVariable Long id,
            @Validated @RequestBody EditarMensajeRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Mensaje mensaje = mensajeService.editar(empresaId, id, request.nombre(), request.contenido());
        return ResponseEntity.ok(MensajeResponse.of(mensaje));
    }

    @DeleteMapping("/mensajes/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        mensajeService.eliminar(empresaId, id);
        return ResponseEntity.noContent().build();
    }
}
