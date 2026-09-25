package com.facimus.procesos.ejecucion.service;

/**
 * Los cuatro numeros que Actuator publica de la operacion. Son de toda la instalacion y no de una tienda: un
 * medidor con una etiqueta por tienda crearia una serie nueva cada vez que alguien se registra, y quien mira las
 * metricas de un servidor esta mirando el servidor.
 *
 * <p>Lo que cuenta cada tienda de lo suyo es el tablero, que se pide con su token y responde solo lo de ella.
 */
public interface MedidoresDeLaOperacion {

    /** Pedidos corriendo ahora mismo. */
    long casosAbiertos();

    /** Tareas esperando en alguna bandeja. */
    long tareasPendientes();

    /** Mensajes mandados que todavia no han llegado a su destino. */
    long salientesPendientes();

    /** Mensajes que llegaron antes de que nadie los esperara, o que un socio dejo dichos para mas adelante. */
    long entrantesPendientes();
}
