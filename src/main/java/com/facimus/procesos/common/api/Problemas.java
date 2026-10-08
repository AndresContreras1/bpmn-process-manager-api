package com.facimus.procesos.common.api;

import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Cada Problem Details de la API se arma aqui, tambien los que escriben los filtros de seguridad antes de que la
 * peticion llegue a un controller: asi todos llevan lo mismo. Ademas de lo que pide la RFC 9457, cada uno lleva
 * {@code requestId}, el mismo id de la cabecera X-Request-Id y de las lineas del log de esa peticion.
 */
public final class Problemas {

    /** La propiedad del ProblemDetail con el id de la peticion. */
    public static final String ID_DE_PETICION = "requestId";

    private Problemas() {
    }

    public static ProblemDetail de(HttpStatusCode estado, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        return conId(problema);
    }

    /** Le pone el id de la peticion en curso a un Problem Details que armo otro, por ejemplo Spring. */
    public static ProblemDetail conId(ProblemDetail problema) {
        String id = MDC.get(IdDePeticionFilter.CLAVE_EN_EL_LOG);
        if (id != null) {
            problema.setProperty(ID_DE_PETICION, id);
        }
        return problema;
    }
}
