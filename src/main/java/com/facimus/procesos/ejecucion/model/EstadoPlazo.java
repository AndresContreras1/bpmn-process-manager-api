package com.facimus.procesos.ejecucion.model;

/**
 * PR 71: como va el plazo de una tarea o de un caso. Solo avanza: una tarea por vencer no vuelve a estar a tiempo, y
 * una vencida se queda vencida aunque se complete, que es lo que cuentan las metricas de cumplimiento.
 */
public enum EstadoPlazo {
    /** Le queda mas de un cuarto del plazo. */
    A_TIEMPO,
    /** Entro en el ultimo cuarto de su plazo: se avisa una vez. */
    POR_VENCER,
    /** Paso su plazo: se avisa una vez, y si se completa, se completo tarde. */
    VENCIDA;

    /** El mas avanzado de los dos: un plazo no retrocede. */
    public EstadoPlazo yA(EstadoPlazo otro) {
        return otro.ordinal() > ordinal() ? otro : this;
    }
}
