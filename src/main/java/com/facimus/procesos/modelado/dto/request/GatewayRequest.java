package com.facimus.procesos.modelado.dto.request;

import com.facimus.procesos.modelado.model.TipoGateway;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "A gateway (decision or split point) inside a lane")
public record GatewayRequest(
        @Schema(description = "Name, unique among the flow nodes of the process", example = "Payment approved?")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres.") String nombre,
        @Schema(example = "EXCLUSIVO")
        @NotNull(message = "El tipo de gateway es obligatorio.") TipoGateway tipoGateway,
        @Schema(description = "Horizontal position on the diagram canvas", example = "420") int posicionX,
        @Schema(description = "Vertical position on the diagram canvas", example = "80") int posicionY) {
}
