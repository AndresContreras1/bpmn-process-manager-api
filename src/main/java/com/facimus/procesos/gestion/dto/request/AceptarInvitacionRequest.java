package com.facimus.procesos.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Who accepts an invitation: the token of its link, their name and their password")
public record AceptarInvitacionRequest(
        @Schema(example = "gJ0o0v5pX2m1Sx0c3yq5k8m7Tn4b6Wd9Qe1Rf2Uh3Zs")
        @NotBlank(message = "El token del enlace es obligatorio.")
        @Size(max = 100, message = "El token del enlace no puede superar 100 caracteres.") String token,
        @Schema(description = "Full name", example = "Luis Gomez")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres.") String nombre,
        @Schema(example = "harbor lights at dusk", format = "password")
        @NotBlank(message = "La contrasena es obligatoria.")
        @Size(min = 15, message = "La contraseña debe tener al menos 15 caracteres.")
        @Size(max = 72, message = "La contrasena no puede superar 72 caracteres.") String password) {
}
