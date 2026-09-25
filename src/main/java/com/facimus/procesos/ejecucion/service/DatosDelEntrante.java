package com.facimus.procesos.ejecucion.service;

import java.util.Map;

import com.facimus.procesos.ejecucion.model.OrigenMensajeEntrante;

/**
 * Un mensaje que llega al proceso. Va junto porque las partes se miran entre si: sin clave se busca en el cuerpo
 * por el campo que el mensaje declara, y la clave externa solo tiene sentido al lado de lo demas.
 *
 * @param nombre       el nombre del mensaje tal como lo llama la version publicada
 * @param clave        con que valor busca su caso; vacia, se toma del cuerpo
 * @param cuerpo       lo que trae dentro, que entra a las variables del caso
 * @param claveExterna lo que puso quien lo mando para que repetirlo no lo procese dos veces
 * @param origen       de donde salio: de una persona o de uno de los socios simulados
 */
public record DatosDelEntrante(String nombre, String clave, Map<String, Object> cuerpo, String claveExterna,
        OrigenMensajeEntrante origen) {

    /** Uno mandado a mano por la API, que es como se prueba un proceso sin socios. */
    public static DatosDelEntrante aMano(String nombre, String clave, Map<String, Object> cuerpo,
            String claveExterna) {
        return new DatosDelEntrante(nombre, clave, cuerpo, claveExterna, OrigenMensajeEntrante.MANUAL);
    }
}
