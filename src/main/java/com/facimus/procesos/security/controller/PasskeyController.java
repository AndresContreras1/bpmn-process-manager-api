package com.facimus.procesos.security.controller;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SinPermisoException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.common.security.Factor;
import com.facimus.procesos.gestion.dto.request.EtiquetaPasskeyRequest;
import com.facimus.procesos.gestion.dto.request.PasskeyFirmaRequest;
import com.facimus.procesos.gestion.dto.request.PasskeyRegistroRequest;
import com.facimus.procesos.gestion.dto.response.OpcionesWebAuthnResponse;
import com.facimus.procesos.gestion.dto.response.PasskeyRegistradaResponse;
import com.facimus.procesos.gestion.dto.response.PasskeyResponse;
import com.facimus.procesos.gestion.dto.response.SesionResponse;
import com.facimus.procesos.gestion.dto.response.SesionVerificada;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.service.IdentidadExternaService;
import com.facimus.procesos.gestion.service.PasskeyService;
import com.facimus.procesos.gestion.service.PersonaWebAuthn;
import com.facimus.procesos.gestion.service.SesionService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.security.AperturaDeSesion;
import com.facimus.procesos.security.CeremoniasWebAuthn;
import com.facimus.procesos.security.LoginAuthenticator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * PR 49b: las passkeys de la propia cuenta y las tres puertas por las que sirven: completar el login a medias,
 * confirmar quien es la persona y entrar sin clave. Cada ceremonia empieza pidiendo unas opciones, que el navegador le
 * pasa al dispositivo, y termina con lo que el dispositivo firmo; las sesiones salen por las mismas cookies que el
 * login con clave.
 */
@Tag(name = "Passkeys", description = "Passkeys (WebAuthn) as a second factor and to sign in without a password. "
        + "Registering, renaming and removing one ask for a verification of the last 10 minutes")
@RestController
@RequestMapping("/api/v1/auth")
public class PasskeyController {

    private final CeremoniasWebAuthn ceremonias;
    private final PasskeyService passkeyService;
    private final LoginAuthenticator loginAuthenticator;
    private final SesionService sesionService;
    private final UsuarioService usuarioService;
    private final IdentidadExternaService identidadExternaService;
    private final AperturaDeSesion apertura;

    @SuppressWarnings("java:S107") // las piezas de cada puerta, cada una por su nombre
    public PasskeyController(CeremoniasWebAuthn ceremonias, PasskeyService passkeyService,
            LoginAuthenticator loginAuthenticator, SesionService sesionService, UsuarioService usuarioService,
            IdentidadExternaService identidadExternaService, AperturaDeSesion apertura) {
        this.ceremonias = ceremonias;
        this.passkeyService = passkeyService;
        this.loginAuthenticator = loginAuthenticator;
        this.sesionService = sesionService;
        this.usuarioService = usuarioService;
        this.identidadExternaService = identidadExternaService;
        this.apertura = apertura;
    }

    @Operation(summary = "List your passkeys", description = "Oldest first, with the name you gave each and when it was "
            + "last used.")
    @ApiResponse(responseCode = "200", description = "The passkeys of the signed-in user")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping("/segundo-factor/passkeys")
    public ResponseEntity<List<PasskeyResponse>> listar(@AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(passkeyService.listar(principal.empresaId(), principal.usuarioId()));
    }

    @Operation(summary = "Start registering a passkey",
            description = "Answers the creation options for navigator.credentials.create(), without the passkeys you "
                    + "already have. The device has to verify you (fingerprint, face or PIN) and keep the passkey "
                    + "discoverable, so it can also sign you in without the email.")
    @ApiResponse(responseCode = "200", description = "The options and the id of their challenge")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @PostMapping("/segundo-factor/passkeys/opciones")
    public ResponseEntity<OpcionesWebAuthnResponse> opcionesDeRegistro(
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(ceremonias.opcionesDeRegistro(principal));
    }

