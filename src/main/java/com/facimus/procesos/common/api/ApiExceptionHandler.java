package com.facimus.procesos.common.api;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /** Propiedad del ProblemDetail con el mensaje de cada campo invalido, para que el cliente lo muestre junto al campo. */
    static final String ERRORES = "errors";

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
        Map<String, String> errores = new TreeMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> agregar(errores, error.getField(), error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> agregar(errores, error.getObjectName(), error.getDefaultMessage()));
        return solicitudInvalida("Validación fallida", "Uno o más campos no son válidos.", errores, req);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest req) {
        Map<String, String> errores = new TreeMap<>();
        ex.getParameterValidationResults().forEach(resultado -> resultado.getResolvableErrors()
                .forEach(error -> agregar(errores, resultado.getMethodParameter().getParameterName(),
                        error.getDefaultMessage())));
        return solicitudInvalida("Parámetro inválido",
                "Uno o más parámetros no cumplen las restricciones de la solicitud.", errores, req);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest req) {
        Map<String, String> errores = new TreeMap<>();
        agregar(errores, ex.getPropertyName(), mensajeDeFormato(ex.getRequiredType()));
        return solicitudInvalida("Parámetro inválido", "El valor enviado no tiene el formato esperado.", errores, req);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest req) {
        Map<String, String> errores = erroresDeLectura(ex);
        String detalle = errores.isEmpty()
                ? "El cuerpo de la solicitud no contiene JSON válido."
                : "Uno o más campos del JSON no se pueden leer.";
        return solicitudInvalida("JSON inválido", detalle, errores, req);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail manejarErrorInesperado(Exception ex, WebRequest req) {
        log.error("Error inesperado en {}", req.getDescription(false), ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Ocurrió un error inesperado. Intenta nuevamente más tarde.", req);
    }

    /** JSON bien formado pero con un campo que no encaja: se dice cual. Si el JSON esta roto no hay campo que senalar. */
    private static Map<String, String> erroresDeLectura(HttpMessageNotReadableException ex) {
        if (!(ex.getCause() instanceof DatabindException error) || error.getPath().isEmpty()) {
            return Map.of();
        }
        String campo = error.getPath().stream()
                .map(JacksonException.Reference::getPropertyName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("."));
        String mensaje = switch (error) {
            case UnrecognizedPropertyException desconocido -> "El campo no existe en esta operación.";
            case InvalidFormatException formato -> mensajeDeFormato(formato.getTargetType());
            default -> "El valor no tiene el formato esperado.";
        };
        return campo.isEmpty() ? Map.of() : Map.of(campo, mensaje);
    }

    private static String mensajeDeFormato(Class<?> tipo) {
        if (tipo != null && tipo.isEnum()) {
            return "Valor no permitido. Valores válidos: " + Arrays.stream(tipo.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", ")) + ".";
        }
        return "El valor no tiene el formato esperado.";
    }

    private static void agregar(Map<String, String> errores, String campo, String mensaje) {
        if (campo != null) {
            errores.merge(campo, Objects.requireNonNullElse(mensaje, "Valor no válido."), (a, b) -> a + " " + b);
        }
    }

    private ResponseEntity<Object> solicitudInvalida(String titulo, String detalle, Map<String, String> errores,
            WebRequest req) {
        ProblemDetail problema = construir(HttpStatus.BAD_REQUEST, titulo, detalle, req);
        if (!errores.isEmpty()) {
            problema.setProperty(ERRORES, errores);
        }
        return ResponseEntity.badRequest().body(problema);
    }

    private ProblemDetail construir(HttpStatus status, String titulo, String detalle, WebRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detalle);
        pd.setTitle(titulo);
        pd.setInstance(URI.create(req.getDescription(false).replace("uri=", "")));
        return pd;
    }
}
