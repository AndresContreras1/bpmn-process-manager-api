package com.facimus.procesos.gestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

@Schema(description = "The options of a WebAuthn ceremony. The web app gives opciones to "
        + "PublicKeyCredential.parseCreationOptionsFromJSON or parseRequestOptionsFromJSON and sends desafio back with "
        + "the answer of the device. They work once, within 5 minutes")
public record OpcionesWebAuthnResponse(
        @Schema(example = "pZ8w2kQe0rS4tV6xY8zA1bC3dE5fG7hJ9kL1mN3pQ5r") String desafio,
        @Schema(description = "The options in the JSON form of WebAuthn Level 3, binary fields in base64url")
        JsonNode opciones) {
}
