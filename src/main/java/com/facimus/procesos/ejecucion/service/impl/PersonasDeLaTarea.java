package com.facimus.procesos.ejecucion.service.impl;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.service.MembresiaRolService;
import com.facimus.procesos.gestion.service.UsuarioService;

import lombok.RequiredArgsConstructor;

/**
 * PR 70: quien puede que con una tarea. La toma quien pertenece al rol de su lane, o un administrador, que reparte el
 * trabajo; se le pasa a quien la puede completar: una persona activa de la tienda que no solo lee.
 */
@Component
@RequiredArgsConstructor
class PersonasDeLaTarea {

    static final String NO_PUEDE_TENERLA = "Esa persona no puede tener tareas: está desactivada o solo lee.";

    private final UsuarioService usuarioService;
    private final MembresiaRolService membresiaRolService;

    /** Quien pide algo de una tarea, con su rol de acceso. Tiene que ser de la tienda. */
    UsuarioResponse quienActua(Long empresaId, Long usuarioId) {
        return usuarioService.obtener(empresaId, usuarioId);
    }

    /** Si quien actua administra la tienda: reparte el trabajo, asi que toma, suelta y reasigna cualquier tarea. */
    static boolean administra(UsuarioResponse persona) {
        return persona.rolAcceso() == RolAcceso.ADMINISTRADOR;
    }

    /** Si puede tomar la tarea que nadie tiene: pertenece a su rol, o administra la tienda. */
    boolean esCandidata(Long empresaId, UsuarioResponse persona, ActividadCaso tarea) {
        if (administra(persona)) {
            return true;
        }
        return tarea.getRolProcesoId() != null
                && membresiaRolService.idsDeLosRolesDe(empresaId, persona.id()).contains(tarea.getRolProcesoId());
    }

    /**
     * A quien se le pasa una tarea: alguien de la tienda (si no, 404) que la pueda completar (si no, 409). Una tarea en
     * manos de quien no puede completarla no la terminaria nadie.
     */
    UsuarioResponse quienRecibe(Long empresaId, Long usuarioId) {
        UsuarioResponse persona = usuarioService.obtener(empresaId, usuarioId);
        if (!puedeTenerla(persona)) {
            throw new ReglaNegocioException(NO_PUEDE_TENERLA);
        }
        return persona;
    }

    /** Para el motor: la persona, si sigue siendo de la tienda y la puede completar; si no, la tarea va al rol. */
    Optional<Long> siPuedeTenerla(Long empresaId, Long usuarioId) {
        try {
            return puedeTenerla(usuarioService.obtener(empresaId, usuarioId)) ? Optional.of(usuarioId)
                    : Optional.empty();
        } catch (RecursoNoEncontradoException _) {
            return Optional.empty();
        }
    }

    private static boolean puedeTenerla(UsuarioResponse persona) {
        return persona.activo() && persona.rolAcceso().completaTareas();
    }
}
