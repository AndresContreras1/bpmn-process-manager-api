package com.facimus.procesos.gestion.controller;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.gestion.dto.request.ConfiguracionTiendaRequest;
import com.facimus.procesos.gestion.dto.request.RegistroEmpresaRequest;
import com.facimus.procesos.gestion.dto.response.ConfiguracionTiendaResponse;
import com.facimus.procesos.gestion.dto.response.EmpresaResponse;
import com.facimus.procesos.gestion.dto.response.HistorialCambioResponse;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.HistorialCambioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/** HU-01: registro de una nueva empresa y su administrador inicial. */
@Tag(name = "Stores", description = "Registration of a store, the tenant that owns users and processes, and the "
        + "caller's own store")
@RestController
@RequestMapping("/api/v1/empresas")
@RequiredArgsConstructor
public class EmpresaController {

    private final EmpresaService empresaService;
    private final HistorialCambioService historialCambioService;
    private final ConfiguracionTiendaService configuracionTiendaService;

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

    @Operation(summary = "Get the history of the store",
            description = "Everything that happened in the store, newest first: users, process roles, the "
                    + "registration and every change to a process. Administrators only. Each entry says what it was "
                    + "about with recursoTipo and recursoId.")
    @ApiResponse(responseCode = "200", description = "One page of the store history")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @GetMapping("/actual/historial")
    public ResponseEntity<PageResponse<HistorialCambioResponse>> historial(
            @Parameter(description = "Page number, starting at 0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGINA_INVALIDA) int pagina,
            @Parameter(description = "Items per page, from 1 to 50")
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.TAMANO_INVALIDO)
            @Max(value = Paginacion.TAMANO_MAXIMO, message = Paginacion.TAMANO_INVALIDO) int tamano,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(historialCambioService.listarDeLaTienda(principal.empresaId(),
                Paginacion.de(pagina, tamano)));
    }

    @Operation(summary = "Get what the store decides about itself",
            description = "Who can create and edit participants and lanes, and the store's own simulation clock: "
                    + "which tick it is on and who moves it. Administrators only.")
    @ApiResponse(responseCode = "200", description = "The store settings")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @GetMapping("/actual/configuracion")
    public ResponseEntity<ConfiguracionTiendaResponse> configuracion(
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(configuracionTiendaService.obtener(principal.empresaId()));
    }

    @Operation(summary = "Change what the store decides about itself",
            description = "Reserving the structure to administrators leaves editors modeling everything inside a "
                    + "lane: steps, flows and messages. Deleting participants and lanes is an administrator's job "
                    + "either way. The simulation mode says who moves the clock; leaving it out keeps the current "
                    + "one. Administrators only.")
    @ApiResponse(responseCode = "200", description = "The store settings")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/actual/configuracion")
    public ResponseEntity<ConfiguracionTiendaResponse> editarConfiguracion(
            @Validated @RequestBody ConfiguracionTiendaRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(configuracionTiendaService.editar(principal.empresaId(), principal.usuarioId(),
                request.politicaEstructura(), request.modoSimulacion(), request.version()));
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
