package com.facimus.procesos.common.notificaciones;

/**
 * PR 63: de que avisa una notificacion. Cada tipo dice como llega por correo a quien no eligio otra cosa; agregar uno es
 * agregarlo aqui, a la restriccion de la tabla y a las palabras de los correos ({@code notificacion.<TIPO>.*}).
 */
public enum TipoDeNotificacion {
    /** Alguien la nombro en un comentario. */
    MENCION(EnvioPorCorreo.INMEDIATO),
    /** Alguien contesto en un hilo que ella empezo o en el que escribio. */
    RESPUESTA(EnvioPorCorreo.INMEDIATO),
    /** Alguien dio por resuelto un hilo en el que escribio. */
    HILO_RESUELTO(EnvioPorCorreo.RESUMEN_DIARIO);

    private final EnvioPorCorreo porDefecto;

    TipoDeNotificacion(EnvioPorCorreo porDefecto) {
        this.porDefecto = porDefecto;
    }

    public EnvioPorCorreo porDefecto() {
        return porDefecto;
    }
}
