package com.facimus.procesos.ejecucion.service.impl;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.facimus.procesos.common.condiciones.Variables;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.modelado.dto.response.AsignacionResponse;

import lombok.RequiredArgsConstructor;

/**
 * PR 70: a quien le llega una tarea de usuario cuando un caso pasa por ella, con la regla que congelo su version. Si
 * la regla no dice a nadie que la pueda completar, la tarea se queda en la bandeja del rol de su lane, que es lo de
 * siempre, y la nota dice por que: un caso no se detiene porque una persona se fue de la tienda.
 */
@Component
@RequiredArgsConstructor
class AsignacionAlCrear {

    private final PersonasDeLaTarea personas;

    /**
     * A quien le llega la tarea.
     *
     * @param usuarioId la persona que la tiene; vacia si espera en la bandeja del rol
     * @param como como le llego, para la linea de tiempo; vacio si espera en el rol
     * @param porQueNo por que la regla no la dio a nadie, para la linea de tiempo; vacio si la dio o no habia regla
     */
    record Destino(Long usuarioId, String como, String porQueNo) {

        static final Destino AL_ROL = new Destino(null, null, null);

        static Destino noSePudo(String porQue) {
            return new Destino(null, null, porQue);
        }
    }

    Destino decidir(Caso caso, GrafoDeVersion grafo, NodoDeLaVersion nodo, Variables variables) {
        Optional<AsignacionResponse> regla = grafo.asignacionDe(nodo.id());
        if (regla.isEmpty() || regla.get().tipo() == null) {
            return Destino.AL_ROL;
        }
        AsignacionResponse asignacion = regla.get();
        Long empresaId = caso.getEmpresa().getId();
        return switch (asignacion.tipo()) {
            case ROL -> Destino.AL_ROL;
            case PERSONA -> aLaPersona(empresaId, asignacion.usuarioId(), "asignada a una persona fija");
            case INICIADOR -> caso.getIniciadoPor() == null
                    ? Destino.noSePudo("Iba para quien abrio el caso, y este caso no lo abrio una persona.")
                    : aLaPersona(empresaId, caso.getIniciadoPor(), "para quien abrio el caso");
            case CAMPO -> deLaVariable(empresaId, asignacion.campo(), variables);
        };
    }

    private Destino deLaVariable(Long empresaId, String campo, Variables variables) {
        Optional<Object> valor = campo == null ? Optional.empty() : variables.valor(campo);
        if (valor.isEmpty()) {
            return Destino.noSePudo("Iba para la persona de \"" + campo + "\", y el caso no tiene esa variable.");
        }
        if (!(valor.get() instanceof Integer || valor.get() instanceof Long)) {
            return Destino.noSePudo("Iba para la persona de \"" + campo + "\", y esa variable no es una persona.");
        }
        return aLaPersona(empresaId, ((Number) valor.get()).longValue(), "por la variable \"" + campo + "\"");
    }

    private Destino aLaPersona(Long empresaId, Long usuarioId, String como) {
        if (usuarioId == null) {
            return Destino.noSePudo("Iba para una persona fija, y la version no dice cual.");
        }
        return personas.siPuedeTenerla(empresaId, usuarioId)
                .map(persona -> new Destino(persona, como, null))
                .orElseGet(() -> Destino.noSePudo("Iba para el usuario " + usuarioId + ", que no puede tener tareas: "
                        + "esta desactivado, solo lee o no es de la organizacion."));
    }
}
