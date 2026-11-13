package com.facimus.procesos.modelado.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.facimus.procesos.common.formularios.Formulario;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.AsignacionResponse;
import com.facimus.procesos.modelado.dto.response.FormularioResponse;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.TipoAsignacion;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ActividadMapper {

    @Mapping(target = "laneId", source = "lane.id")
    @Mapping(target = "padreId", source = "padre.id")
    @Mapping(target = "procesoLlamadoId", source = "procesoLlamado.id")
    @Mapping(target = "asignacion", source = ".")
    ActividadResponse toResponse(Actividad actividad);

    List<ActividadResponse> toResponses(List<Actividad> actividades);

    /** PR 69: el formulario con sus condiciones compiladas (D36). */
    default FormularioResponse toFormulario(Formulario formulario) {
        return FormularioResponse.de(formulario);
    }

    /** PR 70: a quien le llega la tarea; ninguna cuando espera en la bandeja de su rol, que es lo de siempre. */
    default AsignacionResponse toAsignacion(Actividad actividad) {
        TipoAsignacion tipo = actividad.getTipoAsignacion();
        return tipo == null || tipo == TipoAsignacion.ROL ? null
                : new AsignacionResponse(tipo, actividad.getAsignacionUsuarioId(), actividad.getAsignacionCampo());
    }
}
