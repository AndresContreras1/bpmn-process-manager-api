package com.facimus.procesos.security;

import java.time.Instant;
import java.util.EnumSet;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.common.security.Factor;

public final class ApiPrincipalRequestPostProcessor {

    /** La sesion del principal de los tests de controllers. */
    public static final String SESION = "sesion-de-prueba";

    private ApiPrincipalRequestPostProcessor() {
    }

    public static RequestPostProcessor principal(RolAcceso rol) {
        ApiPrincipal principal = new ApiPrincipal(1L, 1L, rol, "test@acme.com", SESION, false);
        // Recien entrado con su contrasena: lo que pide una verificacion reciente la encuentra.
        var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                new TokenDeAcceso(principal, EnumSet.of(Factor.PASSWORD), Instant.now()).autoridades());
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }
}
