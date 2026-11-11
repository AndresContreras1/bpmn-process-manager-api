package com.facimus.procesos.common.api;

/** PR 40: el valor de una etiqueta de {@code ETag} o {@code If-Match}, sin comillas ni marca de debil. */
public final class EtiquetasHttp {

    private EtiquetasHttp() {
    }

    public static String valor(String etiqueta) {
        String valor = etiqueta.strip();
        if (valor.startsWith("W/")) {
            valor = valor.substring(2);
        }
        if (valor.length() >= 2 && valor.startsWith("\"") && valor.endsWith("\"")) {
            valor = valor.substring(1, valor.length() - 1);
        }
        return valor;
    }

    static boolean esNumero(String valor) {
        return !valor.isEmpty() && valor.chars().allMatch(Character::isDigit);
    }
}
