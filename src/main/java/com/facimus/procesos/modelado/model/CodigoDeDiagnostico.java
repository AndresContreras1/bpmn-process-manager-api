package com.facimus.procesos.modelado.model;

/**
 * El catalogo del diagnostico: cada cosa que el modelo sabe reconocer en un diagrama, con su gravedad. Un codigo de
 * severidad ALTA es un error y bloquea publicar; los demas son advertencias que se muestran mientras se modela, y
 * quien modela decide si las atiende. La letra dice de que va: E de error, A de advertencia.
 *
 * <p>PR 55: un proceso documental no se ejecuta, asi que su diagnostico solo cuenta lo estructural, lo que esta mal
 * se lea como se lea el diagrama; lo demas es de un proceso que va a correr.</p>
 */
public enum CodigoDeDiagnostico {

    /** El pool de la empresa no tiene lanes. */
    E01(Severidad.ALTA),
    /** El pool de la empresa no tiene por donde empezar. */
    E02(Severidad.ALTA),
    /** Ningun evento de fin se alcanza desde el inicio. */
    E03(Severidad.ALTA),
    /** Un nodo al que no se llega desde ningun inicio. */
    E04(Severidad.ALTA),
    /** Un nodo del que no sale nada y que no es un evento de fin. */
    E05(Severidad.ALTA),
    /** Un gateway que no une caminos y tiene menos de dos salidas: no decide nada. */
    E06(Severidad.ALTA),
    /** Una salida de un gateway que decide, sin condicion y sin ser la salida por defecto. */
    E07(Severidad.ALTA),
    /** Una condicion que no esta escrita en la gramatica del motor. */
    E08(Severidad.ALTA),
    /**
     * Un evento de inicio con arcos entrantes, uno de fin con arcos salientes, o uno de borde con arcos entrantes o
     * sin actividad a la que ir pegado.
     */
    E09(Severidad.ALTA, true),
    /** Un evento que espera un mensaje y no tiene ninguno anclado. */
    E10(Severidad.ALTA),
    /** Una actividad de envio o de recepcion, o un fin de mensaje, sin mensaje anclado. */
    E11(Severidad.ALTA),
    /** Un mensaje anclado a un nodo que no esta en el pool de su lado. */
    E12(Severidad.ALTA, true),
    /** Un mensaje entre dos pools modelados por dentro, sin anclar uno de sus dos lados. */
    E13(Severidad.ALTA),
    /** Un gateway que une y reparte a la vez, o un paralelo que no hace ninguna de las dos cosas. */
    E14(Severidad.ALTA),
    /** Mas de un inicio sin mensaje en el mismo pool: un caso se abre por uno solo. */
    E15(Severidad.ALTA),
    /** PR 55: un evento de temporizador sin cuando saltar, o con una expresion que no es ISO 8601. */
    E16(Severidad.ALTA),
    /** PR 55: una tarea sin tipo: en un proceso que corre, nadie sabe quien la hace. */
    E17(Severidad.ALTA),
    /**
     * PR 55: un evento de temporizador en un proceso que corre, cuando los temporizadores no saltaban. PR 68: un ciclo
     * en un temporizador que salta una sola vez (el intermedio y el de borde que interrumpe).
     */
    E18(Severidad.ALTA),
    /** PR 56: un subproceso en un proceso que corre: el motor no los corre. */
    E19(Severidad.ALTA),
    /** PR 56: una actividad de llamada sin proceso, o con uno dado de baja. */
    E20(Severidad.ALTA),
    /** PR 56: procesos que se llaman en circulo. */
    E21(Severidad.ALTA, true),
    /** PR 69: un formulario mal armado, o en un nodo que no llena una persona. */
    E22(Severidad.ALTA),

    /** Un mensaje sin anclar hacia un pool modelado por dentro, sin ningun nodo con ese nombre que lo espere. */
    A01(Severidad.MEDIA),
    /** Un mensaje que nadie manda en el diagrama y que no se declaro de origen externo. */
    A02(Severidad.MEDIA),
    /** Un mensaje que se espera en mitad del flujo sin clave de correlacion. */
    A03(Severidad.MEDIA),
    /** Dos mensajes del proceso con el mismo nombre y la misma clave. */
    A04(Severidad.MEDIA),
    /** Un gateway exclusivo con dos salidas de la misma condicion, o sin salida por defecto. */
    A05(Severidad.MEDIA),
    /** Lo que se rompe si se borra el elemento que se pregunto. */
    A06(Severidad.MEDIA, true),
    /** Una correlacion sin el campo del cuerpo que lleva la clave. */
    A07(Severidad.MEDIA),
    /** Un mensaje a un sistema externo sin decir por donde viaja. */
    A08(Severidad.MEDIA),
    /** Un pool con socio detras que nunca responde nada. */
    A09(Severidad.MEDIA),

    /** Una lane sin ningun nodo. */
    A10(Severidad.BAJA, true),
    /** Un participante sin ningun mensaje que entre o salga. */
    A11(Severidad.BAJA),
    /** Una actividad de servicio sin mensaje anclado. */
    A12(Severidad.BAJA),
    /** El modelo vivo tiene cambios que la version publicada no incluye. */
    A13(Severidad.BAJA, true),
    /** Un gateway inclusivo sin salida por defecto. */
    A14(Severidad.BAJA),
    /** PR 55: una asociacion con un extremo que ya no esta en el diagrama. */
    A15(Severidad.BAJA, true),
    /** PR 56: una actividad de llamada todavia no corre el proceso que llama: el caso sigue de largo. */
    A16(Severidad.MEDIA);

    private final Severidad severidad;
    private final boolean estructural;

    CodigoDeDiagnostico(Severidad severidad) {
        this(severidad, false);
    }

    CodigoDeDiagnostico(Severidad severidad, boolean estructural) {
        this.severidad = severidad;
        this.estructural = estructural;
    }

    /** Si cuenta tambien en un proceso documental: lo que esta mal aunque el proceso no vaya a correr. */
    public boolean esEstructural() {
        return estructural;
    }

    public Severidad severidad() {
        return severidad;
    }

    /** El codigo tal como lo nombra el catalogo: E-01, A-14. */
    public String codigo() {
        return name().charAt(0) + "-" + name().substring(1);
    }

    /** Los errores bloquean publicar; las advertencias, no. */
    public boolean esError() {
        return severidad == Severidad.ALTA;
    }
}
