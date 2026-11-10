package com.facimus.procesos.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "A language and a time zone. For a person, leaving one out follows the organization")
public record PreferenciasRequest(
        @Schema(description = "One of es-CO, en-US, en-AU or fr-FR: the language of the web app and how it writes "
                + "dates, numbers and money", example = "es-CO")
        @Pattern(regexp = "(es-CO|en-US|en-AU|fr-FR)?", message = "El idioma es es-CO, en-US, en-AU o fr-FR.")
        String idioma,
        @Schema(description = "A time zone of the IANA database", example = "America/Bogota")
        @Size(max = 40, message = "La zona horaria no puede superar 40 caracteres.") String zonaHoraria) {
}
