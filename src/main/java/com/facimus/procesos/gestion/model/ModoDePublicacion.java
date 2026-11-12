package com.facimus.procesos.gestion.model;

/** PR 64: como publica una tienda sus procesos. */
public enum ModoDePublicacion {
    /** Quien puede publicar, publica: lo de siempre, y lo que tiene una tienda que nunca eligio otra cosa. */
    DIRECTA,
    /** Se pide una revision, y publica la ultima aprobacion que hacia falta. */
    CON_APROBACION
}
