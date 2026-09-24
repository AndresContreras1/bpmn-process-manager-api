package com.facimus.procesos.gestion.service;

/**
 * El diagrama de un proceso congelado en un momento: el JSON que se guarda y la huella con la que se compara. La
 * huella no es la del JSON entero, sino la de su forma canonica, que deja fuera lo que cambia sin que cambie el
 * diagrama (la version optimista, la fecha del ultimo guardado, el estado del proceso).
 */
public record Instantanea(String definicion, String huella) {
}
