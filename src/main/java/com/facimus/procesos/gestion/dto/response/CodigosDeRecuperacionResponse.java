package com.facimus.procesos.gestion.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ten recovery codes, each good for one sign-in without the app. They are shown once: the "
        + "server keeps only a keyed hash of each")
public record CodigosDeRecuperacionResponse(
        @Schema(example = "[\"k7p2m-q9rt4\", \"b4nlw-x3c8d\"]") List<String> codigos) {
}
