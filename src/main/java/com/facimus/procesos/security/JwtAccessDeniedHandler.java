package com.facimus.procesos.security;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import com.facimus.procesos.common.api.Problemas;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** 403: el token es valido pero el rol no alcanza, o a lo que manda el navegador le falta el token CSRF. */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    public JwtAccessDeniedHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        ProblemDetail problem = accessDeniedException instanceof CsrfException
                ? Problemas.de(HttpStatus.FORBIDDEN, "Sin token CSRF", "La peticion tiene que llevar en "
                        + "X-XSRF-TOKEN el valor de la cookie XSRF-TOKEN, que da GET /api/v1/auth/csrf.")
                : Problemas.de(HttpStatus.FORBIDDEN, "Sin permisos", "No tienes permisos para realizar esta "
                        + "operacion.");
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }
}
