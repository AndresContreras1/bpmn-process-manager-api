package com.facimus.procesos.common.api.controller;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.api.Problemas;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * La pagina de error del servidor, en Problem Details como el resto de la API. Aqui llega lo que falla fuera de un
 * controller, por ejemplo un filtro que lanza una excepcion o una peticion que el contenedor rechaza: sin esto, la
 * respuesta seria el cuerpo propio de Spring Boot, con otra forma y sin el id de la peticion.
 *
 * <p>Un 500 no cuenta que paso por dentro: el detalle queda en el log, en la linea que lleva el mismo id que la
 * respuesta.
 */
@Hidden
@RestController
@RequestMapping("${server.error.path:${error.path:/error}}")
public class ErroresController implements ErrorController {

    static final String ERROR_INTERNO = "Ocurrió un error inesperado. Intenta nuevamente más tarde.";

    private static final Logger log = LoggerFactory.getLogger(ErroresController.class);

    @RequestMapping
    public ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        HttpStatus estado = estadoDe(request);
        if (estado.is5xxServerError()) {
            log.error("Error fuera de los controllers en {}", request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI),
                    (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION));
        }
        ProblemDetail problema = problema(estado);
        conLaRutaQueFallo(problema, request);
        return ResponseEntity.status(estado)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problema);
    }

    /**
     * El instance es la ruta que fallo, no /error. Una ruta que ni siquiera es una URI valida (el contenedor rechaza
     * algunas por eso) se queda sin instance antes que dar una mentira.
     */
    private static void conLaRutaQueFallo(ProblemDetail problema, HttpServletRequest request) {
        if (request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String ruta) {
            try {
                problema.setInstance(URI.create(ruta));
            } catch (IllegalArgumentException rutaQueNoEsUnaUri) {
                log.debug("La ruta que fallo no es una URI: {}", rutaQueNoEsUnaUri.getMessage());
            }
        }
    }

    /** Quien pide /error directamente, sin un error detras, no encuentra nada. */
    private static HttpStatus estadoDe(HttpServletRequest request) {
        if (!(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer codigo)) {
            return HttpStatus.NOT_FOUND;
        }
        HttpStatus estado = HttpStatus.resolve(codigo);
        return estado != null ? estado : HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static ProblemDetail problema(HttpStatus estado) {
        if (estado.is5xxServerError()) {
            return Problemas.de(estado, "Error interno", ERROR_INTERNO);
        }
        return switch (estado) {
            case NOT_FOUND -> Problemas.de(estado, "Recurso no encontrado", "No hay nada en esta dirección.");
            case BAD_REQUEST -> Problemas.de(estado, "Solicitud inválida", "El servidor no pudo leer la solicitud.");
            default -> Problemas.de(estado, estado.getReasonPhrase(), "La solicitud no se pudo atender.");
        };
    }
}
