package com.facimus.procesos.modelado.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** PR 62: el HTML saneado de un Markdown, el mismo que se guardaria. */
@Schema(description = "The cleaned HTML of some Markdown, exactly what saving it would keep")
public record VistaPreviaResponse(@Schema(example = "<h2>Packing</h2><ul><li>Wrap the order</li></ul>") String html) {
}
