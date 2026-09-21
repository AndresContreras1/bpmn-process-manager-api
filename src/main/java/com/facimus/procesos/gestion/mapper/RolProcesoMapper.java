package com.facimus.procesos.gestion.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.facimus.procesos.gestion.dto.response.RolProcesoVistaResponse;
import com.facimus.procesos.gestion.model.RolProceso;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RolProcesoMapper {

    /** El rol con su uso: cuantos procesos lo usan, y si por eso no se puede eliminar. */
    @Mapping(target = "procesosQueLoUsan", source = "usos")
    @Mapping(target = "enUso", expression = "java(usos > 0)")
    RolProcesoVistaResponse toResponse(RolProceso rol, long usos);
}
