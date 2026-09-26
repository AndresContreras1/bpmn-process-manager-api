package com.facimus.procesos.ejecucion.puerto;

import java.util.List;
import java.util.Map;

/**
 * D7: quien inventa los pedidos de una tanda simulada. Es otro puerto porque no es lo mismo que atender un
 * mensaje: un cliente que compra no esta contestando a nada, y quien le pide veinte pedidos es una persona por la
 * API, no el proceso.
 *
 * <p>Lo que devuelve son cuerpos de mensaje, nada mas. Entran despues por la misma puerta que cualquier otro
 * mensaje que llega, y por eso un pedido simulado y uno de verdad recorren exactamente el mismo camino.
 */
public interface GeneradorDePedidos {

    /**
     * Los cuerpos de una tanda de pedidos.
     *
     * @param cantidad    cuantos
     * @param plantilla   lo que todos llevan igual; lo que traiga manda sobre lo que el generador invente
     * @param referencias como se llama cada uno, ya numerado y en orden
     * @param parametros  lo que la tienda decidio, de donde sale la semilla con la que se inventan los montos
     */
    List<Map<String, Object>> pedidos(int cantidad, Map<String, Object> plantilla, List<String> referencias,
            ParametrosDeSimulacion parametros);
}
