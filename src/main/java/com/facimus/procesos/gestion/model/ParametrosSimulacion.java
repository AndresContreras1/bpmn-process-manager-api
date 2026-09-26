package com.facimus.procesos.gestion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * D7: como se portan los socios de esta tienda. Son los numeros de una simulacion, no de nada real: cada cuanto
 * rechaza un pago la pasarela, cuanto tarda en contestar, cada cuanto se pierde un envio.
 *
 * <p>La semilla es lo que hace que se pueda repetir: con la misma semilla y los mismos pasos, los socios deciden
 * siempre igual. Sin ella la demo saldria de una manera hoy y de otra manana, y no se podria mostrar dos veces.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Embeddable
public class ParametrosSimulacion {

    /** De aqui salen todas las decisiones de los socios. Cambiarla es cambiar de simulacion, no de reglas. */
    @Column(nullable = false)
    @Builder.Default
    private long semilla = 42L;

    /** De cada cien pagos, cuantos rechaza la pasarela cuando la regla no decide. */
    @Column(name = "tasa_rechazo_pagos", nullable = false)
    @Builder.Default
    private int tasaRechazoPagos = 10;

    @Column(name = "ticks_respuesta_pagos", nullable = false)
    @Builder.Default
    private int ticksRespuestaPagos = 1;

    /**
     * Cuando rechazar un pago, escrito en la gramatica del proyecto sobre el cuerpo del mensaje que sale, por
     * ejemplo {@code total > 5000}. Vacia, manda la tasa; y si decide, la tasa no llega a preguntarse.
     */
    @Column(name = "regla_rechazo_pagos", length = 500)
    private String reglaRechazoPagos;

    @Column(name = "ticks_respuesta_transporte", nullable = false)
    @Builder.Default
    private int ticksRespuestaTransporte = 1;

    /** Cuanto tarda el paquete en llegar desde que el transportista lo recoge. */
    @Column(name = "ticks_entrega", nullable = false)
    @Builder.Default
    private int ticksEntrega = 3;

    @Column(name = "tasa_perdida_envios", nullable = false)
    @Builder.Default
    private int tasaPerdidaEnvios = 5;

    @Column(name = "tasa_fallo_notificaciones", nullable = false)
    @Builder.Default
    private int tasaFalloNotificaciones = 2;
}
