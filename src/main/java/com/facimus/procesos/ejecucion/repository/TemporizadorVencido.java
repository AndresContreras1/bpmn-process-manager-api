package com.facimus.procesos.ejecucion.repository;

/** PR 68: un temporizador que ya tendria que haber saltado, con su tienda: lo que se le pasa a quien lo dispara. */
public record TemporizadorVencido(Long id, Long empresaId) {
}
