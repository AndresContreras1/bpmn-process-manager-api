package com.facimus.procesos.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The language and time zone the web app uses for the signed-in user: their own, or the "
        + "organization's when they chose none")
public record PreferenciasResponse(
        @Schema(description = "The language in effect", example = "es-CO") String idioma,
        @Schema(description = "The time zone in effect", example = "America/Bogota") String zonaHoraria,
        @Schema(description = "What the user chose; empty follows the organization", example = "en-US")
        String idiomaPropio,
        @Schema(description = "What the user chose; empty follows the organization", example = "Europe/Paris")
        String zonaHorariaPropia,
        @Schema(example = "es-CO") String idiomaDeLaOrganizacion,
        @Schema(example = "America/Bogota") String zonaHorariaDeLaOrganizacion) {
}
