package com.facimus.procesos.modelado.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.facimus.procesos.common.formularios.Formulario;
import com.facimus.procesos.modelado.dto.response.EscalamientoResponse;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.dto.response.FormularioResponse;
import com.facimus.procesos.modelado.dto.response.PlazoResponse;
import com.facimus.procesos.modelado.model.Evento;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface EventoMapper {

    @Mapping(target = "laneId", source = "lane.id")
    @Mapping(target = "adjuntoAId", source = "adjuntoA.id")
    @Mapping(target = "padreId", source = "padre.id")
    @Mapping(target = "plazo", source = ".")
    @Mapping(target = "escalamiento", source = ".")
    EventoResponse toResponse(Evento evento);

    List<EventoResponse> toResponses(List<Evento> eventos);

    /** PR 71: el plazo del caso, en un evento de inicio; ninguno si no lo tiene. */
    default PlazoResponse toPlazo(Evento evento) {
        return PlazosDelModelo.plazo(evento);
    }

    /** PR 71: lo que hace un temporizador de borde que no interrumpe; nada si solo abre su camino. */
    default EscalamientoResponse toEscalamiento(Evento evento) {
        return evento.getEscalamiento() == null ? null
                : new EscalamientoResponse(evento.getEscalamiento(), evento.getEscalarA());
    }

    /** PR 69: el formulario con sus condiciones compiladas (D36). */
    default FormularioResponse toFormulario(Formulario formulario) {
        return FormularioResponse.de(formulario);
    }
}
