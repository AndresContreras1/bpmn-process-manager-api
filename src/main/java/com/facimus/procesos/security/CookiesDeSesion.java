package com.facimus.procesos.security;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * D29: la sesion del navegador vive en dos cookies que el JavaScript de la pagina no puede leer, asi que un XSS no se
 * la lleva. La de acceso lleva el JWT a toda la API; la de refresco, solo a {@code /api/v1/auth}, que es donde sirve.
 * Las dos son {@code Secure}, que el navegador acepta tambien en {@code http://localhost}, y {@code SameSite=Lax}: otro
 * sitio no las manda en un POST.
 */
@Component
public class CookiesDeSesion {

    /** {@code __Host-}: el navegador la rechaza si no es Secure, si no va a / o si nombra un dominio. */
    public static final String ACCESO = "__Host-acceso";

    /** {@code __Secure-}: tiene que ser Secure. Su ruta es la de la autenticacion, y a nada mas viaja. */
    public static final String REFRESCO = "__Secure-refresco";

    static final String RUTA_DEL_REFRESCO = "/api/v1/auth";

    private final Duration vidaDelAcceso;
    private final Duration vidaDelRefresco;

    public CookiesDeSesion(@Value("${jwt.expiration-seconds}") long segundosDelAcceso,
            @Value("${jwt.refresh-expiration-seconds}") long segundosDelRefresco) {
        this.vidaDelAcceso = Duration.ofSeconds(segundosDelAcceso);
        this.vidaDelRefresco = Duration.ofSeconds(segundosDelRefresco);
    }

    /** Las dos cookies de una sesion recien abierta o renovada, cada una con la vida de su token. */
    public void abrir(HttpServletResponse respuesta, String accessToken, String refreshToken) {
        respuesta.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESO, accessToken, "/", vidaDelAcceso));
        respuesta.addHeader(HttpHeaders.SET_COOKIE,
                cookie(REFRESCO, refreshToken, RUTA_DEL_REFRESCO, vidaDelRefresco));
    }

    /** Las borra del navegador: la misma cookie, vacia y vencida. */
    public void cerrar(HttpServletResponse respuesta) {
        respuesta.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESO, "", "/", Duration.ZERO));
        respuesta.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESCO, "", RUTA_DEL_REFRESCO, Duration.ZERO));
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
