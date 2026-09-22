package com.facimus.procesos.modelado.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.facimus.procesos.modelado.dto.response.CorrelacionResponse;
import com.facimus.procesos.modelado.model.Correlacion;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CorrelacionMapper {

    @Mapping(target = "mensajeId", source = "mensaje.id")
    CorrelacionResponse toResponse(Correlacion correlacion);

    List<CorrelacionResponse> toResponses(List<Correlacion> correlaciones);
}
