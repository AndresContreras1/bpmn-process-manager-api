package com.facimus.procesos.gestion.service;

import java.time.LocalDate;

import com.facimus.procesos.gestion.model.OrigenFestivo;

/**
 * PR 68: un festivo de un calendario laboral: el dia, como se llama y de donde sale.
 *
 * @param origen {@code CALCULADO} si sale de la ley del pais, {@code EDITADO} si la tienda lo agrego o lo renombro
 */
public record Festivo(LocalDate fecha, String nombre, OrigenFestivo origen) {

    static Festivo calculado(LocalDate fecha, String nombre) {
        return new Festivo(fecha, nombre, OrigenFestivo.CALCULADO);
    }
}
