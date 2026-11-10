package com.facimus.procesos.common.xml;

import com.facimus.procesos.common.SolicitudInvalidaException;

/**
 * PR 52: un XML que no se lee: trae un DTD o entidades, pasa uno de los limites, viene en una codificacion que no se
 * acepta o esta mal formado. Responde 400 con el motivo, como cualquier solicitud invalida.
 */
public class XmlRechazadoException extends SolicitudInvalidaException {

    private static final long serialVersionUID = 1L;

    public XmlRechazadoException(String motivo) {
        super(motivo);
    }

    public XmlRechazadoException(String motivo, Throwable causa) {
        super(motivo);
        initCause(causa);
    }
}
