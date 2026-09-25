package com.facimus.procesos.ejecucion.mapper;

import java.util.List;
import java.util.Map;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.springframework.beans.factory.annotation.Autowired;

import com.facimus.procesos.ejecucion.dto.response.MensajeEntranteResponse;
import com.facimus.procesos.ejecucion.dto.response.MensajeSalienteResponse;
import com.facimus.procesos.ejecucion.model.MensajeEntrante;
import com.facimus.procesos.ejecucion.model.MensajeSaliente;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Traduce lo que las bandejas guardan a lo que la API responde. Es una clase y no una interfaz por lo mismo que
 * {@link CasoMapper}: el cuerpo de un mensaje se guarda como JSON en una columna y sale como objeto, y esa
 * traduccion necesita el mismo JsonMapper que usa el resto de la aplicacion.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class MensajeriaMapper {

    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {
    };

    @Autowired
    protected JsonMapper json;

    @Mapping(target = "casoId", source = "caso.id")
    @Mapping(target = "casoReferencia", source = "caso.referencia")
    public abstract MensajeSalienteResponse toSaliente(MensajeSaliente saliente);

    public abstract List<MensajeSalienteResponse> toSalientes(List<MensajeSaliente> salientes);

    @Mapping(target = "procesoId", source = "proceso.id")
    @Mapping(target = "casoId", source = "caso.id")
    @Mapping(target = "casoReferencia", source = "caso.referencia")
    @Mapping(target = "repetido", constant = "false")
    public abstract MensajeEntranteResponse toEntrante(MensajeEntrante entrante);

    public abstract List<MensajeEntranteResponse> toEntrantes(List<MensajeEntrante> entrantes);

    /**
     * El mismo mensaje, marcado como lo que es cuando alguien lo manda dos veces con la misma clave externa.
     * Recibe la respuesta ya traducida y no la entidad, para que MapStruct no dude entre las dos al traducir una
     * lista: para el son dos formas de convertir lo mismo.
     */
    public MensajeEntranteResponse comoRepetido(MensajeEntranteResponse respuesta) {
        return new MensajeEntranteResponse(respuesta.id(), respuesta.procesoId(), respuesta.casoId(),
                respuesta.casoReferencia(), respuesta.nombre(), respuesta.clave(), respuesta.cuerpo(),
                respuesta.origen(), respuesta.claveExterna(), respuesta.resultado(), respuesta.tick(),
                respuesta.fecha(), true);
    }

    /** El JSON guardado, como objeto; una columna vacia no es un objeto vacio, es que no hay cuerpo. */
    public Map<String, Object> aMapa(String guardado) {
        return guardado == null || guardado.isBlank() ? null : json.readValue(guardado, MAPA);
    }

    /** Lo que llega del cliente, como se guarda; sin cuerpo se guarda un objeto vacio, nunca nulo. */
    public String aJson(Map<String, Object> cuerpo) {
        return json.writeValueAsString(cuerpo == null ? Map.of() : cuerpo);
    }
}
