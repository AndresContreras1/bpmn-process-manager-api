package com.facimus.procesos.modelado.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** PR 64: pedir la revision de un proceso, con lo que cambio y por que. */
@Schema(description = "A review request for the draft of a process")
public record SolicitudRequest(
        @Schema(description = "What changed and why, in Markdown; optional",
                example = "Adds the **fraud check** before packing.")
        @Size(max = 10_000, message = "El texto no puede superar 10 000 caracteres.") String markdown) {
}
