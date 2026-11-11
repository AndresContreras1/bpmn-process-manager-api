package com.facimus.procesos.modelado.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.facimus.procesos.modelado.dto.response.ArtefactoResponse;
import com.facimus.procesos.modelado.model.Artefacto;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ArtefactoMapper {

    @Mapping(target = "poolId", source = "pool.id")
    @Mapping(target = "laneId", source = "lane.id")
    ArtefactoResponse toResponse(Artefacto artefacto);

    List<ArtefactoResponse> toResponses(List<Artefacto> artefactos);
}
