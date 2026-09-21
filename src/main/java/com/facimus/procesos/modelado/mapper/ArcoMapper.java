package com.facimus.procesos.modelado.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.facimus.procesos.modelado.dto.response.ArcoResponse;
import com.facimus.procesos.modelado.model.Arco;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ArcoMapper {

    @Mapping(target = "origenId", source = "origen.id")
    @Mapping(target = "destinoId", source = "destino.id")
    @Mapping(target = "poolId", source = "pool.id")
    ArcoResponse toResponse(Arco arco);

    List<ArcoResponse> toResponses(List<Arco> arcos);
}
