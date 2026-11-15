package com.facimus.procesos.modelado.model;

import com.facimus.procesos.common.formularios.Formulario;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 69: el formulario de un nodo, como JSON en una sola columna. Como {@link CamposConverter}, con su propio
 * JsonMapper: lo construye Hibernate, no Spring, y lo guardado no cambia si cambia la forma del API. Lo que va vacio
 * no se escribe, y un formulario sin campos se guarda como ninguno.
 */
@Converter
public class FormularioConverter implements AttributeConverter<Formulario, String> {

    private static final JsonMapper JSON = JsonMapper.builder()
            .changeDefaultPropertyInclusion(incluir -> incluir.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    @Override
    public String convertToDatabaseColumn(Formulario formulario) {
        return Formulario.siTieneCampos(formulario).map(JSON::writeValueAsString).orElse(null);
    }

    @Override
    public Formulario convertToEntityAttribute(String json) {
        return json == null || json.isBlank() ? null : JSON.readValue(json, Formulario.class);
    }
}
