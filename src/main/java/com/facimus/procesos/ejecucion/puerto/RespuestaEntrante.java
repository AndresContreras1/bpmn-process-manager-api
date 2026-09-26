package com.facimus.procesos.ejecucion.puerto;

import java.util.Map;

/**
 * Un mensaje que un socio simulado manda de vuelta. Lleva su clave para que encuentre su caso: adivinarla por el
 * nombre seria meter la respuesta de un pedido en otro.
 */
public record RespuestaEntrante(String nombre, String clave, Map<String, Object> cuerpo) {
}
