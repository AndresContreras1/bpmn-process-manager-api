package com.facimus.procesos.gestion.mapper;

import java.time.LocalDate;

import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.model.Proceso;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProcesoMapper {

    /**
     * Si el borrador tiene cambios sin publicar no sale de la entidad: hay que armar el diagrama de hoy y comparar
     * su huella. Lo hace quien lee un proceso suyo, con conBorradorPendiente. Sin dia, no dice si su revision vencio:
     * es el proceso que va dentro de una instantanea, que no cambia con el calendario.
     */
    @Mapping(target = "borradorPendiente", ignore = true)
    @Mapping(target = "revisionVencida", ignore = true)
    ProcesoResponse toResponse(Proceso proceso);

    /** PR 64: el mismo, diciendo si su revision vencio contra el dia de hoy del reloj de la API. */
    @Mapping(target = "borradorPendiente", ignore = true)
    @Mapping(target = "revisionVencida", expression = "java(proceso.revisionVencida(hoy))")
    ProcesoResponse toResponse(Proceso proceso, @Context LocalDate hoy);
}
