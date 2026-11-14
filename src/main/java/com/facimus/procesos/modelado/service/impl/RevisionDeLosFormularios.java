package com.facimus.procesos.modelado.service.impl;

import java.util.Map;

import com.facimus.procesos.common.formularios.EsquemaDelFormulario;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.dto.response.FormularioResponse;
import com.facimus.procesos.modelado.model.CodigoDeDiagnostico;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;

/**
 * PR 69: E-22, los formularios. Guardarlos por el API ya los revisa, pero un archivo BPMN importado o guardado desde el
 * editor trae el suyo sin pasar por ahi: lo que se publica se revisa aqui, con las mismas reglas.
 */
final class RevisionDeLosFormularios {

    private RevisionDeLosFormularios() {
    }

    static void revisar(DiagramaResponse diagrama, Hallazgos hallazgos) {
        for (ActividadResponse actividad : diagrama.actividades()) {
            revisar("Actividad \"" + actividad.nombre() + "\"", actividad.id(), actividad.formulario(),
                    actividad.tipoActividad() == TipoActividad.USUARIO, hallazgos);
        }
        for (EventoResponse evento : diagrama.eventos()) {
            revisar("Evento \"" + evento.nombre() + "\"", evento.id(), evento.formulario(),
                    evento.tipoEvento() == TipoEvento.INICIO, hallazgos);
        }
    }

    private static void revisar(String etiqueta, Long id, FormularioResponse formulario, boolean loLlenaUnaPersona,
            Hallazgos hallazgos) {
        if (formulario == null) {
            return;
        }
        if (!loLlenaUnaPersona) {
            hallazgos.anotar(CodigoDeDiagnostico.E22, etiqueta, id,
                    "Tiene formulario, pero no lo llena nadie: solo lo llevan las tareas de usuario y los inicios "
                            + "simples.",
                    "Quita el formulario, o cambia el nodo a una tarea de usuario o a un inicio simple.");
            return;
        }
        Map<String, String> problemas = EsquemaDelFormulario.problemas(formulario.formulario());
        problemas.forEach((propiedad, problema) -> hallazgos.anotar(CodigoDeDiagnostico.E22, etiqueta, id,
                "El formulario no esta bien armado: " + propiedad + ": " + problema,
                "Corrige el campo en el formulario del nodo."));
    }
}
