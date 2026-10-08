package com.facimus.procesos.security;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import jakarta.servlet.http.Cookie;

/**
 * D29 en las pruebas con MockMvc: lo que hace un navegador con la sesion. Entrar deja dos cookies, y cada peticion
 * devuelve la de acceso junto con el token CSRF, como lo manda la web: el valor de la cookie XSRF-TOKEN en la
 * cabecera X-XSRF-TOKEN. No usa el csrf() de spring-security-test, que cambia el repositorio de tokens del contexto
 * compartido y deja sin la cookie a las pruebas que vienen despues.
 */
public final class SesionEnCookies {

    private SesionEnCookies() {
    }

    /** El POST del login, con el token CSRF que el login pide. */
    public static MockHttpServletRequestBuilder login() {
        return MockMvcRequestBuilders.post("/api/v1/auth/login").with(conCsrf());
    }

    /** El JWT que dejo la respuesta en la cookie de acceso. */
    public static String acceso(ResultActions resultado) {
        return cookie(resultado.andReturn(), CookiesDeSesion.ACCESO);
    }

    /** El refresh token que dejo la respuesta en su cookie. */
    public static String refresco(ResultActions resultado) {
        return cookie(resultado.andReturn(), CookiesDeSesion.REFRESCO);
    }

    /** Una peticion con la sesion de ese access token: su cookie y el token CSRF. */
    public static RequestPostProcessor conSesion(String accessToken) {
        return conCookie(new Cookie(CookiesDeSesion.ACCESO, accessToken));
    }

    /** Una peticion con el refresh token en su cookie, y el token CSRF. */
    public static RequestPostProcessor conRefresco(String refreshToken) {
        return conCookie(new Cookie(CookiesDeSesion.REFRESCO, refreshToken));
    }

    /** El token CSRF de un navegador: el mismo valor en la cookie XSRF-TOKEN y en la cabecera X-XSRF-TOKEN. */
    public static RequestPostProcessor conCsrf() {
        return peticion -> {
            String token = UUID.randomUUID().toString();
            agregar(peticion, new Cookie("XSRF-TOKEN", token));
            peticion.addHeader("X-XSRF-TOKEN", token);
            return peticion;
        };
    }

    private static RequestPostProcessor conCookie(Cookie cookie) {
        return peticion -> {
            agregar(peticion, cookie);
            return conCsrf().postProcessRequest(peticion);
        };
    }

    private static void agregar(MockHttpServletRequest peticion, Cookie cookie) {
        Cookie[] actuales = peticion.getCookies();
        peticion.setCookies(actuales == null ? new Cookie[] {cookie}
                : Stream.concat(Arrays.stream(actuales), Stream.of(cookie)).toArray(Cookie[]::new));
    }

    private static String cookie(MvcResult resultado, String nombre) {
        MockHttpServletResponse respuesta = resultado.getResponse();
        Cookie cookie = respuesta.getCookie(nombre);
        if (cookie == null) {
            throw new AssertionError("La respuesta no dejo la cookie " + nombre + ": " + respuesta.getStatus());
        }
        return cookie.getValue();
    }
}
