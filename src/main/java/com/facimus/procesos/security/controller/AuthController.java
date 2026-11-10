package com.facimus.procesos.security.controller;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SesionInvalidaException;
import com.facimus.procesos.common.SinPermisoException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.common.security.Factor;
import com.facimus.procesos.gestion.dto.request.CambiarClaveRequest;
import com.facimus.procesos.gestion.dto.request.CodigoRequest;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.dto.request.ReautenticacionRequest;
import com.facimus.procesos.gestion.dto.response.SesionActual;
import com.facimus.procesos.gestion.dto.response.SesionActualResponse;
import com.facimus.procesos.gestion.dto.response.SesionResponse;
import com.facimus.procesos.gestion.dto.response.SesionVerificada;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.service.IdentidadExternaService;
import com.facimus.procesos.gestion.service.SegundoFactorService;
import com.facimus.procesos.gestion.service.SesionService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.security.AperturaDeSesion;
import com.facimus.procesos.security.CookiesDeSesion;
import com.facimus.procesos.security.LoginAuthenticator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * HU-03 y D29: inicio, renovacion y cierre de sesion. Los tokens viajan en cookies que el navegador guarda y manda
 * solo, y que el JavaScript de la pagina no puede leer; el cuerpo de la respuesta dice quien entro y cuanto dura el
 * acceso, nunca un token.
 */
