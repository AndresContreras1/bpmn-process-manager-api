package com.facimus.procesos.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

@Schema(description = "What the device signed for the request options: the challenge id they came with and the "
        + "credential, as PublicKeyCredential.toJSON() writes it")
public record PasskeyFirmaRequest(
        @Schema(description = "The desafio of the options", example = "pZ8w2kQe0rS4tV6xY8zA1bC3dE5fG7hJ9kL1mN3pQ5r")
        @NotBlank(message = "El desafío es obligatorio.")
        @Size(max = 64, message = "El desafío no puede superar 64 caracteres.") String desafio,
        @Schema(description = "The assertion credential: id, rawId, type and response with clientDataJSON, "
                + "authenticatorData, signature and userHandle, in base64url")
        @NotNull(message = "La credencial es obligatoria.") JsonNode credencial) {
}
