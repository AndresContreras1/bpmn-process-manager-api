package com.facimus.procesos.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

@Schema(description = "The answer of the device to the registration options: the challenge id they came with, a name "
        + "for the passkey and the credential, as PublicKeyCredential.toJSON() writes it")
public record PasskeyRegistroRequest(
        @Schema(description = "The desafio of the options", example = "pZ8w2kQe0rS4tV6xY8zA1bC3dE5fG7hJ9kL1mN3pQ5r")
        @NotBlank(message = "El desafío es obligatorio.")
        @Size(max = 64, message = "El desafío no puede superar 64 caracteres.") String desafio,
        @Schema(description = "Optional; \"Passkey\" when empty", example = "Work laptop")
        @Size(max = 64, message = "El nombre de la passkey no puede superar 64 caracteres.") String etiqueta,
        @Schema(description = "The registration credential: id, rawId, type and response with clientDataJSON and "
                + "attestationObject, in base64url")
        @NotNull(message = "La credencial es obligatoria.") JsonNode credencial) {
}
