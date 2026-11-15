package com.facimus.procesos.ejecucion.repository;

/** PR 71: una tarea o un caso cuyo plazo ya cambio de estado, con su tienda: lo que se le pasa a quien lo mueve. */
public record PlazoQueCambia(Long id, Long empresaId) {
}
