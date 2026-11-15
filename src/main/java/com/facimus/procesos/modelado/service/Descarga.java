package com.facimus.procesos.modelado.service;

import java.io.InputStream;

/**
 * PR 61: lo que se manda al bajar una version.
 *
 * @param tipo el tipo con que se sirve: el real, o {@code application/octet-stream} para lo que un navegador podria
 *        ejecutar
 */
public record Descarga(String nombre, String tipo, long tamano, InputStream contenido) {
}
