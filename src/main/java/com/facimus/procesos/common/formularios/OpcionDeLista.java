package com.facimus.procesos.common.formularios;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PR 69: una opcion de una lista: lo que se guarda y lo que se le muestra a quien elige. */
@Schema(description = "An option of a list field")
public record OpcionDeLista(
        @Schema(description = "What is stored when it is chosen", example = "TARJETA")
        @NotBlank(message = "El valor de la opcion es obligatorio.")
        @Size(max = 100, message = "El valor de la opcion no puede superar 100 caracteres.") String valor,
        @Schema(description = "What the person sees", example = "Credit card")
        @Size(max = 200, message = "La etiqueta de la opcion no puede superar 200 caracteres.") String etiqueta) {

    /** Una etiqueta en blanco es ninguna, como vuelve del BPMN: se muestra el valor. */
    public OpcionDeLista {
        etiqueta = etiqueta == null || etiqueta.isBlank() ? null : etiqueta;
    }
}
