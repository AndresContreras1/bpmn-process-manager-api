package com.facimus.procesos.gestion.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A passkey just registered. When it is the first second factor of the user, the ten recovery "
        + "codes come with it, shown once")
public record PasskeyRegistradaResponse(
        PasskeyResponse passkey,
        @Schema(description = "Empty when the user already had recovery codes",
                example = "[\"k7p2m-q9rt4\", \"b4nlw-x3c8d\"]") List<String> codigosDeRecuperacion) {
}
