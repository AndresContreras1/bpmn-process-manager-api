package com.facimus.procesos.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Confirming that the account of a provider belongs to the user of that e-mail: the password "
        + "without a second factor, the code of the app or a recovery code with one")
public record VinculoRequest(
        @Schema(description = "The token after #token= in the address the web app was sent to")
        @NotBlank(message = "El token es obligatorio.")
        @Size(max = 100, message = "El token no puede superar 100 caracteres.") String token,
        @Schema(example = "every-order-on-time", format = "password")
        @Size(max = 72, message = "La contraseña no puede superar 72 caracteres.") String password,
        @Schema(example = "492039")
        @Size(max = 20, message = "El código no puede superar 20 caracteres.") String codigo) {
}
