package com.facimus.procesos.common.api;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler{

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail manejarNoEncontrado(RecursoNoEncontradoException ex, WebRequest req) {
        return construir(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage(), req);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ProblemDetail manejarReglaNegocio(ReglaNegocioException ex, WebRequest req) {
        return construir(HttpStatus.CONFLICT, "Regla de negocio violada", ex.getMessage(), req);
    }

    /**
     * Ultima barrera de las restricciones de la base (por ejemplo el correo unico): dos peticiones simultaneas
     * pueden pasar la validacion del service antes de guardar. El detalle de la base queda en el log, no en la respuesta.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail manejarConflictoDeDatos(DataIntegrityViolationException ex, WebRequest req) {
        log.warn("Restriccion de datos violada en {}: {}", req.getDescription(false), ex.getMostSpecificCause().getMessage());
        return construir(HttpStatus.CONFLICT, "Conflicto de datos",
                "La operacion choca con un dato existente, por ejemplo un valor que debe ser unico.", req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail manejarNoAutenticado(AuthenticationException ex, WebRequest req) {
        return construir(HttpStatus.UNAUTHORIZED, "No autenticado", "Credenciales inválidas o token ausente", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail manejarSinPermiso(AccessDeniedException ex, WebRequest req) {
        return construir(HttpStatus.FORBIDDEN, "Sin permisos", "No tienes permisos para esta operación", req);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest req) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(construir(HttpStatus.BAD_REQUEST, "Validación fallida", detalle, req));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest req) {
        return ResponseEntity.badRequest()
                .body(construir(HttpStatus.BAD_REQUEST, "Parámetro inválido",
                        "Uno o más parámetros no cumplen las restricciones de la solicitud.", req));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest req) {
        return ResponseEntity.badRequest()
                .body(construir(HttpStatus.BAD_REQUEST, "Parámetro inválido",
                        "El valor enviado no tiene el formato esperado.", req));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest req) {
        return ResponseEntity.badRequest()
                .body(construir(HttpStatus.BAD_REQUEST, "JSON inválido",
                        "El cuerpo de la solicitud no contiene JSON válido.", req));
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail manejarErrorInesperado(Exception ex, WebRequest req) {
        log.error("Error inesperado en {}", req.getDescription(false), ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Ocurrió un error inesperado. Intenta nuevamente más tarde.", req);
    }

    private ProblemDetail construir(HttpStatus status, String titulo, String detalle, WebRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detalle);
        pd.setTitle(titulo);
        pd.setInstance(java.net.URI.create(req.getDescription(false).replace("uri=", "")));
        return pd;
    }
}
