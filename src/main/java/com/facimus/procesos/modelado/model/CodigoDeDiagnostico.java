package com.facimus.procesos.modelado.model;

/**
 * El catalogo del diagnostico: cada cosa que el modelo sabe reconocer en un diagrama, con su gravedad. Un codigo de
 * severidad ALTA es un error y bloquea publicar; los demas son advertencias que se muestran mientras se modela, y
 * quien modela decide si las atiende. La letra dice de que va: E de error, A de advertencia.
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
    /** Un evento de inicio con arcos entrantes, o uno de fin con arcos salientes. */
    E09(Severidad.ALTA),
    /** Un evento que espera un mensaje y no tiene ninguno anclado. */
    E10(Severidad.ALTA),
    /** Una actividad de envio o de recepcion, o un fin de mensaje, sin mensaje anclado. */
    E11(Severidad.ALTA),
    /** Un mensaje anclado a un nodo que no esta en el pool de su lado. */
    E12(Severidad.ALTA),
    /** Un mensaje entre dos pools modelados por dentro, sin anclar uno de sus dos lados. */
    E13(Severidad.ALTA),
    /** Un gateway que une y reparte a la vez, o un paralelo que no hace ninguna de las dos cosas. */
    E14(Severidad.ALTA),
    /** Mas de un inicio sin mensaje en el mismo pool: un caso se abre por uno solo. */
    E15(Severidad.ALTA),

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
    A06(Severidad.MEDIA),
    /** Una correlacion sin el campo del cuerpo que lleva la clave. */
    A07(Severidad.MEDIA),
    /** Un mensaje a un sistema externo sin decir por donde viaja. */
    A08(Severidad.MEDIA),
    /** Un pool con socio detras que nunca responde nada. */
    A09(Severidad.MEDIA),

    /** Una lane sin ningun nodo. */
    A10(Severidad.BAJA),
    /** Un participante sin ningun mensaje que entre o salga. */
    A11(Severidad.BAJA),
    /** Una actividad de servicio sin mensaje anclado. */
    A12(Severidad.BAJA),
    // A-13, "hay cambios sin publicar desde la version n", entra con las versiones publicadas: hasta entonces no
    // hay ninguna version con la que comparar el borrador.
    /** Un gateway inclusivo sin salida por defecto. */
    A14(Severidad.BAJA);

    private final Severidad severidad;

    CodigoDeDiagnostico(Severidad severidad) {
        this.severidad = severidad;
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
