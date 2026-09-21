package com.facimus.procesos.gestion.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.model.Usuario;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UsuarioMapper {

    @Mapping(target = "empresaId", source = "empresa.id")
    UsuarioResponse toResponse(Usuario usuario);

    List<UsuarioResponse> toResponses(List<Usuario> usuarios);
}
