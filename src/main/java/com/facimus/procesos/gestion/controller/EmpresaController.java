package com.facimus.procesos.gestion.controller;

import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.gestion.controller.dto.EmpresaResponse;
import com.facimus.procesos.gestion.controller.dto.RegistroEmpresaRequest;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.service.EmpresaService;

import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;

/** HU-01: registro de una nueva empresa y su administrador inicial. */
@RestController
@RequestMapping("/api/v1/empresas")
@RequiredArgsConstructor
public class EmpresaController {

    private final EmpresaService empresaService;

    @SecurityRequirements()
    @PostMapping
    public ResponseEntity<EmpresaResponse> registrar(@Validated @RequestBody RegistroEmpresaRequest request) {
        Empresa empresa = empresaService.registrar(request.nombreEmpresa(), request.nit(),
                request.correoContacto(), request.nombreAdmin(), request.emailAdmin(), request.passwordAdmin());
        return ResponseEntity.created(URI.create("/api/v1/empresas/" + empresa.getId()))
        .body(EmpresaResponse.of(empresa));
    }
}
