package com.facimus.procesos.gestion.service;

/**
 * PR 64: el borrador de un proceso congelado por una solicitud de revision, listo para publicarse tal cual: la
 * instantanea que se guardara como version, el numero que tendra y la version vigente cuando se congelo (vacia si no
 * habia ninguna). Si esa vigente cambia antes de aprobarse, lo que se aprobo ya no es lo que se publicaria.
 */
public record BorradorCongelado(int numero, Integer versionBase, Instantanea instantanea) {
}
