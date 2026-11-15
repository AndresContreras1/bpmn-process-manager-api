package com.facimus.procesos.modelado.service.impl;

import java.util.HashSet;
import java.util.Set;

import com.facimus.procesos.common.condiciones.NombreDeVariable;
import com.facimus.procesos.common.formularios.CampoDeFormulario;
import com.facimus.procesos.common.formularios.TipoDeCampo;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.AsignacionResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.dto.response.FormularioResponse;
import com.facimus.procesos.modelado.model.CodigoDeDiagnostico;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoAsignacion;
import com.facimus.procesos.modelado.model.TipoEvento;

/**
 * PR 70: E-23 y A-17, a quien le llega cada tarea. Guardar la asignacion por el API ya la revisa, pero un BPMN
 * importado o guardado desde el editor trae la suya sin pasar por ahi. Que la persona fija siga pudiendo completar
 * tareas no se mira aqui, que solo lee el diagrama: lo mira el motor al crear la tarea, y si no puede, la deja en la
 * bandeja del rol.
 */
final class RevisionDeLasAsignaciones {

    private static final String ACTIVIDAD = "Actividad \"";

    private RevisionDeLasAsignaciones() {
    }

    static void revisar(DiagramaResponse diagrama, Hallazgos hallazgos) {
        Set<String> personas = rutasDePersonas(diagrama);
        for (ActividadResponse actividad : diagrama.actividades()) {
            AsignacionResponse asignacion = actividad.asignacion();
            if (asignacion == null || asignacion.tipo() == null || asignacion.tipo() == TipoAsignacion.ROL) {
                continue;
            }
            String etiqueta = ACTIVIDAD + actividad.nombre() + "\"";
            if (actividad.tipoActividad() != TipoActividad.USUARIO) {
                hallazgos.anotar(CodigoDeDiagnostico.E23, etiqueta, actividad.id(),
                        "Tiene asignacion, pero no espera a nadie: solo se asignan las tareas de usuario.",
                        "Devuelvela a la bandeja de su rol, o cambia la actividad a una tarea de usuario.");
            } else if (asignacion.tipo() == TipoAsignacion.PERSONA && asignacion.usuarioId() == null) {
                hallazgos.anotar(CodigoDeDiagnostico.E23, etiqueta, actividad.id(),
                        "Esta asignada a una persona fija, pero no dice a quien.",
                        "Elige la persona en la asignacion de la tarea.");
            } else if (asignacion.tipo() == TipoAsignacion.CAMPO) {
                revisarCampo(etiqueta, actividad.id(), asignacion.campo(), personas, hallazgos);
            }
        }
    }

    private static void revisarCampo(String etiqueta, Long id, String campo, Set<String> personas,
            Hallazgos hallazgos) {
        if (!TipoAsignacion.esRutaDeVariable(campo)) {
            hallazgos.anotar(CodigoDeDiagnostico.E23, etiqueta, id,
                    "Esta asignada a la persona de una variable, pero la variable no esta bien escrita.",
                    "Escribe la variable como la nombra una condicion: aprobador, tarea.revisarPedido.aprobador.");
        } else if (!personas.contains(campo)) {
            hallazgos.anotar(CodigoDeDiagnostico.A17, etiqueta, id,
                    "Esta asignada a la persona de \"" + campo + "\", y ningun campo PERSONA de un formulario del "
                            + "diagrama la escribe: si al llegar el caso no la trae, la tarea queda en la bandeja del "
                            + "rol.",
                    "Pon un campo PERSONA en el formulario del inicio o de una tarea anterior, o revisa el nombre.");
        }
    }

    /**
     * Las variables que escribe un campo PERSONA: las del formulario del inicio quedan con su nombre, y las de una
     * tarea, bajo {@code tarea.<nombre de la tarea en camello>}.
     */
    private static Set<String> rutasDePersonas(DiagramaResponse diagrama) {
        Set<String> rutas = new HashSet<>();
        for (EventoResponse evento : diagrama.eventos()) {
            if (evento.tipoEvento() == TipoEvento.INICIO) {
                personas(evento.formulario()).forEach(rutas::add);
            }
        }
        for (ActividadResponse actividad : diagrama.actividades()) {
            personas(actividad.formulario())
                    .forEach(campo -> rutas.add(NombreDeVariable.deLaTarea(actividad.nombre(), campo)));
        }
        return rutas;
    }

    private static Iterable<String> personas(FormularioResponse formulario) {
        if (formulario == null) {
            return Set.of();
        }
        return formulario.formulario().campos().stream()
                .filter(campo -> campo.tipo() == TipoDeCampo.PERSONA)
                .map(CampoDeFormulario::nombre)
                .toList();
    }
}
