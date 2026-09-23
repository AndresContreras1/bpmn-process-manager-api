package com.facimus.procesos.modelado.service.impl;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.facimus.procesos.common.IntegracionFallidaException;
import com.facimus.procesos.modelado.service.Dictamen;
import com.facimus.procesos.modelado.service.RevisorDeDiagramas;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Le pide a Gemini que revise un diagrama BPMN. El modelo contesta en el formato que se le declara, asi que lo que
 * llega se lee como datos y no como texto libre; si aun asi no encaja, la peticion falla en vez de inventar una
 * respuesta. Sin GEMINI_API_KEY la funcion queda apagada y nadie gasta una llamada.
 */
@Component
public class GeminiRevisor implements RevisorDeDiagramas {

    /** Lo que se le pide al modelo. El diagrama va despues, en el mismo mensaje. */
    private static final String INSTRUCCION = """
            Eres un analista de procesos de negocio. Revisa este diagrama BPMN de una tienda en linea y responde \
            solo con los hallazgos que un modelador deberia corregir: caminos que no terminan, decisiones sin \
            alternativa, actividades sin responsable, mensajes sin respuesta, pasos que faltan para el caso de \
            error. No inventes elementos que no estan en el diagrama y no repitas lo que ya esta bien. Si el \
            diagrama esta correcto, devuelve la lista de hallazgos vacia. Escribe en el idioma del diagrama.

            Diagrama:
            """;

    /** El formato del que el modelo no se puede salir. Es el mismo que leen Dictamen y HallazgoResponse. */
    private static final Map<String, Object> ESQUEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "resumen", Map.of("type", "string",
                            "description", "Una frase sobre lo que hace el proceso y su punto mas debil"),
                    "hallazgos", Map.of("type", "array", "items", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "severidad", Map.of("type", "string", "enum", List.of("ALTA", "MEDIA", "BAJA")),
                                    "elemento", Map.of("type", "string"),
                                    "problema", Map.of("type", "string"),
                                    "sugerencia", Map.of("type", "string")),
                            "required", List.of("severidad", "elemento", "problema", "sugerencia")))),
            "required", List.of("resumen", "hallazgos"));

    private final RestClient cliente;
    private final JsonMapper jsonMapper;
    private final String clave;
    private final String modelo;

    public GeminiRevisor(RestClient clienteDelRevisor, JsonMapper jsonMapper,
            @Value("${gemini.api-key}") String clave,
            @Value("${gemini.model}") String modelo) {
        this.cliente = clienteDelRevisor;
        this.jsonMapper = jsonMapper;
        this.clave = clave;
        this.modelo = modelo;
    }

    @Override
    public boolean estaConfigurado() {
        return StringUtils.hasText(clave);
    }

    @Override
    public Dictamen revisar(String diagrama) {
        String respuesta = llamar(diagrama);
        return leer(texto(respuesta));
    }

    private String llamar(String diagrama) {
        try {
            return cliente.post()
                    .uri("/interactions")
                    .header("x-goog-api-key", clave)
                    .body(Map.of(
                            "model", modelo,
                            "input", INSTRUCCION + diagrama,
                            "response_format", Map.of(
                                    "type", "text",
                                    "mime_type", "application/json",
                                    "schema", ESQUEMA)))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new IntegracionFallidaException("El revisor de IA no respondió. Intenta de nuevo más tarde.", e);
        }
    }

    /** La respuesta trae los pasos del modelo; el que interesa es su salida de texto, que lleva el JSON pedido. */
    private String texto(String respuesta) {
        JsonNode raiz = parsear(respuesta, "El revisor de IA respondió algo que no es JSON.");
        for (JsonNode paso : raiz.path("steps")) {
            if ("model_output".equals(paso.path("type").asString(""))) {
                for (JsonNode parte : paso.path("content")) {
                    if ("text".equals(parte.path("type").asString(""))) {
                        return parte.path("text").asString("");
                    }
                }
            }
        }
        throw new IntegracionFallidaException("El revisor de IA no devolvió ninguna revisión.");
    }

    private Dictamen leer(String texto) {
        Dictamen dictamen;
        try {
            dictamen = jsonMapper.readValue(texto, Dictamen.class);
        } catch (JacksonException e) {
            throw new IntegracionFallidaException("El revisor de IA respondió fuera del formato acordado.", e);
        }
        if (dictamen == null || dictamen.resumen() == null || dictamen.hallazgos() == null) {
            throw new IntegracionFallidaException("El revisor de IA respondió fuera del formato acordado.");
        }
        return dictamen;
    }

    private JsonNode parsear(String texto, String mensaje) {
        try {
            return jsonMapper.readTree(texto == null ? "" : texto);
        } catch (JacksonException e) {
            throw new IntegracionFallidaException(mensaje, e);
        }
    }
}
