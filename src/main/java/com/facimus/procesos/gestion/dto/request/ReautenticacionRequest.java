package com.facimus.procesos.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Confirming who you are again: the password without a second factor, the code of the "
        + "authenticator app or a recovery code with one")
public record ReautenticacionRequest(
        @Schema(example = "every-order-on-time", format = "password")
        @Size(max = 72, message = "La contraseña no puede superar 72 caracteres.") String password,
        @Schema(description = "With a second factor: the 6 digits of the app or a recovery code", example = "492039")
        @Size(max = 20, message = "El código no puede superar 20 caracteres.") String codigo) {

    /** Sin segundo factor: solo la clave. */
    public ReautenticacionRequest(String password) {
        this(password, null);
    }
}
