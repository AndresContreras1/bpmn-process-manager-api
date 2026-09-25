package com.facimus.procesos.ejecucion.puerto;

import com.facimus.procesos.modelado.model.Integracion;

/**
 * D7: el participante del otro lado de un mensaje. Nada de esto es real: no hay red, no hay pasarela y no hay
 * transportista. Hay un socio simulado que recibe lo que el proceso manda, decide si lo da por entregado y
 * contesta lo que el diagrama dice que contesta.
 *
 * <p>Es un puerto y no una clase porque la ejecucion no tiene que saber quien la atiende: pide el socio de una
 * clase de participante y trabaja con lo que le devuelvan. Los socios de verdad viven fuera, en su propio modulo,
 * y no pueden mirar hacia dentro del motor.
 */
public interface SocioSimulado {

    /** Con que clase de participante habla. El que dice NINGUNA atiende a los que no tienen socio propio. */
    Integracion integracion();

    /**
     * Cuanto tarda en contestar, en ticks del reloj de la tienda. Se pregunta al escribir el mensaje en la bandeja,
     * porque es lo que decide en que tick le toca llegar; nunca menos de uno, o contestaria antes de recibir.
     */
    int latencia(ParametrosDeSimulacion parametros);

    /** Que hace con el mensaje que le llega: si lo da por entregado y que contesta. */
    RespuestaDelSocio recibir(MensajeParaElSocio mensaje);
}
