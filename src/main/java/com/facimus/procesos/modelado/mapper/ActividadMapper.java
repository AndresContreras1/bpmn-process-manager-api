package com.facimus.procesos.modelado.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.facimus.procesos.common.formularios.Formulario;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.FormularioResponse;
import com.facimus.procesos.modelado.model.Actividad;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ActividadMapper {

    @Mapping(target = "laneId", source = "lane.id")
    @Mapping(target = "padreId", source = "padre.id")
    @Mapping(target = "procesoLlamadoId", source = "procesoLlamado.id")
    ActividadResponse toResponse(Actividad actividad);

    List<ActividadResponse> toResponses(List<Actividad> actividades);

    /** PR 69: el formulario con sus condiciones compiladas (D36). */
    default FormularioResponse toFormulario(Formulario formulario) {
        return FormularioResponse.de(formulario);
    }
}
