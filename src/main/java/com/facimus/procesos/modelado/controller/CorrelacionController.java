package com.facimus.procesos.modelado.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.modelado.controller.dto.CorrelacionRequest;
import com.facimus.procesos.modelado.controller.dto.CorrelacionResponse;
import com.facimus.procesos.modelado.model.Correlacion;
import com.facimus.procesos.modelado.service.CorrelacionService;
import com.facimus.procesos.security.ApiPrincipal;

import lombok.RequiredArgsConstructor;

/** HU-28: correlacion de mensajes. */
@RestController
@RequestMapping("/api/v1/mensajes/{mensajeId}/correlacion")
@RequiredArgsConstructor
public class CorrelacionController {

    private final CorrelacionService correlacionService;

    @PutMapping
    public ResponseEntity<CorrelacionResponse> definir(@PathVariable Long mensajeId,
            @Validated @RequestBody CorrelacionRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Correlacion correlacion = correlacionService.definir(empresaId, mensajeId, request.criterio());
        return ResponseEntity.ok(CorrelacionResponse.of(correlacion));
    }

    @GetMapping
    public ResponseEntity<CorrelacionResponse> obtener(@PathVariable Long mensajeId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Correlacion correlacion = correlacionService.obtener(empresaId, mensajeId);
        return ResponseEntity.ok(CorrelacionResponse.of(correlacion));
    }
}
