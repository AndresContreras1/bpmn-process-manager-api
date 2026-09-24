package com.facimus.procesos.gestion.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import com.facimus.procesos.gestion.dto.response.ConfiguracionTiendaResponse;
import com.facimus.procesos.gestion.model.ConfiguracionTienda;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ConfiguracionTiendaMapper {

    ConfiguracionTiendaResponse toResponse(ConfiguracionTienda configuracion);
}
