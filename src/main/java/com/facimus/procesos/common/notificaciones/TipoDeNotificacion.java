package com.facimus.procesos.common.notificaciones;

/**
 * PR 63: de que avisa una notificacion. Cada tipo dice como llega por correo a quien no eligio otra cosa, y como se
 * llama en el enlace de la web lo que senala su referencia; agregar uno es agregarlo aqui, a la restriccion de la
 * tabla y a las palabras de los correos ({@code notificacion.<TIPO>.*}).
 */
public enum TipoDeNotificacion {
    /** Alguien la nombro en un comentario. */
    MENCION(EnvioPorCorreo.INMEDIATO, Referencia.COMENTARIO),
    /** Alguien contesto en un hilo que ella empezo o en el que escribio. */
    RESPUESTA(EnvioPorCorreo.INMEDIATO, Referencia.COMENTARIO),
    /** Alguien dio por resuelto un hilo en el que escribio. */
    HILO_RESUELTO(EnvioPorCorreo.RESUMEN_DIARIO, Referencia.COMENTARIO),
    /** PR 64: alguien pidio una revision que ella puede aprobar. */
    APROBACION_PEDIDA(EnvioPorCorreo.INMEDIATO, Referencia.SOLICITUD),
    /** PR 64: un aprobador aprobo su solicitud y todavia faltan otras aprobaciones. */
    SOLICITUD_APROBADA(EnvioPorCorreo.RESUMEN_DIARIO, Referencia.SOLICITUD),
    /** PR 64: su solicitud tuvo todas sus aprobaciones y se publico. */
    SOLICITUD_PUBLICADA(EnvioPorCorreo.INMEDIATO, Referencia.SOLICITUD),
    /** PR 64: su solicitud tuvo todas sus aprobaciones, pero ya no se podia publicar. */
    SOLICITUD_SIN_PUBLICAR(EnvioPorCorreo.INMEDIATO, Referencia.SOLICITUD),
    /** PR 64: un aprobador le pidio cambios. */
    CAMBIOS_PEDIDOS(EnvioPorCorreo.INMEDIATO, Referencia.SOLICITUD),
    /** PR 64: un aprobador rechazo su solicitud. */
    SOLICITUD_RECHAZADA(EnvioPorCorreo.INMEDIATO, Referencia.SOLICITUD),
    /** PR 64: llego la fecha de revision de un proceso del que es duena. */
    RECERTIFICACION_PENDIENTE(EnvioPorCorreo.INMEDIATO, Referencia.NINGUNA),
    /** PR 64: esa revision paso de su fecha sin que nadie confirmara que el proceso sigue vigente. */
    RECERTIFICACION_VENCIDA(EnvioPorCorreo.INMEDIATO, Referencia.NINGUNA);

    /** Que senala la referencia de una notificacion, con el nombre del parametro del enlace a la web. */
    public enum Referencia {
        COMENTARIO("comentario"),
        SOLICITUD("solicitud"),
        NINGUNA(null);

        private final String parametro;

        Referencia(String parametro) {
            this.parametro = parametro;
        }

        /** El parametro del enlace; vacio si la notificacion solo senala el proceso. */
        public String parametro() {
            return parametro;
        }
    }

    private final EnvioPorCorreo porDefecto;
    private final Referencia referencia;

    TipoDeNotificacion(EnvioPorCorreo porDefecto, Referencia referencia) {
        this.porDefecto = porDefecto;
        this.referencia = referencia;
    }

    public EnvioPorCorreo porDefecto() {
        return porDefecto;
    }

    public Referencia referencia() {
        return referencia;
    }
}
