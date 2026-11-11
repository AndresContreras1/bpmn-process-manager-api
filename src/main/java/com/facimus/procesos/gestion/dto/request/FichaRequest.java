package com.facimus.procesos.gestion.dto.request;

import java.time.LocalDate;
import java.util.List;

import com.facimus.procesos.gestion.model.Clasificacion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Where a process is filed and who answers for it")
public record FichaRequest(
        @Schema(description = "Its folder; empty for none", example = "1") Long carpetaId,
        @Schema(description = "The user who owns it; empty for nobody", example = "2") Long duenoId,
        @Schema(description = "When it has to be reviewed next", example = "2027-03-01") LocalDate proximaRevision,
        @Schema(example = "CONFIDENCIAL")
        @NotNull(message = "La clasificación es obligatoria.") Clasificacion clasificacion,
        @Schema(description = "Up to 10 tags, kept in lowercase", example = "[\"gdpr\", \"logistics\"]")
        @NotNull(message = "Las etiquetas son obligatorias.")
        @Size(max = 10, message = "Un proceso no puede tener más de 10 etiquetas.")
        List<@NotBlank(message = "Una etiqueta no puede estar vacía.")
                @Size(max = 40, message = "Una etiqueta no puede superar 40 caracteres.") String> etiquetas,
        @Schema(description = "Version read with the last GET", example = "0")
        @NotNull(message = "La versión es obligatoria.") Long version) {
}
