package com.facimus.procesos.common;

/**
 * 400: un enlace del correo que ya no sirve, porque se uso, vencio o nunca existio. La respuesta no dice cual de las
 * tres: a quien lo prueba al azar no le cuenta nada.
 */
public class EnlaceNoValidoException extends RuntimeException {

    public EnlaceNoValidoException() {
        super("El enlace ya no sirve: se usó, venció o no existe. Pide uno nuevo.");
    }
}
