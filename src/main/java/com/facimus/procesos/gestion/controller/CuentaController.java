package com.facimus.procesos.gestion.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.gestion.dto.request.AceptarInvitacionRequest;
import com.facimus.procesos.gestion.dto.request.EnlaceRequest;
import com.facimus.procesos.gestion.dto.request.NuevaClaveRequest;
import com.facimus.procesos.gestion.dto.request.RecuperacionRequest;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.service.CuentaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Lo que la cuenta resuelve con un enlace del correo: verificar el correo, recuperar la clave y aceptar una invitacion.
 * Cada enlace sirve una vez y vence; su token llega en la direccion despues de {@code #token=}, y la web lo manda aqui.
 */
@Tag(name = "Account", description = "E-mail verification, password recovery and invitations, with single-use links "
        + "sent by e-mail")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class CuentaController {

    private final CuentaService cuentaService;

    @Operation(summary = "Send the verification e-mail again",
            description = "Sends the signed-in user a new link to verify their e-mail. Nothing is sent when it is "
                    + "already verified. Until it is, the user cannot invite anybody by e-mail.")
    @ApiResponse(responseCode = "202", description = "The e-mail is queued")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @PostMapping("/verificacion")
    public ResponseEntity<Void> pedirVerificacion(@AuthenticationPrincipal ApiPrincipal principal) {
        cuentaService.pedirVerificacion(principal.empresaId(), principal.usuarioId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @Operation(summary = "Verify the e-mail", description = "Uses the link of the verification e-mail. It works "
            + "once and expires after 48 hours.")
    @ApiResponse(responseCode = "204", description = "E-mail verified")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @SecurityRequirements()
    @PostMapping("/verificacion/confirmar")
    public ResponseEntity<Void> verificar(@Validated @RequestBody EnlaceRequest request) {
        cuentaService.verificarCorreo(request.token());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Ask for a password reset",
            description = "Sends a link to choose a new password if the e-mail belongs to an active user. The answer "
                    + "is the same whether it does or not, so it tells nobody which e-mails are registered.")
    @ApiResponse(responseCode = "202", description = "If the e-mail belongs to an active user, the link is on its way")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @SecurityRequirements()
    @PostMapping("/recuperacion")
    public ResponseEntity<Void> pedirRecuperacion(@Validated @RequestBody RecuperacionRequest request) {
        cuentaService.pedirRecuperacion(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @Operation(summary = "Choose a new password",
            description = "Uses the link of the recovery e-mail: it works once and expires after 15 minutes. Every "
                    + "session of the user is closed, and the other recovery links stop working.")
    @ApiResponse(responseCode = "204", description = "Password changed")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @SecurityRequirements()
    @PostMapping("/recuperacion/confirmar")
    public ResponseEntity<Void> recuperar(@Validated @RequestBody NuevaClaveRequest request) {
        cuentaService.recuperarClave(request.token(), request.nueva());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Accept an invitation",
            description = "Uses the link of the invitation e-mail, which works once and expires after 7 days, and "
                    + "creates the user with the access role of the invitation and an e-mail already verified.")
    @ApiResponse(responseCode = "201", description = "User created; they sign in with their e-mail and this password")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @SecurityRequirements()
    @PostMapping("/invitacion/aceptar")
    public ResponseEntity<UsuarioResponse> aceptarInvitacion(@Validated @RequestBody AceptarInvitacionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cuentaService.aceptarInvitacion(request.token(), request.nombre(), request.password()));
    }
}
