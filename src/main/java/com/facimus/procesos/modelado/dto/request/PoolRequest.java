package com.facimus.procesos.modelado.dto.request;

import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.TipoParticipante;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "A participant of the process")
public record PoolRequest(
        @Schema(example = "Payment gateway")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres.") String nombre,
        @Schema(example = "SISTEMA_EXTERNO")
        @NotNull(message = "El tipo de participante es obligatorio.") TipoParticipante tipoParticipante,
        @Schema(description = "true when the store does not model the participant's internal flow", example = "true")
        boolean cajaNegra,
        @Schema(description = "Kind of partner behind the participant: NINGUNA, CLIENTE, PAGOS, "
                + "TRANSPORTE or NOTIFICACIONES. NINGUNA when it is missing", example = "PAGOS")
        Integracion integracion) {
}
