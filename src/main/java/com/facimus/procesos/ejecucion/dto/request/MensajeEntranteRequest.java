package com.facimus.procesos.ejecucion.dto.request;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "A message arriving at the process from another participant")
public record MensajeEntranteRequest(
        @Schema(description = "Name of the message as the published version calls it",
                example = "Payment authorization result")
        @NotBlank(message = "El nombre del mensaje es obligatorio.")
        @Size(max = 120, message = "El nombre del mensaje no puede superar 120 caracteres.") String nombre,
        @Schema(description = "Value it looks for its case by; leave it out to take it from the body by the "
                + "correlation field", example = "ORD-1001")
        @Size(max = 120, message = "La clave no puede superar 120 caracteres.") String clave,
        @Schema(description = "What travels inside; it lands in the case variables under the message's variable",
                example = "{\"status\": \"APPROVED\"}") Map<String, Object> cuerpo,
        @Schema(description = "Your own id for this message: sending it twice with the same one does not process "
                + "it twice", example = "webhook-7")
        @Size(max = 120, message = "La clave externa no puede superar 120 caracteres.") String claveExterna) {
}
