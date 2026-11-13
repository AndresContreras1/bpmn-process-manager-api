package com.facimus.procesos.modelado.service;

/** PR 62: lo que dice la ficha de caracterizacion, y la version que se leyo. */
public record DatosDeCaracterizacion(String objetivo, String alcance, String entradas, String salidas,
        String proveedores, String clientes, String indicadores, String riesgos, String normativa, Long version) {
}