    @Operation(summary = "Register a passkey",
            description = "Checks what the device answered to the creation options and keeps its public key. From the "
                    + "next login the password is not enough. When it is your first second factor the answer carries "
                    + "your ten recovery codes, once. A challenge works once and for 5 minutes; an answer that does not "
                    + "verify gets 400, and a passkey already registered 409. Up to 10 passkeys per person.")
    @ApiResponse(responseCode = "201", description = "The passkey, and the recovery codes if they are new")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/segundo-factor/passkeys")
    public ResponseEntity<PasskeyRegistradaResponse> registrar(@Validated @RequestBody PasskeyRegistroRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        byte[] credencial = ceremonias.registrar(principal, request.desafio(), request.etiqueta(),
                request.credencial());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(passkeyService.registrada(principal.empresaId(), principal.usuarioId(), credencial));
    }

    @Operation(summary = "Rename a passkey")
    @ApiResponse(responseCode = "200", description = "The passkey with its new name")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @PatchMapping("/segundo-factor/passkeys/{id}")
    public ResponseEntity<PasskeyResponse> renombrar(@PathVariable Long id,
            @Validated @RequestBody EtiquetaPasskeyRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(passkeyService.renombrar(principal.empresaId(), principal.usuarioId(), id,
                request.etiqueta()));
    }

    @Operation(summary = "Remove a passkey",
            description = "The device keeps it, but it no longer opens anything here. When it was your last second "
                    + "factor, the recovery codes go with it and the login goes back to the password alone.")
    @ApiResponse(responseCode = "204", description = "Passkey removed")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @DeleteMapping("/segundo-factor/passkeys/{id}")
    public ResponseEntity<Void> quitar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        passkeyService.quitar(principal.empresaId(), principal.usuarioId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Start a verification with a passkey",
            description = "Answers the request options for navigator.credentials.get(), limited to your passkeys: to "
                    + "finish the half-done login the password left, or to confirm who you are. Without passkeys it "
                    + "answers 409.")
    @ApiResponse(responseCode = "200", description = "The options and the id of their challenge")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/segundo-factor/verificacion/passkey/opciones")
    public ResponseEntity<OpcionesWebAuthnResponse> opcionesDeVerificacion(
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(ceremonias.opcionesDeVerificacion(principal));
    }

    @Operation(summary = "Finish the login with a passkey",
            description = "Takes what one of your passkeys signed for the verification options, from the half-done "
                    + "login, and opens the session with both cookies. A signature that does not verify counts with "
                    + "the login's limit: after 5 it answers 429.")
    @ApiResponse(responseCode = "200", description = "The session cookies, and in the body the user's profile")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
    @PostMapping("/segundo-factor/verificacion/passkey")
    public ResponseEntity<SesionResponse> completarLogin(@Validated @RequestBody PasskeyFirmaRequest request,
            @AuthenticationPrincipal ApiPrincipal principal, Authentication autenticacion,
            HttpServletRequest peticion, HttpServletResponse respuesta) {
        if (!principal.segundoFactorPendiente()) {
            throw new ReglaNegocioException("Esta sesión ya está completa: no le falta ningún factor.");
        }
        verificar(principal, request, peticion);
        Long empresaId = principal.empresaId();
        Long usuarioId = principal.usuarioId();
        sesionService.cerrar(empresaId, usuarioId, principal.sesion());
        Set<Factor> factores = Factor.de(autenticacion.getAuthorities());
        factores.add(Factor.PASSKEY);
        return ResponseEntity.ok(apertura.abrir(sesionService.iniciar(empresaId, usuarioId, factores), respuesta));
    }

    @Operation(summary = "Confirm who you are with a passkey",
            description = "Like POST /api/v1/auth/reautenticacion, with what one of your passkeys signed for the "
                    + "verification options: renews only the access cookie, marked as verified now.")
    @ApiResponse(responseCode = "200", description = "A new access cookie of the same session, and in the body the "
            + "user's profile")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
    @PostMapping("/reautenticacion/passkey")
    public ResponseEntity<SesionResponse> reautenticar(@Validated @RequestBody PasskeyFirmaRequest request,
            @AuthenticationPrincipal ApiPrincipal principal, HttpServletRequest peticion,
            HttpServletResponse respuesta) {
        if (principal.segundoFactorPendiente()) {
            throw new ReglaNegocioException("Primero termina de entrar: este login todavía espera el segundo factor.");
        }
        verificar(principal, request, peticion);
        SesionVerificada verificada = sesionService.reverificar(principal.empresaId(), principal.usuarioId(),
                principal.sesion(), Factor.PASSKEY);
        return ResponseEntity.ok(apertura.renovarAcceso(verificada, respuesta));
    }

