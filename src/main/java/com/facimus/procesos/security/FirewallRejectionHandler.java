package com.facimus.procesos.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * 400: el firewall de Spring Security rechaza la URL (por ejemplo con "//" o ";") antes de que llegue a la API.
 * Sin este handler la respuesta seria el error por defecto de Spring Boot, no un ProblemDetail.
 */
@Component
public class FirewallRejectionHandler implements RequestRejectedHandler {

    private static final Logger log = LoggerFactory.getLogger(FirewallRejectionHandler.class);

    private final JsonMapper jsonMapper;

    public FirewallRejectionHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            RequestRejectedException requestRejectedException) throws IOException {
        log.debug("Solicitud rechazada por el firewall: {}", requestRejectedException.getMessage());
        // Sin instance: la URL rechazada puede no ser una URI valida (por ejemplo con "\")
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "La URL de la solicitud no es valida.");
        problem.setTitle("Solicitud rechazada");

        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }
}
