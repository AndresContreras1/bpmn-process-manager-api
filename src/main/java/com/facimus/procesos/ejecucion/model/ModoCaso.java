package com.facimus.procesos.ejecucion.model;

/**
 * PR 68 (D35): con que tiempo corre un caso. Los dos usan el mismo motor y la misma version; cambia de donde sale la
 * hora a la que vence un temporizador y quien contesta los mensajes que salen.
 */
public enum ModoCaso {

    /**
     * El reloj de pared (un {@code java.time.Clock}) y el calendario laboral de la tienda. Los mensajes que salen se
     * quedan en la bandeja para la integracion que los lea, y las respuestas llegan por la entrada de mensajes: los
     * socios simulados no contestan a un caso de produccion.
     */
    PRODUCCION,

    /**
     * D8: los ticks del reloj de la tienda y los socios simulados del v3, enteros. Es el modo de lo que se abria antes
     * del PR 68, y sigue siendo el de un caso que se abre sin decir modo, el de uno que abre un mensaje y el de una
     * prueba del borrador.
     */
    PRUEBA
}
