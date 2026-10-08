package com.facimus.procesos.common.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
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
 *
 * <p>El despacho de error conserva el metodo de la peticion que fallo, asi que la pagina responde a los que usa la
 * API. Van nombrados y en dos grupos, los que leen y los que escriben, como cualquier otra ruta: ninguna acepta un
 * metodo que no dice.
 */
@Hidden
@RestController
@RequestMapping("${server.error.path:${error.path:/error}}")
public class ErroresController implements ErrorController {

    static final String ERROR_INTERNO = "Ocurrió un error inesperado. Intenta nuevamente más tarde.";

    private static final Logger log = LoggerFactory.getLogger(ErroresController.class);

    @RequestMapping(method = {RequestMethod.GET, RequestMethod.HEAD, RequestMethod.OPTIONS})
    public ResponseEntity<ProblemDetail> errorAlLeer(HttpServletRequest request) {
        return error(request);
    }

    @RequestMapping(method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<ProblemDetail> errorAlEscribir(HttpServletRequest request) {
        return error(request);
    }

    private static ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        HttpStatus estado = estadoDe(request);
        if (estado.is5xxServerError()) {
            log.error("Error fuera de los controllers en {}", request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI),
                    request.getAttribute(RequestDispatcher.ERROR_EXCEPTION));
        }
        return ResponseEntity.status(estado)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problema(estado));
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
