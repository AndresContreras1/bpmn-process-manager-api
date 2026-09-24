package com.facimus.procesos.modelado.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import com.facimus.procesos.modelado.dto.response.MensajeResponse;
import com.facimus.procesos.modelado.model.Mensaje;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface MensajeMapper {

    @Mapping(target = "poolOrigenId", source = "poolOrigen.id")
    @Mapping(target = "poolDestinoId", source = "poolDestino.id")
    @Mapping(target = "nodoOrigenId", source = "nodoOrigen.id")
    @Mapping(target = "nodoDestinoId", source = "nodoDestino.id")
    @Mapping(target = "nodoManejoErrorId", source = "nodoManejoError.id")
    @Mapping(target = "respuestaEsperadaId", source = "respuestaEsperada.id")
    @Mapping(target = "procesoId", source = "proceso.id")
    MensajeResponse toResponse(Mensaje mensaje);

    List<MensajeResponse> toResponses(List<Mensaje> mensajes);
}
