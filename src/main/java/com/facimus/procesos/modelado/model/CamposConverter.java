package com.facimus.procesos.modelado.model;

import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guarda los campos de un mensaje como JSON en una sola columna. Su propio JsonMapper, sin inyectar: un converter
 * de JPA lo construye Hibernate, no Spring, y la forma que se guarda no debe cambiar si cambia la del API.
 */
@Converter
public class CamposConverter implements AttributeConverter<List<CampoDeMensaje>, String> {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<CampoDeMensaje>> LISTA = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<CampoDeMensaje> campos) {
        return campos == null || campos.isEmpty() ? null : JSON.writeValueAsString(campos);
    }

    @Override
    public List<CampoDeMensaje> convertToEntityAttribute(String json) {
        return json == null || json.isBlank() ? List.of() : JSON.readValue(json, LISTA);
    }
}
