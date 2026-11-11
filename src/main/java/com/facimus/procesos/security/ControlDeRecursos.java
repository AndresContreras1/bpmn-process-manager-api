package com.facimus.procesos.security;

import java.util.Map;
import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.gestion.service.Actor;
import com.facimus.procesos.gestion.service.Permisos;
import com.facimus.procesos.gestion.service.ProcesoDeElemento;
import com.facimus.procesos.gestion.service.Recurso;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** PR 60: el punto por donde pasa toda peticion sobre un proceso, una carpeta o un elemento de un diagrama. */
@Component
@RequiredArgsConstructor
public class ControlDeRecursos {

    private final Permisos permisos;
    private final ProcesoDeElemento elementos;

    void comprobar(HttpServletRequest peticion) {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !(autenticacion.getPrincipal() instanceof ApiPrincipal principal)) {
            return;
        }
        String patron = (String) peticion.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        Optional<ReglaDeRecurso> regla = ReglaDeRecurso.de(peticion.getMethod(), patron, peticion.getParameter("para"));
        if (regla.isEmpty()) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, String> variables = (Map<String, String>) peticion
                .getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        Long id = primerId(patron, variables);
        if (id != null) {
            recurso(regla.get(), principal.empresaId(), id).ifPresent(recurso -> permisos.exigir(
                    new Actor(principal.usuarioId(), principal.empresaId(), principal.rol()), regla.get().accion(),
                    recurso));
        }
    }

    private Optional<Recurso> recurso(ReglaDeRecurso regla, Long empresaId, Long id) {
        return switch (regla.clase()) {
            case PROCESO -> Optional.of(Recurso.proceso(id));
            case CARPETA -> Optional.of(Recurso.carpeta(id));
            case ELEMENTO -> elementos.procesoDe(empresaId, regla.tipo(), id).map(Recurso::proceso);
        };
    }

    /** La primera variable del patron es la del recurso: en /procesos/{procesoId}/versiones/{numero}, el proceso. */
    private static Long primerId(String patron, Map<String, String> variables) {
        int abre = patron.indexOf('{');
        int cierra = patron.indexOf('}', abre);
        if (abre < 0 || cierra < 0 || variables == null) {
            return null;
        }
        String valor = variables.get(patron.substring(abre + 1, cierra));
        try {
            return valor == null ? null : Long.valueOf(valor);
        } catch (NumberFormatException noEsUnId) {
            // La ruta no se resolvera a un recurso: que responda el controlador, con su 400
            return null;
        }
    }
}
