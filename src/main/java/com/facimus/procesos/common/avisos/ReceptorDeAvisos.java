package com.facimus.procesos.common.avisos;

/**
 * PR 63: quien recibe los avisos de un canal de PostgreSQL ({@code NOTIFY}) en esta replica, para mandarlos a sus
 * conexiones abiertas. Cada modulo declara el suyo; una sola escucha por replica los atiende a todos.
 */
public interface ReceptorDeAvisos {

    /** El canal de {@code LISTEN}: letras minusculas y guiones bajos. */
    String canal();

    /** Un aviso que llego por ese canal, como se publico. */
    void recibir(String aviso);

    /** Cada 25 segundos: un comentario a cada conexion para mantenerla y ver si sigue viva. */
    void latir();
}
