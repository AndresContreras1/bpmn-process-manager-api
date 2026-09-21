package com.facimus.procesos.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.facimus.procesos.gestion.model.RolAcceso;

public final class ApiPrincipalRequestPostProcessor {

    private ApiPrincipalRequestPostProcessor() {
    }

    public static RequestPostProcessor principal(RolAcceso rol) {
        ApiPrincipal principal = new ApiPrincipal(1L, 1L, rol, "test@acme.com");
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.authorities());
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }
}
