package com.facimus.procesos.gestion.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.model.Proceso;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProcesoMapper {

    ProcesoResponse toResponse(Proceso proceso);
}
