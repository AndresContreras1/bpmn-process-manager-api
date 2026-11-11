package com.facimus.procesos.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** PR 60: registra el control despues de Spring Security. En una prueba de un solo controlador no hay evaluador. */
@Configuration
public class ControlDeRecursosConfig implements WebMvcConfigurer {

    private final ObjectProvider<ControlDeRecursos> control;

    public ControlDeRecursosConfig(ObjectProvider<ControlDeRecursos> control) {
        this.control = control;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest peticion, HttpServletResponse respuesta, Object manejador) {
                control.ifAvailable(unico -> unico.comprobar(peticion));
                return true;
            }
        }).addPathPatterns("/api/v1/**");
    }
}
