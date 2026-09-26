package com.facimus.procesos.gestion.dto.request;

import com.facimus.procesos.gestion.model.ParametrosSimulacion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Como se portan los socios simulados de la tienda. Se manda entero o no se manda: cambiar un numero suelto
 * dejaria una simulacion a medio camino entre la de antes y la de ahora, y con dos personas tocandola, ninguna de
 * las dos sabria cual esta corriendo.
 */
@Schema(description = "How the store's simulated partners behave; send all of it or none of it")
public record ParametrosSimulacionRequest(
        @Schema(description = "Where every decision of the partners comes from: same seed, same simulation",
                example = "42")
        @NotNull(message = "La semilla es obligatoria.") Long semilla,
        @Schema(description = "Out of a hundred payments, how many the gateway declines when the rule does not "
                + "decide", example = "10")
        @NotNull(message = "La tasa de rechazo de pagos es obligatoria.")
        @Min(value = 0, message = "Una tasa va de 0 a 100.")
        @Max(value = 100, message = "Una tasa va de 0 a 100.") Integer tasaRechazoPagos,
        @Schema(description = "Ticks the gateway takes to answer", example = "1")
        @NotNull(message = "Los ticks de respuesta de pagos son obligatorios.")
        @Min(value = 1, message = "Un socio tarda al menos un tick en contestar.") Integer ticksRespuestaPagos,
        @Schema(description = "When to decline a payment, in the same language as the flow conditions, read over "
                + "the body of the outgoing message; empty leaves it to the rate", example = "total > 5000")
        @Size(max = 500, message = "La regla no puede superar 500 caracteres.") String reglaRechazoPagos,
        @Schema(description = "Ticks the carrier takes to answer", example = "1")
        @NotNull(message = "Los ticks de respuesta de transporte son obligatorios.")
        @Min(value = 1, message = "Un socio tarda al menos un tick en contestar.") Integer ticksRespuestaTransporte,
        @Schema(description = "Ticks the package takes to arrive once the carrier has it", example = "3")
        @NotNull(message = "Los ticks de entrega son obligatorios.")
        @Min(value = 1, message = "Una entrega tarda al menos un tick.") Integer ticksEntrega,
        @Schema(description = "Out of a hundred shipments, how many are lost", example = "5")
        @NotNull(message = "La tasa de pérdida de envíos es obligatoria.")
        @Min(value = 0, message = "Una tasa va de 0 a 100.")
        @Max(value = 100, message = "Una tasa va de 0 a 100.") Integer tasaPerdidaEnvios,
        @Schema(description = "Out of a hundred notifications, how many do not get through", example = "2")
        @NotNull(message = "La tasa de fallo de notificaciones es obligatoria.")
        @Min(value = 0, message = "Una tasa va de 0 a 100.")
        @Max(value = 100, message = "Una tasa va de 0 a 100.") Integer tasaFalloNotificaciones) {

    /** Lo que se guarda; una peticion sin parametros deja los que la tienda ya tenia. */
    public static ParametrosSimulacion aModelo(ParametrosSimulacionRequest request) {
        return request == null ? null : ParametrosSimulacion.builder()
                .semilla(request.semilla())
                .tasaRechazoPagos(request.tasaRechazoPagos())
                .ticksRespuestaPagos(request.ticksRespuestaPagos())
                .reglaRechazoPagos(request.reglaRechazoPagos())
                .ticksRespuestaTransporte(request.ticksRespuestaTransporte())
                .ticksEntrega(request.ticksEntrega())
                .tasaPerdidaEnvios(request.tasaPerdidaEnvios())
                .tasaFalloNotificaciones(request.tasaFalloNotificaciones())
                .build();
    }
}
