package com.facimus.procesos.modelado.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PR 63: el texto nuevo de un comentario propio, con la version que se leyo. */
@Schema(description = "The new text of one's own comment, with the version read")
public record EdicionDeComentarioRequest(
        @Schema(description = "The text in Markdown", example = "Who checks the **address and phone** before packing?")
        @NotBlank(message = "El comentario no puede ir vacío.")
        @Size(max = 10_000, message = "Un comentario no puede superar 10 000 caracteres.") String markdown,
        @Schema(description = "Ids of the people it mentions; only the new ones are notified", example = "[2, 5]")
        @Size(max = 20, message = "Un comentario menciona a 20 personas como mucho.")
        List<@NotNull(message = "Una mención no puede ir vacía.") Long> menciones,
        @Schema(description = "Version read with the last GET", example = "0")
        @NotNull(message = "La versión es obligatoria.") Long version) {
}
