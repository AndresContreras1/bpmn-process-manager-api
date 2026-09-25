package com.facimus.procesos.ejecucion.puerto;

import java.util.Map;

/**
 * Lo que le llega a un socio simulado. Es una copia de lo que el proceso mando, no la fila de la bandeja: un socio
 * no toca la base ni sabe de casos, solo de lo que recibe y de lo que contesta.
 *
 * @param casoId            el caso del que salio, para que las respuestas de una misma tienda sean distintas
 * @param nombre            como se llama el mensaje en el diagrama
 * @param clave             el valor con el que la respuesta encontrara su caso
 * @param cuerpo            lo que viaja dentro
 * @param respuestaEsperada como se llama el mensaje con el que el diagrama dice que se contesta, si lo hay
 * @param tick              en que tick del reloj de la tienda le llega
 * @param parametros        lo que la tienda decidio sobre sus socios
 */
public record MensajeParaElSocio(Long casoId, String nombre, String clave, Map<String, Object> cuerpo,
        String respuestaEsperada, int tick, ParametrosDeSimulacion parametros) {
}
