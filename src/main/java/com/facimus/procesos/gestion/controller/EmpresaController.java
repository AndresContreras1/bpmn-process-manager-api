package com.facimus.procesos.gestion.controller;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.gestion.dto.response.EmpresaResponse;
import com.facimus.procesos.gestion.dto.request.RegistroEmpresaRequest;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.service.EmpresaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** HU-01: registro de una nueva empresa y su administrador inicial. */
@Tag(name = "Stores", description = "Registration of a store, the tenant that owns users and processes")
@RestController
@RequestMapping("/api/v1/empresas")
@RequiredArgsConstructor
public class EmpresaController {

    private final EmpresaService empresaService;

    @Operation(summary = "Register a store",
            description = "Creates the store together with its first administrator, whose email becomes the login.")
    @ApiResponse(responseCode = "201", description = "Store and administrator created",
            headers = @Header(name = HttpHeaders.LOCATION, description = "URL of the new store"))
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @SecurityRequirements()
    @PostMapping
    public ResponseEntity<EmpresaResponse> registrar(@Validated @RequestBody RegistroEmpresaRequest request) {
        Empresa empresa = empresaService.registrar(request.nombreEmpresa(), request.nit(),
                request.correoContacto(), request.nombreAdmin(), request.emailAdmin(), request.passwordAdmin());
        return ResponseEntity.created(URI.create("/api/v1/empresas/" + empresa.getId()))
                .body(EmpresaResponse.of(empresa));
    }
}
