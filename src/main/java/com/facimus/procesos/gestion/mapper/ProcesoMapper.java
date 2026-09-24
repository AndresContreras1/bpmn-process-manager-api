package com.facimus.procesos.gestion.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.model.Proceso;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProcesoMapper {

    /**
     * Si el borrador tiene cambios sin publicar no sale de la entidad: hay que armar el diagrama de hoy y comparar
     * su huella. Lo hace quien lee un proceso suyo, con conBorradorPendiente.
     */
    @Mapping(target = "borradorPendiente", ignore = true)
    ProcesoResponse toResponse(Proceso proceso);
}
