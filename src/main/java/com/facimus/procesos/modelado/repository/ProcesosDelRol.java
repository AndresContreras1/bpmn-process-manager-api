package com.facimus.procesos.modelado.repository;

/** Cuantos procesos activos usan un rol: una fila del conteo agrupado de una pagina de roles. */
public record ProcesosDelRol(Long rolId, Long procesos) {
}
