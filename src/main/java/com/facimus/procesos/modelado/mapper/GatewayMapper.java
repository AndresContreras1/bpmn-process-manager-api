package com.facimus.procesos.modelado.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.facimus.procesos.modelado.dto.response.GatewayResponse;
import com.facimus.procesos.modelado.model.Gateway;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface GatewayMapper {

    @Mapping(target = "laneId", source = "lane.id")
    GatewayResponse toResponse(Gateway gateway);

    List<GatewayResponse> toResponses(List<Gateway> gateways);
}
