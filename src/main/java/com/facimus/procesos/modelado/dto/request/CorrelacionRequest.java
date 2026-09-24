package com.facimus.procesos.modelado.dto.request;

import com.facimus.procesos.modelado.model.PoliticaSinCaso;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Correlation key of a message")
public record CorrelacionRequest(
        @Schema(description = "Data that links the messages of one conversation", example = "orderId")
        @NotBlank(message = "El criterio es obligatorio.")
        @Size(max = 120, message = "El criterio no puede superar 120 caracteres.") String criterio,
        @Schema(description = "Field of the message body that carries the key; without it a message cannot "
                + "be matched to a case", example = "orderId")
        @Size(max = 80, message = "El campo no puede superar 80 caracteres.") String campo,
        @Schema(description = "What to do with a message that matches no open case: DESCARTAR or "
                + "INICIAR_CASO. DESCARTAR when it is missing", example = "INICIAR_CASO")
        PoliticaSinCaso sinCaso,
        @Schema(description = "Version of the current key, read with the last GET. Leave it out to create the first "
                + "one; if it does not match the saved key, the change answers 409", example = "0") Long version) {
}
