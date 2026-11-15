package com.facimus.procesos.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "A review that confirms a process is still valid")
public record RecertificacionRequest(
        @Schema(description = "What was checked, up to 2000 characters; it goes to the audit trail",
                example = "Checked against the new carrier contract: nothing changes.")
        @Size(max = 2000, message = "La nota no puede superar 2000 caracteres.") String nota) {
}
