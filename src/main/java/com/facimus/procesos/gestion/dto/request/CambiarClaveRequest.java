package com.facimus.procesos.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "A user changing their own password")
public record CambiarClaveRequest(
        @Schema(description = "The password in use, temporary or not", example = "Xk7pQm2r")
        @NotBlank(message = "La contraseña actual es obligatoria.")
        @Size(max = 72, message = "La contraseña no puede superar 72 caracteres.") String actual,
        @Schema(description = "The new one", example = "mi-clave-nueva")
        @NotBlank(message = "La contraseña nueva es obligatoria.")
        @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres.")
        @Size(max = 72, message = "La contraseña no puede superar 72 caracteres.") String nueva) {
}
