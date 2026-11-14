package com.facimus.procesos.common.notificaciones;

/**
 * PR 63: de que avisa una notificacion. Cada tipo dice como llega por correo a quien no eligio otra cosa; agregar uno es
 * agregarlo aqui, a la restriccion de la tabla y a las palabras de los correos ({@code notificacion.<TIPO>.*}).
 *
 * <p>PR 71: los de un plazo y el escalamiento senalan un caso, no un comentario: su {@code referenciaId} es el id del
 * caso, y el enlace del correo lleva a el.
 */
public enum TipoDeNotificacion {
    /** Alguien la nombro en un comentario. */
    MENCION(EnvioPorCorreo.INMEDIATO, false),
    /** Alguien contesto en un hilo que ella empezo o en el que escribio. */
    RESPUESTA(EnvioPorCorreo.INMEDIATO, false),
    /** Alguien dio por resuelto un hilo en el que escribio. */
    HILO_RESUELTO(EnvioPorCorreo.RESUMEN_DIARIO, false),
    /** PR 71: una tarea que tiene, o de su rol, o un caso de un proceso suyo, entro en su ultimo cuarto de plazo. */
    PLAZO_POR_VENCER(EnvioPorCorreo.INMEDIATO, true),
    /** PR 71: esa tarea o ese caso paso su plazo. */
    PLAZO_VENCIDO(EnvioPorCorreo.INMEDIATO, true),
    /** PR 71: un temporizador de borde escalo una tarea: a quien la tenia y a quien la supervisa. */
    TAREA_ESCALADA(EnvioPorCorreo.INMEDIATO, true);

    private final EnvioPorCorreo porDefecto;
    private final boolean senalaUnCaso;

    TipoDeNotificacion(EnvioPorCorreo porDefecto, boolean senalaUnCaso) {
        this.porDefecto = porDefecto;
        this.senalaUnCaso = senalaUnCaso;
    }

    public EnvioPorCorreo porDefecto() {
        return porDefecto;
    }

    /** Si su {@code referenciaId} es un caso; si no, es un comentario del proceso. */
    public boolean senalaUnCaso() {
        return senalaUnCaso;
    }
}
