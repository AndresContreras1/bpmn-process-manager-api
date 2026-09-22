package com.facimus.procesos.gestion.controller;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.gestion.dto.request.RegistroEmpresaRequest;
import com.facimus.procesos.gestion.dto.response.EmpresaResponse;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** HU-01: registro de una nueva empresa y su administrador inicial. */
@Tag(name = "Stores", description = "Registration of a store, the tenant that owns users and processes, and the "
        + "caller's own store")
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
        EmpresaResponse empresa = empresaService.registrar(request.nombreEmpresa(), request.nit(),
                request.correoContacto(), request.nombreAdmin(), request.emailAdmin(), request.passwordAdmin());
        return ResponseEntity.created(URI.create("/api/v1/empresas/" + empresa.id())).body(empresa);
    }

    @Operation(summary = "Get the caller's own store", description = "The store of the user who holds the token.")
    @ApiResponse(responseCode = "200", description = "The caller's store")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping("/actual")
    public ResponseEntity<EmpresaResponse> actual(@AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(empresaService.obtener(principal.empresaId(), principal.empresaId()));
    }

    @Operation(summary = "Get a store",
            description = "The URL that the registration answers in Location. Only the caller's own store exists "
                    + "for it: any other id answers 404.")
    @ApiResponse(responseCode = "200", description = "The store")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/{id}")
    public ResponseEntity<EmpresaResponse> detalle(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(empresaService.obtener(principal.empresaId(), id));
    }
}
