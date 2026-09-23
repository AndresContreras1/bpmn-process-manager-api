package com.facimus.procesos.modelado.dto.request;

import com.facimus.procesos.modelado.model.TipoActividad;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "New name, description and position of an activity, with the version that was read")
public record EditarActividadRequest(
        @Schema(description = "Name, unique among the flow nodes of the process", example = "Pick and pack items")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres.") String nombre,
        @Schema(example = "Collect the items and prepare the package.")
        @Size(max = 1000, message = "La descripcion no puede superar 1000 caracteres.") String descripcion,
        @Schema(description = "Who does the work: USUARIO a person of the lane's role, SERVICIO the store itself, "
                + "ENVIO sends a message to another participant and RECEPCION waits for one. Optional for now: "
                + "when it is missing the activity is USUARIO.", example = "USUARIO") TipoActividad tipoActividad,
        @Schema(description = "Horizontal position on the diagram canvas", example = "580") int posicionX,
        @Schema(description = "Vertical position on the diagram canvas", example = "200") int posicionY,
        @Schema(description = "Version read with the last GET; if someone saved a change since, the edit answers 409",
                example = "0")
        @NotNull(message = "La versión es obligatoria.") Long version) {
}
