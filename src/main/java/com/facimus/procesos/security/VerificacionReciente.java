package com.facimus.procesos.security;

import java.time.Duration;
import java.util.Arrays;

import org.springframework.security.authorization.AllRequiredFactorsAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.RequiredFactor;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import com.facimus.procesos.common.security.Factor;

/**
 * PR 49: lo que pide haber confirmado la identidad hace poco, con cualquiera de los factores de la sesion. Es el
 * modelo de factores de Spring Security 7: cada factor del token esta fechado en la ultima verificacion, y la regla
 * lo exige mas nuevo que la ventana. Si no lo es, la respuesta es un 403 "Verificación requerida", y
 * {@code POST /api/v1/auth/reautenticacion} lo arregla.
 */
final class VerificacionReciente {

    /** Lo que dura una verificacion para lo que la pide. */
    static final Duration VENTANA = Duration.ofMinutes(10);

    private VerificacionReciente() {
    }

    /** Alguno de los factores de la sesion, verificado dentro de la ventana. */
    @SuppressWarnings("unchecked")
    static AuthorizationManager<RequestAuthorizationContext> requerida() {
        AllRequiredFactorsAuthorizationManager<RequestAuthorizationContext>[] alguno = Arrays.stream(Factor.values())
                .map(factor -> AllRequiredFactorsAuthorizationManager.<RequestAuthorizationContext>builder()
                        .requireFactor(RequiredFactor.withAuthority(factor.autoridad()).validDuration(VENTANA).build())
                        .build())
                .toArray(AllRequiredFactorsAuthorizationManager[]::new);
        return AllRequiredFactorsAuthorizationManager.anyOf(alguno);
    }
}
