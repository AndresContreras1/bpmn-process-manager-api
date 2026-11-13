package com.facimus.procesos.modelado.service.impl;

import java.util.Map;

import com.facimus.procesos.common.CamposInvalidosException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.service.UsuarioService;

/**
 * PR 70 y PR 71: la persona que un nodo nombra para hacer una tarea, la fija de una asignacion o el supervisor de un
 * escalamiento, tiene que poder completarla: activa, y administradora o editora de la tienda. Si no, es un 400 en la
 * propiedad que la nombra, con la misma razon para otra tienda que para quien no existe.
 */
final class PersonaQueCompleta {

    static final String NO_ES_DE_LA_TIENDA = "No es una persona de la organización.";
    static final String NO_COMPLETA = "No puede completar tareas: está desactivada o solo lee.";

    private PersonaQueCompleta() {
    }

    static Long exigir(UsuarioService usuarioService, Long empresaId, Long personaId, String titulo,
            String propiedad) {
        UsuarioResponse persona;
        try {
            persona = usuarioService.obtener(empresaId, personaId);
        } catch (RecursoNoEncontradoException _) {
            throw new CamposInvalidosException(titulo, Map.of(propiedad, NO_ES_DE_LA_TIENDA));
        }
        if (!persona.activo() || !persona.rolAcceso().completaTareas()) {
            throw new CamposInvalidosException(titulo, Map.of(propiedad, NO_COMPLETA));
        }
        return personaId;
    }
}
