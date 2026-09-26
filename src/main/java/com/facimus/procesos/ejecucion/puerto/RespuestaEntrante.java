package com.facimus.procesos.ejecucion.puerto;

import java.util.Map;

/**
 * Un mensaje que un socio simulado manda de vuelta. Lleva su clave para que encuentre su caso: adivinarla por el
 * nombre seria meter la respuesta de un pedido en otro.
 *
 * @param enTicks dentro de cuantos ticks llega. Cero es ahora mismo; un transportista que confirma la entrega tres
 *                ticks despues de recoger el paquete lo dice aqui, y el reloj la recoge cuando le toca
 */
public record RespuestaEntrante(String nombre, String clave, Map<String, Object> cuerpo, int enTicks) {

    /** La que llega en el acto, que es lo que hace un socio que solo contesta. */
    public static RespuestaEntrante ahora(String nombre, String clave, Map<String, Object> cuerpo) {
        return new RespuestaEntrante(nombre, clave, cuerpo, 0);
    }
}
