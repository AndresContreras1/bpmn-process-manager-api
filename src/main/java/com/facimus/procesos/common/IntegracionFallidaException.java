package com.facimus.procesos.common;

/** 502: un servicio externo no respondio, o respondio algo que esta API no puede usar. No es culpa de quien llama. */
public class IntegracionFallidaException extends RuntimeException {

    public IntegracionFallidaException(String mensaje) {
        super(mensaje);
    }

    public IntegracionFallidaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
