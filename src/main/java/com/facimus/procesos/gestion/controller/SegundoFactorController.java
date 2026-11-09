package com.facimus.procesos.gestion.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.gestion.dto.request.CodigoRequest;
import com.facimus.procesos.gestion.dto.response.CodigosDeRecuperacionResponse;
import com.facimus.procesos.gestion.dto.response.InscripcionTotpResponse;
import com.facimus.procesos.gestion.dto.response.SegundoFactorResponse;
import com.facimus.procesos.gestion.service.SegundoFactorService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * PR 49: el segundo factor de la propia cuenta. Todo lo que lo cambia pide haber confirmado la identidad en los
 * ultimos diez minutos; el login lo pide solo cuando esta confirmado. Completar el login con el codigo vive en
 * AuthController, que es quien abre las sesiones.
 */
@Tag(name = "Second factor", description = "An authenticator app (TOTP, RFC 6238) and ten single-use recovery codes. "
        + "Enrolling, confirming, removing and renewing the codes ask for a verification of the last 10 minutes")
@RestController
@RequestMapping("/api/v1/auth/segundo-factor")
@RequiredArgsConstructor
public class SegundoFactorController {

    private final SegundoFactorService segundoFactorService;

    @Operation(summary = "See your second factor", description = "Whether the login asks for the code of an "
            + "authenticator app, and how many recovery codes are left.")
    @ApiResponse(responseCode = "200", description = "The state of the second factor")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping
    public ResponseEntity<SegundoFactorResponse> estado(@AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(segundoFactorService.estado(principal.empresaId(), principal.usuarioId()));
    }

    @Operation(summary = "Enroll an authenticator app",
            description = "Answers a new secret, in Base32 and as the otpauth URI of the QR code, once. It is not "
                    + "active until a code of it is confirmed; enrolling again before that replaces it. With an app "
                    + "already active it answers 409: remove it first.")
    @ApiResponse(responseCode = "200", description = "The secret, shown only here")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/totp")
    public ResponseEntity<InscripcionTotpResponse> inscribir(@AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(segundoFactorService.inscribir(principal.empresaId(), principal.usuarioId()));
    }

    @Operation(summary = "Confirm the authenticator app",
            description = "Takes a code the app shows now and turns the second factor on: from the next login, the "
                    + "password is not enough. Answers the ten recovery codes, once; the server keeps only a keyed "
                    + "hash of each.")
    @ApiResponse(responseCode = "200", description = "The recovery codes, shown only here")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/totp/confirmacion")
    public ResponseEntity<CodigosDeRecuperacionResponse> confirmar(@Validated @RequestBody CodigoRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(segundoFactorService.confirmar(principal.empresaId(), principal.usuarioId(),
                request.codigo()));
    }

    @Operation(summary = "Remove your authenticator app",
            description = "Removes the app and the recovery codes: the login goes back to the password alone.")
    @ApiResponse(responseCode = "204", description = "Second factor removed")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @DeleteMapping("/totp")
    public ResponseEntity<Void> quitar(@AuthenticationPrincipal ApiPrincipal principal) {
        segundoFactorService.quitarPropio(principal.empresaId(), principal.usuarioId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Renew the recovery codes",
            description = "Ten new codes, shown once; the ones left stop working. Only with an active app.")
    @ApiResponse(responseCode = "200", description = "The new recovery codes, shown only here")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/codigos")
    public ResponseEntity<CodigosDeRecuperacionResponse> renovarCodigos(
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(segundoFactorService.renovarCodigos(principal.empresaId(), principal.usuarioId()));
    }
}
