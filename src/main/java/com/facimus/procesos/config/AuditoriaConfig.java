package com.facimus.procesos.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.facimus.procesos.security.ApiPrincipal;

/**
 * Auditoria de Spring Data: al guardar una entidad editable anota quien y cuando la creo y la cambio por ultima vez.
 * Quien sale del token de la peticion; sin token, como en el registro de una tienda o la semilla de dev, queda vacio.
 * Va en una @Configuration propia porque los tests de slices (@WebMvcTest) no las cargan y no tienen JPA.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditor")
public class AuditoriaConfig {

    @Bean
    public AuditorAware<Long> auditor() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(Authentication::getPrincipal)
                .filter(ApiPrincipal.class::isInstance)
                .map(principal -> ((ApiPrincipal) principal).usuarioId());
    }
}
