package com.facimus.procesos.modelado.service.impl;

/**
 * R-35 y R-36: como decide un gateway exclusivo o inclusivo. La regla se comprueba en dos momentos, al tocar un arco
 * que sale del gateway y al cambiar el tipo de un gateway que ya tiene arcos, asi que los mensajes viven en un solo
 * sitio.
 */
final class ReglasDeGateways {

    static final String SIN_CONDICION =
            "Un arco que sale de un gateway exclusivo o inclusivo requiere condicion o ser la salida por defecto.";
    static final String SALIDAS_SIN_CONDICION = "Todos los arcos que salen de un gateway exclusivo o inclusivo "
            + "requieren condicion o ser la salida por defecto.";
    static final String DEFECTO_SIN_GATEWAY = "Solo un gateway exclusivo o inclusivo tiene salida por defecto.";
    static final String DEFECTO_CON_CONDICION = "La salida por defecto no lleva condicion.";
    static final String DEFECTO_REPETIDO = "Un gateway solo puede tener una salida por defecto.";

    private ReglasDeGateways() {
    }
}
