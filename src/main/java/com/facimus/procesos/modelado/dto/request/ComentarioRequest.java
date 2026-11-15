package com.facimus.procesos.modelado.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** PR 63: el primer comentario de un hilo, sobre el proceso, uno de sus elementos o una version publicada. */
@Schema(description = "A new comment thread on a process, optionally on one element and one published version")
public record ComentarioRequest(
        @Schema(description = "The text in Markdown", example = "Who checks the **address** before packing?")
        @NotBlank(message = "El comentario no puede ir vacío.")
        @Size(max = 10_000, message = "Un comentario no puede superar 10 000 caracteres.") String markdown,
        @Schema(description = "bpmnId of the element it is about; empty for the whole process",
                example = "Activity_0b3k9xq")
        @Size(max = 64, message = "El bpmnId no puede superar 64 caracteres.")
        @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_.-]*", message = "El bpmnId es un id de BPMN: letras, números, _, - y .")
        String bpmnId,
        @Schema(description = "Number of the published version it is about; empty for the draft", example = "2")
        @Positive(message = "La versión es un número desde 1.") Integer versionDelProceso,
        @Schema(description = "Ids of the people it mentions, who must be able to view the process",
                example = "[2, 5]")
        @Size(max = 20, message = "Un comentario menciona a 20 personas como mucho.")
        List<@NotNull(message = "Una mención no puede ir vacía.") Long> menciones) {
}
