package com.facimus.procesos.modelado.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.facimus.procesos.modelado.dto.response.PoolResponse;
import com.facimus.procesos.modelado.model.Pool;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PoolMapper {

    @Mapping(target = "procesoId", source = "proceso.id")
    PoolResponse toResponse(Pool pool);

    List<PoolResponse> toResponses(List<Pool> pools);
}
