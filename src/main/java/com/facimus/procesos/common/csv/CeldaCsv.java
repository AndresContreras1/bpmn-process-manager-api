package com.facimus.procesos.common.csv;

/**
 * Una celda de CSV (RFC 4180) que una hoja de calculo no ejecuta, para cada CSV que sale de la API: la bitacora (PR 31)
 * y los casos de la exportacion (PR 66). Un texto que empieza por =, +, -, @, tabulador o retorno lleva delante un
 * apostrofo (la recomendacion de OWASP), y lo que tiene comas, comillas o saltos va entre comillas, con las comillas
 * dobladas. Un numero queda como numero: -5 es un dato, no una formula.
 */
public final class CeldaCsv {

    private static final String COMIENZOS_DE_FORMULA = "=+-@\t\r";

    private CeldaCsv() {
    }

    public static String de(Object valor) {
        if (valor == null) {
            return "";
        }
        String texto = valor.toString();
        if (!(valor instanceof Number) && !texto.isEmpty() && COMIENZOS_DE_FORMULA.indexOf(texto.charAt(0)) >= 0) {
            texto = "'" + texto;
        }
        if (texto.indexOf(',') >= 0 || texto.indexOf('"') >= 0 || texto.indexOf('\n') >= 0
                || texto.indexOf('\r') >= 0) {
            texto = "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }
}
