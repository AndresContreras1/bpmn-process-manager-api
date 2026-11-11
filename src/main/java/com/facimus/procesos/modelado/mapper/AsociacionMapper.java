package com.facimus.procesos.modelado.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import com.facimus.procesos.modelado.dto.response.AsociacionResponse;
import com.facimus.procesos.modelado.model.Asociacion;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AsociacionMapper {

    AsociacionResponse toResponse(Asociacion asociacion);

    List<AsociacionResponse> toResponses(List<Asociacion> asociaciones);
}
