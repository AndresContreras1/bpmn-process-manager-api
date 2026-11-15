package com.facimus.procesos.modelado.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PR 63: una respuesta en un hilo de comentarios. */
@Schema(description = "A reply in a comment thread")
public record RespuestaRequest(
        @Schema(description = "The text in Markdown", example = "The warehouse lead, every morning.")
        @NotBlank(message = "La respuesta no puede ir vacía.")
        @Size(max = 10_000, message = "Un comentario no puede superar 10 000 caracteres.") String markdown,
        @Schema(description = "Ids of the people it mentions, who must be able to view the process", example = "[3]")
        @Size(max = 20, message = "Un comentario menciona a 20 personas como mucho.")
        List<@NotNull(message = "Una mención no puede ir vacía.") Long> menciones) {
}