@Tag(name = "Authentication", description = "Login, renewal and logout. The session travels in two HttpOnly cookies: "
        + "a short-lived JWT for the whole API and a single-use refresh token for /api/v1/auth")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LoginAuthenticator loginAuthenticator;
    private final SesionService sesionService;
    private final UsuarioService usuarioService;
    private final CookiesDeSesion cookies;
    private final SegundoFactorService segundoFactorService;
    private final AperturaDeSesion apertura;
    private final IdentidadExternaService identidadExternaService;

    public AuthController(LoginAuthenticator loginAuthenticator, SesionService sesionService,
            UsuarioService usuarioService, CookiesDeSesion cookies, SegundoFactorService segundoFactorService,
            AperturaDeSesion apertura, IdentidadExternaService identidadExternaService) {
        this.loginAuthenticator = loginAuthenticator;
        this.sesionService = sesionService;
        this.usuarioService = usuarioService;
        this.cookies = cookies;
        this.segundoFactorService = segundoFactorService;
        this.apertura = apertura;
        this.identidadExternaService = identidadExternaService;
    }

    @Operation(summary = "Get the CSRF token", description = "Sets the XSRF-TOKEN cookie, whose value every request "
            + "that changes something with the session cookies sends back in the X-XSRF-TOKEN header. The web app "
            + "calls it when it starts; the login asks for the header too.")
    @ApiResponse(responseCode = "204", description = "The XSRF-TOKEN cookie is set")
    @SecurityRequirements()
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        // Leerlo es lo que lo crea y escribe su cookie: el token se carga cuando alguien lo pide.
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get the current session",
            description = "Who the session cookies belong to and how long the session has left: before it closes for "
                    + "being idle, and before it ends. It renews nothing, so asking does not keep a session alive. "
                    + "The web app asks when it starts, instead of remembering the profile in the browser. On the "
                    + "half-done login of a second factor it says only that the code is missing.")
    @ApiResponse(responseCode = "200", description = "The profile and the time the session has left")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping("/sesion")
    public ResponseEntity<SesionActualResponse> sesion(@AuthenticationPrincipal ApiPrincipal principal) {
        if (principal.segundoFactorPendiente()) {
            return ResponseEntity.ok(new SesionActualResponse(
                    usuarioService.obtener(principal.empresaId(), principal.usuarioId()), true, null, null));
        }
        SesionActual actual = sesionService.actual(principal.empresaId(), principal.usuarioId(), principal.sesion());
        return ResponseEntity.ok(new SesionActualResponse(actual.usuario(), false, actual.inactividad().toSeconds(),
                actual.restante().toSeconds()));
    }

    @Operation(summary = "Log in", description = "Checks the email and password and opens a session: an access "
            + "cookie with a signed JWT that lasts 15 minutes and a refresh cookie that renews it. The refresh "
            + "cookie lasts the store's idle timeout, an hour unless the store shortened it, and the session ends "
            + "the store's maximum duration after the login, 24 hours unless shortened: then the user signs in "
            + "again. A wrong password and an unknown email get the same answer. After 5 failed attempts for an "
            + "email from the same address within 15 minutes, the login answers 429 until the oldest attempt leaves "
            + "that window. With a second factor the password is not enough: the answer carries only an access "
            + "cookie of 5 minutes, segundoFactorPendiente is true, and that cookie only works to send the code to "
            + "POST /api/v1/auth/segundo-factor/verificacion.")
    @ApiResponse(responseCode = "200", description = "The session cookies, and in the body the user's profile")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
    @SecurityRequirements()
    @PostMapping("/login")
    public ResponseEntity<SesionResponse> login(@Validated @RequestBody LoginRequest request,
            HttpServletRequest peticion, HttpServletResponse respuesta) {
        // Si falla, ApiExceptionHandler responde 401 con el mismo mensaje generico, exista o no el correo (HU-03).
        UsuarioResponse usuario = loginAuthenticator.autenticar(request.email(), request.password(),
                peticion.getRemoteAddr());
        // PR 50: en una tienda que entra con un proveedor, la clave sola solo le sirve al administrador.
        if (identidadExternaService.exigeEntradaExterna(usuario.empresaId(), usuario.rolAcceso())) {
            throw new SinPermisoException("Tu tienda entra con Google o Microsoft: usa ese botón para entrar.");
        }
        if (segundoFactorService.loPide(usuario.empresaId(), usuario.id())) {
            return ResponseEntity.ok(apertura.pendiente(usuario, EnumSet.of(Factor.PASSWORD), respuesta));
        }
        return ResponseEntity.ok(apertura.abrir(sesionService.iniciar(usuario.empresaId(), usuario.id()),
                respuesta));
    }

    @Operation(summary = "Finish the login with the second factor",
            description = "Takes the 6 digits of the authenticator app, or one of the recovery codes, from the "
                    + "half-done login the password left, and opens the session with both cookies. A code of the app "
                    + "works once, and so does each recovery code. Wrong codes count with the login's limit: after 5 "
                    + "it answers 429.")
    @ApiResponse(responseCode = "200", description = "The session cookies, and in the body the user's profile")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
    @PostMapping("/segundo-factor/verificacion")
    public ResponseEntity<SesionResponse> completarLogin(@Validated @RequestBody CodigoRequest request,
            @AuthenticationPrincipal ApiPrincipal principal, Authentication autenticacion,
            HttpServletRequest peticion, HttpServletResponse respuesta) {
        if (!principal.segundoFactorPendiente()) {
            throw new ReglaNegocioException("Esta sesión ya está completa: no le falta ningún factor.");
        }
        Long empresaId = principal.empresaId();
        Long usuarioId = principal.usuarioId();
        Factor factor = loginAuthenticator.comprobarCodigo(principal.email(), peticion.getRemoteAddr(),
                () -> segundoFactorService.verificar(empresaId, usuarioId, request.codigo()));
        // El token del login a medias ya hizo su trabajo: desde aqui no sirve ni para intentarlo otra vez.
        sesionService.cerrar(empresaId, usuarioId, principal.sesion());
        // El primer factor es el que dejo el login a medias: la clave o, desde el PR 50, un proveedor.
        Set<Factor> factores = Factor.de(autenticacion.getAuthorities());
        factores.add(factor);
        return ResponseEntity.ok(apertura.abrir(sesionService.iniciar(empresaId, usuarioId, factores), respuesta));
    }

    @Operation(summary = "Renew the session", description = "Trades the refresh cookie for a new access cookie and a "
            + "new refresh cookie of the same session. Each refresh token works once: sending one that was already "
            + "used closes the session, because a copy of it is going around. Past the store's maximum duration "
            + "the session closes, and near it neither cookie outlives it. A refusal also clears the cookies.")
    @ApiResponse(responseCode = "200", description = "New cookies of the session, and in the body the user's profile")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @SecurityRequirements()
    @PostMapping("/refresh")
    public ResponseEntity<SesionResponse> renovar(HttpServletRequest peticion, HttpServletResponse respuesta) {
        try {
            String refreshToken = CookiesDeSesion.leer(peticion, CookiesDeSesion.REFRESCO)
                    .orElseThrow(SesionInvalidaException::new);
            return ResponseEntity.ok(apertura.abrir(sesionService.renovar(refreshToken), respuesta));
        } catch (SesionInvalidaException e) {
            // Una cookie que ya no renueva nada no tiene que seguir viajando en cada peticion.
            cookies.cerrar(respuesta);
            throw e;
        }
    }

    @Operation(summary = "Log out", description = "Closes the session of the cookies, the one the access token names "
            + "and the one the refresh token belongs to, and clears both cookies. It answers the same with an expired "
            + "access token or without a session, so a browser can always leave.")
    @ApiResponse(responseCode = "204", description = "Session closed and cookies cleared")
    @SecurityRequirements()
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal ApiPrincipal principal, HttpServletRequest peticion,
            HttpServletResponse respuesta) {
        if (principal != null) {
            sesionService.cerrar(principal.empresaId(), principal.usuarioId(), principal.sesion());
        }
        CookiesDeSesion.leer(peticion, CookiesDeSesion.REFRESCO).ifPresent(sesionService::cerrarConToken);
        cookies.cerrar(respuesta);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Change your own password",
            description = "Checks the password in use and replaces it. Every session of the user is closed, this "
                    + "one included, and the answer opens a new one with new cookies. A user who signed in with a "
                    + "temporary password can only call this, logout and refresh until the password is changed.")
    @ApiResponse(responseCode = "200", description = "The cookies of a new session, and in the body the user's profile")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @PostMapping("/password")
    public ResponseEntity<SesionResponse> cambiarClave(@Validated @RequestBody CambiarClaveRequest request,
            @AuthenticationPrincipal ApiPrincipal principal, Authentication autenticacion,
            HttpServletResponse respuesta) {
        Long empresaId = principal.empresaId();
        Long usuarioId = principal.usuarioId();
        usuarioService.cambiarClavePropia(empresaId, usuarioId, request.actual(), request.nueva());
        // Cambiar la contrasena echa a quien la estuviera usando en otro sitio; quien la cambio sigue con una nueva,
        // con los factores con que entro: quien dio su codigo no tiene que darlo otra vez por cambiar la clave.
        sesionService.cerrarTodas(empresaId, usuarioId);
        Set<Factor> factores = Factor.de(autenticacion.getAuthorities());
        factores.add(Factor.PASSWORD);
        return ResponseEntity.ok(apertura.abrir(sesionService.iniciar(empresaId, usuarioId, factores), respuesta));
    }

    @Operation(summary = "Confirm who you are again",
            description = "Checks who the signed-in user is again and renews only the access cookie, marked as "
                    + "verified now. Without a second factor it takes the password; with one, the code of the "
                    + "authenticator app or a recovery code, and the password is not enough. What needs a recent "
                    + "verification, such as closing the store or managing the second factor, asks for one from the "
                    + "last 10 minutes; without it the answer is 403 with the title \"Verificación requerida\", and "
                    + "this is how to get it back. Wrong passwords and codes count with the login's limit.")
    @ApiResponse(responseCode = "200", description = "A new access cookie of the same session, and in the body the "
            + "user's profile")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
    @PostMapping("/reautenticacion")
    public ResponseEntity<SesionResponse> reautenticar(@Validated @RequestBody ReautenticacionRequest request,
            @AuthenticationPrincipal ApiPrincipal principal, HttpServletRequest peticion,
            HttpServletResponse respuesta) {
        // La misma comprobacion del login, con su limite de intentos: una cookie robada no prueba claves gratis.
        Long empresaId = principal.empresaId();
        Long usuarioId = principal.usuarioId();
        Factor factor;
        if (segundoFactorService.loPide(empresaId, usuarioId)) {
            // Con segundo factor, la clave sola no confirma nada: quien tiene la cookie quiza tambien tiene la clave.
            if (request.codigo() == null || request.codigo().isBlank()) {
                throw new SolicitudInvalidaException("Con segundo factor, confirmar quién eres pide el código de tu "
                        + "aplicación de autenticación o uno de recuperación.");
            }
            factor = loginAuthenticator.comprobarCodigo(principal.email(), peticion.getRemoteAddr(),
                    () -> segundoFactorService.verificar(empresaId, usuarioId, request.codigo()));
        } else {
            if (request.password() == null || request.password().isBlank()) {
                throw new SolicitudInvalidaException("La contraseña es obligatoria.");
            }
            loginAuthenticator.autenticar(principal.email(), request.password(), peticion.getRemoteAddr());
            factor = Factor.PASSWORD;
        }
        SesionVerificada verificada = sesionService.reverificar(empresaId, usuarioId, principal.sesion(), factor);
        return ResponseEntity.ok(apertura.renovarAcceso(verificada, respuesta));
    }
}
