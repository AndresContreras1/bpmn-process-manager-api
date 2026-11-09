package com.facimus.procesos.gestion.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.gestion.dto.request.DominioRequest;
import com.facimus.procesos.gestion.dto.response.DominioResponse;
import com.facimus.procesos.gestion.service.DominioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** PR 50: los dominios de correo de la tienda, con los que su gente se une al entrar con Google o Microsoft. */
@Tag(name = "Store domains", description = "E-mail domains the store proves it controls with a TXT record. Whoever "
        + "signs in with Google or Microsoft from a verified one for the first time joins with its default role. "
        + "Administrators only")
@RestController
@RequestMapping("/api/v1/empresas/actual/dominios")
@RequiredArgsConstructor
public class DominioController {

    private final DominioService dominioService;

    @Operation(summary = "List the domains of the store")
    @ApiResponse(responseCode = "200", description = "The domains, verified or not")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @GetMapping
    public ResponseEntity<List<DominioResponse>> listar(@AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(dominioService.listar(principal.empresaId()));
    }

    @Operation(summary = "Add a domain", description = "Answers the TXT record the domain has to publish. Until "
            + "it is verified, nobody joins through it.")
    @ApiResponse(responseCode = "201", description = "The domain, not verified yet")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping
    public ResponseEntity<DominioResponse> agregar(@Validated @RequestBody DominioRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dominioService.agregar(principal.empresaId(),
                principal.usuarioId(), request.dominio(), request.rolPorDefecto()));
    }

    @Operation(summary = "Verify a domain", description = "Looks for its TXT record in the DNS. Without it the answer "
            + "is 400; when another store already verified the domain, 409.")
    @ApiResponse(responseCode = "200", description = "The domain, verified")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/{id}/verificacion")
    public ResponseEntity<DominioResponse> verificar(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(dominioService.verificar(principal.empresaId(), principal.usuarioId(), id));
    }

    @Operation(summary = "Remove a domain", description = "Whoever already joined through it stays.")
    @ApiResponse(responseCode = "204", description = "Removed")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> quitar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        dominioService.quitar(principal.empresaId(), principal.usuarioId(), id);
        return ResponseEntity.noContent().build();
    }
}
