package com.facimus.procesos.common.correo;

/**
 * Un correo listo para salir: a quien, con que asunto, y el cuerpo en texto y en HTML, para que cada cliente de correo
 * muestre el que sabe mostrar.
 */
public record CorreoSaliente(String para, String asunto, String texto, String html) {

    /** El cuerpo lleva un enlace de un solo uso: el toString de un record no tiene que llevarlo a un log. */
    @Override
    public String toString() {
        return "CorreoSaliente[para=" + para + ", asunto=" + asunto + "]";
    }
}
