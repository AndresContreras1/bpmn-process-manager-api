package com.facimus.procesos.gestion.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.facimus.procesos.gestion.dto.response.VersionResponse;
import com.facimus.procesos.gestion.model.VersionProceso;

/** La definicion no viaja en el DTO: es un documento entero y tiene su propio endpoint. */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface VersionProcesoMapper {

    @Mapping(target = "procesoId", source = "proceso.id")
    VersionResponse toResponse(VersionProceso version);

    List<VersionResponse> toResponses(List<VersionProceso> versiones);
}
