package com.facimus.procesos.gestion.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.dto.response.EmpresaResponse;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface EmpresaMapper {

    EmpresaResponse toResponse(Empresa empresa);
}
