package com.facimus.procesos.security.controller;

import java.util.EnumSet;
import java.util.List;

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

import com.facimus.procesos.common.EnlaceNoValidoException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.common.security.Factor;
import com.facimus.procesos.gestion.dto.request.VinculoRequest;
import com.facimus.procesos.gestion.dto.response.IdentidadExternaResponse;
import com.facimus.procesos.gestion.dto.response.SesionResponse;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.service.IdentidadExternaService;
import com.facimus.procesos.gestion.service.SegundoFactorService;
import com.facimus.procesos.gestion.service.SesionService;
import com.facimus.procesos.security.AperturaDeSesion;
import com.facimus.procesos.security.LoginAuthenticator;
import com.facimus.procesos.security.ProveedoresOidc;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * PR 50: entrar con Google o Microsoft. La ida y la vuelta del proveedor las hace Spring Security en
 * {@code /api/v1/auth/oidc/{proveedor}}; aqui quedan los proveedores que hay, el vinculo con una cuenta que ya tenia
 * ese correo y las cuentas vinculadas de cada persona.
 */
@Tag(name = "Sign in with a provider", description = "Google and Microsoft with OpenID Connect and PKCE. The browser "
        + "goes to GET /api/v1/auth/oidc/{proveedor} and comes back with the same session cookies as the login")
@RestController
@RequestMapping("/api/v1/auth")
public class EntradaExternaController {

    private final ProveedoresOidc.Clientes clientes;
    private final IdentidadExternaService identidades;
    private final SegundoFactorService segundoFactor;
    private final SesionService sesiones;
    private final LoginAuthenticator loginAuthenticator;
    private final AperturaDeSesion apertura;

    public EntradaExternaController(ProveedoresOidc.Clientes clientes, IdentidadExternaService identidades,
            SegundoFactorService segundoFactor, SesionService sesiones, LoginAuthenticator loginAuthenticator,
            AperturaDeSesion apertura) {
        this.clientes = clientes;
        this.identidades = identidades;
        this.segundoFactor = segundoFactor;
        this.sesiones = sesiones;
        this.loginAuthenticator = loginAuthenticator;
        this.apertura = apertura;
    }

    @Operation(summary = "List the providers you can sign in with",
            description = "The ids of the configured providers, google and microsoft, in the order to show them. "
                    + "Each one is entered at GET /api/v1/auth/oidc/{id}, which sends the browser to the provider. "
                    + "Empty when none is configured.")
    @ApiResponse(responseCode = "200", description = "The configured providers")
    @SecurityRequirements()
    @GetMapping("/proveedores")
    public ResponseEntity<List<String>> proveedores() {
        return ResponseEntity.ok(clientes.proveedores());
    }

    @Operation(summary = "Link a provider to your account",
            description = "Coming back from a provider with the e-mail of an account that exists, the web app is sent "
                    + "to /vincular#token=... . This confirms that the account is yours: with the password, or with "
                    + "the code of the authenticator app or a recovery code when you have a second factor. It links "
                    + "the provider and opens the session. The token works once and for 10 minutes; wrong passwords "
                    + "and codes count with the login's limit.")
    @ApiResponse(responseCode = "200", description = "The session cookies, and in the body the user's profile")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
    @SecurityRequirements()
    @PostMapping("/identidades/vinculo")
    public ResponseEntity<SesionResponse> vincular(@Validated @RequestBody VinculoRequest request,
            HttpServletRequest peticion, HttpServletResponse respuesta) {
        UsuarioResponse usuario = identidades.usuarioDelVinculo(request.token())
                .orElseThrow(() -> new EnlaceNoValidoException());
        Long empresaId = usuario.empresaId();
        Long usuarioId = usuario.id();
        Factor factor;
        if (segundoFactor.loPide(empresaId, usuarioId)) {
            if (request.codigo() == null || request.codigo().isBlank()) {
                throw new SolicitudInvalidaException("Con segundo factor, vincular la cuenta pide el código de tu "
                        + "aplicación de autenticación o uno de recuperación.");
            }
            factor = loginAuthenticator.comprobarCodigo(usuario.email(), peticion.getRemoteAddr(),
                    () -> segundoFactor.verificar(empresaId, usuarioId, request.codigo()));
        } else {
            if (request.password() == null || request.password().isBlank()) {
                throw new SolicitudInvalidaException("La contraseña es obligatoria.");
            }
            loginAuthenticator.autenticar(usuario.email(), request.password(), peticion.getRemoteAddr());
            factor = Factor.PASSWORD;
        }
        identidades.vincular(request.token());
        return ResponseEntity.ok(apertura.abrir(sesiones.iniciar(empresaId, usuarioId,
                EnumSet.of(factor, Factor.PROVEEDOR_EXTERNO)), peticion, respuesta));
    }

    @Operation(summary = "List your linked providers", description = "The Google and Microsoft accounts you can sign "
            + "in with.")
    @ApiResponse(responseCode = "200", description = "The linked accounts")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping("/identidades")
    public ResponseEntity<List<IdentidadExternaResponse>> identidades(
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(identidades.identidades(principal.empresaId(), principal.usuarioId()));
    }

    @Operation(summary = "Unlink a provider",
            description = "Stops signing in with that account. In a store that signs in only with a provider, the "
                    + "last one of someone who is not an administrator cannot go: 409. Needs a verification of the "
                    + "last 10 minutes.")
    @ApiResponse(responseCode = "204", description = "Unlinked")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @DeleteMapping("/identidades/{id}")
    public ResponseEntity<Void> desvincular(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        identidades.desvincular(principal.empresaId(), principal.usuarioId(), id);
        return ResponseEntity.noContent().build();
    }
}
