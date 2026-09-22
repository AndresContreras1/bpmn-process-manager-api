package com.facimus.procesos.common;

/**
 * 401: el refresh token no sirve porque no existe, vencio, ya se habia usado o su sesion se cerro. El cliente vuelve
 * al login.
 */
public class SesionInvalidaException extends RuntimeException {

    public SesionInvalidaException() {
        super("La sesión expiró o se cerró. Inicia sesión de nuevo.");
    }
}
