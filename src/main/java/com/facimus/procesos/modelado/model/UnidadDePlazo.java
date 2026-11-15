package com.facimus.procesos.modelado.model;

/**
 * PR 71: en que se cuenta el plazo de una tarea o de un caso. Las horas son de reloj, sin mirar el calendario; los dias
 * habiles se cuentan con el calendario de la tienda (PR 68): un dia habil es una jornada de tiempo habil, sin fines de
 * semana ni festivos. Los topes son los de un temporizador: un ano de horas o 366 dias habiles.
 */
public enum UnidadDePlazo {
    /** Horas de reloj. */
    HORAS(8784),
    /** Dias habiles del calendario de la tienda. */
    DIAS_HABILES(366);

    private final int maximo;

    UnidadDePlazo(int maximo) {
        this.maximo = maximo;
    }

    /** Lo mas que se puede pedir en esta unidad; lo sostiene tambien el check ck_nodos_flujo_plazo. */
    public int maximo() {
        return maximo;
    }

    /** Si esa cantidad cabe: al menos una, y no mas que el tope. */
    public boolean admite(int cantidad) {
        return cantidad >= 1 && cantidad <= maximo;
    }
}
