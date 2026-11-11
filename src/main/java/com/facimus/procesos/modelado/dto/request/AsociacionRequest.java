package com.facimus.procesos.modelado.dto.request;

import com.facimus.procesos.modelado.model.DireccionAsociacion;
import com.facimus.procesos.modelado.model.TipoAsociacion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PR 55: una asociacion nueva; los extremos se nombran por su bpmnId. */
public record AsociacionRequest(
        @Schema(description = "ASOCIACION joins an artifact to anything; ENTRADA_DATOS goes from a data object or "
                + "store to an activity, and SALIDA_DATOS the other way", example = "ASOCIACION")
        @NotNull(message = "El tipo de asociación es obligatorio.") TipoAsociacion tipo,
        @Schema(description = "bpmnId of the source element", example = "TextAnnotation_0a1b2c3")
        @NotBlank(message = "El origen es obligatorio.")
        @Size(max = 64, message = "El origen no puede superar 64 caracteres.") String origen,
        @Schema(description = "bpmnId of the target element", example = "Activity_0d4e5f6")
        @NotBlank(message = "El destino es obligatorio.")
        @Size(max = 64, message = "El destino no puede superar 64 caracteres.") String destino,
        @Schema(description = "Arrows of an ASOCIACION: NINGUNA (default), UNA or AMBAS", example = "NINGUNA")
        DireccionAsociacion direccion) {
}
