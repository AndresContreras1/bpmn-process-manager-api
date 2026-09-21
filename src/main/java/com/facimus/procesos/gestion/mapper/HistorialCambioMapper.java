package com.facimus.procesos.gestion.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.facimus.procesos.gestion.dto.response.HistorialCambioResponse;
import com.facimus.procesos.gestion.model.HistorialCambio;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface HistorialCambioMapper {

    @Mapping(target = "autorNombre", source = "autor.nombre")
    HistorialCambioResponse toResponse(HistorialCambio historial);

    List<HistorialCambioResponse> toResponses(List<HistorialCambio> historial);
}
