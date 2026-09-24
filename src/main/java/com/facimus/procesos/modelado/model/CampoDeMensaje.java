package com.facimus.procesos.modelado.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Un dato que viaja dentro de un mensaje. Se guarda como JSON en la fila del mensaje, porque la lista completa
 * solo tiene sentido junto al mensaje y nadie consulta un campo por su cuenta.
 */
@Schema(description = "A field that travels inside the message")
public record CampoDeMensaje(
        @Schema(example = "orderId")
        @NotBlank(message = "El nombre del campo es obligatorio.")
        @Size(max = 60, message = "El nombre del campo no puede superar 60 caracteres.") String nombre,
        @Schema(example = "TEXTO")
        @NotNull(message = "El tipo del campo es obligatorio.") TipoDeDato tipo) {
}
