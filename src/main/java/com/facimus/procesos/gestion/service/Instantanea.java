package com.facimus.procesos.gestion.service;

/**
 * El diagrama de un proceso congelado en un momento: el JSON que se guarda, la huella con la que se compara y, desde
 * el PR 53, el mismo diagrama en BPMN 2.0.2. La huella no es la del JSON entero, sino la de su forma canonica, que deja
 * fuera lo que cambia sin que cambie el diagrama (la version optimista, la fecha del ultimo guardado, el estado del
 * proceso).
 */
public record Instantanea(String definicion, String huella, String bpmn) {
}
