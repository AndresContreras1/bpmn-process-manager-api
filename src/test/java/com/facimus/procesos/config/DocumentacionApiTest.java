package com.facimus.procesos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** El contrato OpenAPI esta completo: cada operacion dice que hace, que devuelve y como puede fallar. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DocumentacionApiTest {

    private static final Set<String> METODOS = Set.of("get", "post", "put", "patch", "delete");
    private static final String RESPUESTAS = "#/components/responses/";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    private JsonNode contrato;

    @BeforeEach
    void leerContrato() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        contrato = jsonMapper.readTree(json);
    }

    @Test
    @DisplayName("Cada operacion tiene resumen, un tag con descripcion y una respuesta de exito en JSON")
    void operaciones_documentadas() {
        Set<String> tagsDescritos = contrato.path("tags").valueStream()
                .filter(tag -> !tag.path("description").asString("").isBlank())
                .map(tag -> tag.path("name").asString())
                .collect(Collectors.toSet());
        List<String> fallas = new ArrayList<>();

        operaciones().forEach((nombre, operacion) -> {
            if (operacion.path("summary").asString("").isBlank()) {
                fallas.add(nombre + ": sin summary");
            }
            List<String> tags = operacion.path("tags").valueStream().map(JsonNode::asString).toList();
            if (tags.size() != 1 || !tagsDescritos.contains(tags.getFirst())) {
                fallas.add(nombre + ": tags " + tags + " sin @Tag descrito");
            }
            List<String> exitos = operacion.path("responses").propertyNames().stream()
                    .filter(codigo -> codigo.startsWith("2"))
                    .toList();
            if (exitos.size() != 1) {
                fallas.add(nombre + ": respuestas de exito " + exitos);
            } else {
                JsonNode contenido = operacion.path("responses").path(exitos.getFirst()).path("content");
                if (!contenido.isMissingNode() && !contenido.has(MediaType.APPLICATION_JSON_VALUE)) {
                    fallas.add(nombre + ": responde " + contenido.propertyNames() + " en vez de JSON");
                }
            }
        });

        assertThat(fallas).isEmpty();
    }

    @Test
    @DisplayName("Los errores remiten a respuestas comunes con ProblemDetail, y toda operacion protegida documenta el 401")
    void errores_documentados() {
        JsonNode respuestasComunes = contrato.path("components").path("responses");
        List<String> fallas = new ArrayList<>();

        operaciones().forEach((nombre, operacion) -> {
            operacion.path("responses").properties().stream()
                    .filter(respuesta -> respuesta.getKey().charAt(0) >= '4')
                    .forEach(respuesta -> {
                        String ref = respuesta.getValue().path("$ref").asString("");
                        JsonNode comun = respuestasComunes.path(ref.replace(RESPUESTAS, ""));
                        if (!ref.startsWith(RESPUESTAS)
                                || !comun.path("content").has(MediaType.APPLICATION_PROBLEM_JSON_VALUE)) {
                            fallas.add(nombre + " " + respuesta.getKey() + ": sin respuesta ProblemDetail comun");
                        }
                    });
            boolean publica = operacion.has("security") && operacion.path("security").isEmpty();
            if (!publica && !operacion.path("responses").has("401")) {
                fallas.add(nombre + ": protegida y sin 401");
            }
        });

        assertThat(fallas).isEmpty();
        assertThat(contrato.path("components").path("schemas").path("ProblemDetail").path("properties").propertyNames())
                .containsExactlyInAnyOrder("title", "status", "detail", "instance", "errors");
    }

    /** Todas las operaciones de la API, con nombre "METODO /ruta". */
    private Map<String, JsonNode> operaciones() {
        return contrato.path("paths").propertyStream()
                .filter(ruta -> ruta.getKey().startsWith("/api/v1/"))
                .flatMap(ruta -> ruta.getValue().propertyStream()
                        .filter(metodo -> METODOS.contains(metodo.getKey()))
                        .map(metodo -> Map.entry(metodo.getKey().toUpperCase() + " " + ruta.getKey(),
                                metodo.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
