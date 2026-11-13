package com.facimus.procesos.security;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * D29: la sesion del navegador vive en dos cookies que el JavaScript de la pagina no puede leer, asi que un XSS no se
 * la lleva. La de acceso lleva el JWT a toda la API; la de refresco, solo a {@code /api/v1/auth}, que es donde sirve.
 * Las dos son {@code Secure}, que el navegador acepta tambien en {@code http://localhost}, y {@code SameSite=Lax}: otro
 * sitio no las manda en un POST.
 * <p>
 * ADR-0064: el token CSRF, en una tercera cookie, dura lo que dura la sesion. Cambia cuando se abre una, entre por
 * donde entre, y cuando se cierra; renovarla no lo toca, ni ninguna otra peticion.
 */
@Component
public class CookiesDeSesion {

    /** {@code __Host-}: el navegador la rechaza si no es Secure, si no va a / o si nombra un dominio. */
    public static final String ACCESO = "__Host-acceso";

    /** {@code __Secure-}: tiene que ser Secure. Su ruta es la de la autenticacion, y a nada mas viaja. */
    public static final String REFRESCO = "__Secure-refresco";

    static final String RUTA_DEL_REFRESCO = "/api/v1/auth";

    /** La cookie del token CSRF: la misma que lee y escribe el filtro CSRF (SecurityConfig). */
    private final CsrfTokenRepository tokensCsrf = repositorioCsrf();

    /**
     * D29: la cookie {@code XSRF-TOKEN}, la unica que el JavaScript de la web puede leer, para devolver su valor en
     * {@code X-XSRF-TOKEN}. Es {@code Secure} y {@code SameSite=Lax}, como las de la sesion.
     */
    // S3330: no es HttpOnly porque la web tiene que leerla; las cookies de la sesion si lo son.
    @SuppressWarnings("java:S3330")
    static CookieCsrfTokenRepository repositorioCsrf() {
        CookieCsrfTokenRepository repositorio = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repositorio.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Lax"));
        return repositorio;
    }

    /**
     * Las dos cookies de una sesion recien abierta, cada una con la vida de su token, y un token CSRF nuevo: el que
     * tenia el navegador antes de entrar, que alguien pudo dejarle, no le sirve a la sesion.
     */
    public void abrir(HttpServletRequest peticion, HttpServletResponse respuesta, String accessToken,
            Duration vidaDelAcceso, String refreshToken, Duration vidaDelRefresco) {
        renovar(respuesta, accessToken, vidaDelAcceso, refreshToken, vidaDelRefresco);
        nuevoTokenCsrf(peticion, respuesta);
    }

    /** Las dos cookies de una sesion renovada. Es la misma sesion, asi que el token CSRF sigue siendo el mismo. */
    public void renovar(HttpServletResponse respuesta, String accessToken, Duration vidaDelAcceso,
            String refreshToken, Duration vidaDelRefresco) {
        respuesta.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESO, accessToken, "/", vidaDelAcceso));
        respuesta.addHeader(HttpHeaders.SET_COOKIE,
                cookie(REFRESCO, refreshToken, RUTA_DEL_REFRESCO, vidaDelRefresco));
    }

    /** PR 49: solo la cookie de acceso, con un token nuevo de la misma sesion; la de refresco sigue sirviendo. */
    public void renovarAcceso(HttpServletResponse respuesta, String accessToken, Duration vidaDelAcceso) {
        respuesta.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESO, accessToken, "/", vidaDelAcceso));
    }

    /**
     * PR 49: el login a medias de quien tiene segundo factor. Solo la cookie de acceso, con un token que solo sirve
     * para dar el codigo; la de refresco se borra, porque todavia no hay una sesion que renovar. Es una entrada, asi
     * que el token CSRF tambien cambia.
     */
    public void abrirPendiente(HttpServletRequest peticion, HttpServletResponse respuesta, String accessToken,
            Duration vida) {
        respuesta.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESO, accessToken, "/", vida));
        respuesta.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESCO, "", RUTA_DEL_REFRESCO, Duration.ZERO));
        nuevoTokenCsrf(peticion, respuesta);
    }

    /**
     * Las borra del navegador: la misma cookie, vacia y vencida. El token CSRF cambia con la sesion que se va, y el
     * nuevo queda listo para el login que venga.
     */
    public void cerrar(HttpServletRequest peticion, HttpServletResponse respuesta) {
        respuesta.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESO, "", "/", Duration.ZERO));
        respuesta.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESCO, "", RUTA_DEL_REFRESCO, Duration.ZERO));
        nuevoTokenCsrf(peticion, respuesta);
    }

    /** Un token CSRF nuevo en su cookie, que pisa la que tenia el navegador. */
    private void nuevoTokenCsrf(HttpServletRequest peticion, HttpServletResponse respuesta) {
        tokensCsrf.saveToken(tokensCsrf.generateToken(peticion), peticion, respuesta);
    }

    public static Optional<String> leer(HttpServletRequest peticion, String nombre) {
        Cookie[] cookies = peticion.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> nombre.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(valor -> !valor.isBlank())
                .findFirst();
    }

    /** Si la peticion trae la sesion. Un navegador la manda por su cuenta, y por eso esas peticiones piden CSRF. */
    static boolean traeSesion(HttpServletRequest peticion) {
        return leer(peticion, ACCESO).isPresent() || leer(peticion, REFRESCO).isPresent();
    }

    private static String cookie(String nombre, String valor, String ruta, Duration vida) {
        return ResponseCookie.from(nombre, valor)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(ruta)
                .maxAge(vida)
                .build()
                .toString();
    }
}
