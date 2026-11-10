package com.facimus.procesos.gestion.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.gestion.dto.request.PreferenciasRequest;
import com.facimus.procesos.gestion.dto.response.PreferenciasResponse;
import com.facimus.procesos.gestion.service.PreferenciasService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** F12: el idioma y la zona horaria de la persona y de su organizacion. */
@Tag(name = "Preferences", description = "The language and the time zone of the web app, for each person and for "
        + "the organization")
@RestController
@RequiredArgsConstructor
public class PreferenciasController {

    private final PreferenciasService preferenciasService;

    @Operation(summary = "See your language and time zone",
            description = "The ones in effect, the ones you chose and the ones of the organization.")
    @ApiResponse(responseCode = "200", description = "The preferences of the signed-in user")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping("/api/v1/auth/preferencias")
    public ResponseEntity<PreferenciasResponse> obtener(@AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(preferenciasService.obtener(principal.empresaId(), principal.usuarioId()));
    }

    @Operation(summary = "Choose your language and time zone",
            description = "Leave one out, or empty, to follow the organization. A time zone is a name of the IANA "
                    + "database, such as America/Bogota.")
    @ApiResponse(responseCode = "200", description = "The preferences now in effect")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @PutMapping("/api/v1/auth/preferencias")
    public ResponseEntity<PreferenciasResponse> cambiarLasPropias(@Validated @RequestBody PreferenciasRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(preferenciasService.cambiarLasPropias(principal.empresaId(), principal.usuarioId(),
                request.idioma(), request.zonaHoraria()));
    }

    @Operation(summary = "Set the language and time zone of the organization",
            description = "What everybody who chose none uses. Both are required. It lands in the store history.")
    @ApiResponse(responseCode = "200", description = "The preferences of the signed-in administrator")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @PutMapping("/api/v1/empresas/actual/preferencias")
    public ResponseEntity<PreferenciasResponse> cambiarLasDeLaOrganizacion(
            @Validated @RequestBody PreferenciasRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(preferenciasService.cambiarLasDeLaOrganizacion(principal.empresaId(),
                principal.usuarioId(), request.idioma(), request.zonaHoraria()));
    }
}
