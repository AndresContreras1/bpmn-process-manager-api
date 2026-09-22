package com.facimus.procesos.modelado.model;

public enum TipoGateway {
    EXCLUSIVO,
    PARALELO,
    INCLUSIVO;

    /** El exclusivo y el inclusivo eligen por condicion los arcos que salen de ellos; el paralelo los sigue todos. */
    public boolean eligePorCondicion() {
        return this == EXCLUSIVO || this == INCLUSIVO;
    }
}
