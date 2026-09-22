package com.facimus.procesos.gestion.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.facimus.procesos.gestion.dto.response.EmpresaInvitadaResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoRecibidoResponse;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.ProcesoCompartido;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = ProcesoMapper.class)
public interface ProcesoCompartidoMapper {

    /** Lo que ve la duena: con que empresa comparte el proceso y desde cuando. */
    @Mapping(target = "empresaId", source = "empresaInvitada.id")
    @Mapping(target = "nombre", source = "empresaInvitada.nombre")
    @Mapping(target = "nit", source = "empresaInvitada.nit")
    EmpresaInvitadaResponse toInvitada(ProcesoCompartido comparticion);

    /** Lo que ve la invitada: el proceso y de que empresa es. */
    @Mapping(target = "proceso", source = "proceso")
    @Mapping(target = "empresaPropietariaId", source = "empresa.id")
    @Mapping(target = "empresaPropietariaNombre", source = "empresa.nombre")
    ProcesoRecibidoResponse toRecibido(Proceso proceso);
}
