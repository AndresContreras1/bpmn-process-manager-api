package com.facimus.procesos.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "The token of a link the API sent by e-mail: what follows #token= in its address")
public record EnlaceRequest(
        @Schema(example = "gJ0o0v5pX2m1Sx0c3yq5k8m7Tn4b6Wd9Qe1Rf2Uh3Zs")
        @NotBlank(message = "El token del enlace es obligatorio.")
        @Size(max = 100, message = "El token del enlace no puede superar 100 caracteres.") String token) {
}