    @Operation(summary = "Start signing in with a passkey",
            description = "Answers the request options for navigator.credentials.get() without any credential: the "
                    + "device offers the passkeys it keeps for this site and you choose one, without typing the email.")
    @ApiResponse(responseCode = "200", description = "The options and the id of their challenge")
    @SecurityRequirements()
    @PostMapping("/passkeys/opciones")
    public ResponseEntity<OpcionesWebAuthnResponse> opcionesDeEntrada() {
        return ResponseEntity.ok(ceremonias.opcionesDeEntrada());
    }

    @Operation(summary = "Sign in with a passkey",
            description = "Takes what the passkey signed for the options and opens a session with both cookies, with "
                    + "no password and no code: the device already verified you, so a passkey is a second factor by "
                    + "itself. A signature that does not verify, an unknown passkey and a deactivated user get the "
                    + "same 401; for a known passkey it counts with the login's limit of its owner.")
    @ApiResponse(responseCode = "200", description = "The session cookies, and in the body the user's profile")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
    @SecurityRequirements()
    @PostMapping("/passkeys/entrada")
    public ResponseEntity<SesionResponse> entrar(@Validated @RequestBody PasskeyFirmaRequest request,
            HttpServletRequest peticion, HttpServletResponse respuesta) {
        // De una passkey que existe se sabe de quien es antes de verificarla: su duena tiene el mismo limite que en el
        // login con clave, y un fallo queda en la bitacora de su tienda.
        Optional<String> duena = ceremonias.credencialDe(request.credencial())
                .flatMap(passkeyService::buscar)
                .flatMap(guardada -> passkeyService.personaPorHandle(guardada.handle()))
                .map(PersonaWebAuthn::email);
        Optional<PersonaWebAuthn> persona = duena.isPresent()
                ? loginAuthenticator.comprobarPasskey(duena.get(), peticion.getRemoteAddr(),
                        () -> ceremonias.entrar(request.desafio(), request.credencial()))
                : ceremonias.entrar(request.desafio(), request.credencial());
        UsuarioResponse usuario = persona
                .flatMap(encontrada -> usuarioService.buscarCredenciales(encontrada.email()))
                .map(credenciales -> credenciales.usuario())
                .orElseThrow(() -> new BadCredentialsException(LoginAuthenticator.PASSKEY_INVALIDA));
        // PR 50: una tienda que entra con un proveedor tampoco entra con passkey, salvo su administrador.
        if (identidadExternaService.exigeEntradaExterna(usuario.empresaId(), usuario.rolAcceso())) {
            throw new SinPermisoException("Tu tienda entra con Google o Microsoft: usa ese botón para entrar.");
        }
        return ResponseEntity.ok(apertura.abrir(sesionService.iniciar(usuario.empresaId(), usuario.id(),
                EnumSet.of(Factor.PASSKEY)), respuesta));
    }

    /**
     * La firma de una passkey de la persona de la sesion, con el limite y la bitacora de los codigos. Una que no
     * verifica responde 400, como un codigo malo: la sesion sigue, y se puede volver a intentar.
     */
    private void verificar(ApiPrincipal principal, PasskeyFirmaRequest request, HttpServletRequest peticion) {
        loginAuthenticator.comprobarPasskey(principal.email(), peticion.getRemoteAddr(),
                        () -> ceremonias.verificar(principal, request.desafio(), request.credencial()))
                .orElseThrow(() -> new SolicitudInvalidaException(CeremoniasWebAuthn.NO_VERIFICADA));
    }
}
