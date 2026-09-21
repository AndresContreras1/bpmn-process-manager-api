package com.facimus.procesos.modelado.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.facimus.procesos.modelado.dto.response.LaneResponse;
import com.facimus.procesos.modelado.model.Lane;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface LaneMapper {

    @Mapping(target = "poolId", source = "pool.id")
    @Mapping(target = "rolProcesoId", source = "rolProceso.id")
    @Mapping(target = "rolProcesoNombre", source = "rolProceso.nombre")
    LaneResponse toResponse(Lane lane);

    List<LaneResponse> toResponses(List<Lane> lanes);
}
