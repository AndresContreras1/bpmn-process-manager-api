package com.facimus.procesos.config;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Contrato OpenAPI: datos de la API, esquema de seguridad JWT y las respuestas de error que los controllers
 * citan por nombre con @ApiResponse(ref = "...").
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_SEGURIDAD = "bearerAuth";
    private static final String PROBLEMA = "ProblemDetail";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BPMN Process Manager API")
                        .description("Multi-tenant REST API for modeling e-commerce operations (order fulfillment, "
                                + "payments and returns) as BPMN processes.")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_SEGURIDAD))
                .components(new Components()
                        .addSecuritySchemes(ESQUEMA_SEGURIDAD, new SecurityScheme()
                                .name(ESQUEMA_SEGURIDAD)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSchemas(PROBLEMA, esquemaProblema())
                        .addResponses("BadRequest", respuestaDeError("Invalid request: a field failed validation, "
                                + "the JSON is malformed or has a field the operation does not accept. `errors` holds "
                                + "the message for each field."))
                        .addResponses("Unauthorized", respuestaDeError("Missing, invalid or expired token, or wrong "
                                + "login credentials.")
                                .addHeaderObject(HttpHeaders.WWW_AUTHENTICATE, new Header()
                                        .description("Authentication scheme the API expects")
                                        .schema(new StringSchema().example("Bearer"))))
                        .addResponses("Forbidden", respuestaDeError("The user's access role does not allow this "
                                + "operation."))
                        .addResponses("NotFound", respuestaDeError("The resource does not exist, or it belongs to "
                                + "another store."))
                        .addResponses("Conflict", respuestaDeError("A business rule rejects the operation, for "
                                + "example a duplicated name."))
                        .addResponses("TooManyRequests", respuestaDeError("Too many failed logins for this email from "
                                + "this address. Retry-After says how many seconds to wait.")
                                .addHeaderObject(HttpHeaders.RETRY_AFTER, new Header()
                                        .description("Seconds until the login can be tried again")
                                        .schema(new IntegerSchema().example(900)))));
    }

    /** Forma de todos los errores (RFC 9457); errors solo viaja en los 400 que senalan campos concretos. */
    private static Schema<?> esquemaProblema() {
        return new ObjectSchema()
                .description("Error in Problem Details format (RFC 9457)")
                .addProperty("title", new StringSchema().example("Recurso no encontrado"))
                .addProperty("status", new IntegerSchema().example(404))
                .addProperty("detail", new StringSchema().example("Proceso no encontrado."))
                .addProperty("instance", new StringSchema().example("/api/v1/procesos/99"))
                .addProperty("errors", new MapSchema()
                        .additionalProperties(new StringSchema())
                        .description("Message for each invalid field; only in 400 responses")
                        .example(Map.of("nombre", "El nombre es obligatorio.")));
    }

    private static ApiResponse respuestaDeError(String descripcion) {
        return new ApiResponse()
                .description(descripcion)
                .content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEMA))));
    }
}
