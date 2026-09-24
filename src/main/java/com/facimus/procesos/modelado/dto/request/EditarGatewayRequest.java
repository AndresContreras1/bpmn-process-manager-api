package com.facimus.procesos.modelado.dto.request;

import com.facimus.procesos.modelado.model.TipoGateway;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "New name, type, lane and position of a gateway, with the version that was read")
public record EditarGatewayRequest(
        @Schema(description = "Name, unique among the flow nodes of the process", example = "Payment approved?")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres.") String nombre,
        @Schema(description = "EXCLUSIVO and INCLUSIVO need a condition on every sequence flow that leaves the gateway",
                example = "EXCLUSIVO")
        @NotNull(message = "El tipo de gateway es obligatorio.") TipoGateway tipoGateway,
        @Schema(description = "Lane the node moves to; without it the node stays where it is", example = "4")
        Long laneId,
        @Schema(description = "Horizontal position on the diagram canvas", example = "420") int posicionX,
        @Schema(description = "Vertical position on the diagram canvas", example = "80") int posicionY,
        @Schema(description = "Version read with the last GET; if someone saved a change since, the edit answers 409",
                example = "0")
        @NotNull(message = "La versión es obligatoria.") Long version) {
}
