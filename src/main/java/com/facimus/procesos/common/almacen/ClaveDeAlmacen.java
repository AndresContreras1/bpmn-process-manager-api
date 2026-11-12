package com.facimus.procesos.common.almacen;

import java.util.regex.Pattern;

/**
 * PR 61: las claves del almacen. Siempre empiezan por la tienda, y solo llevan letras, numeros, guiones y barras: un
 * nombre de archivo nunca entra en una clave, asi que nadie puede salirse de su prefijo con un {@code ../}.
 */
public final class ClaveDeAlmacen {

    private static final Pattern VALIDA = Pattern.compile("[a-z0-9][a-z0-9/_-]{0,250}");

    private ClaveDeAlmacen() {
    }

    /** El prefijo de todo lo de una tienda. */
    public static String deEmpresa(long empresaId) {
        return "empresas/" + empresaId + "/";
    }

    /** La clave de una version de un documento: no dice nada del archivo, solo de donde es. */
    public static String deDocumento(long empresaId, long documentoId, int version, String sufijo) {
        return comprobar(deEmpresa(empresaId) + "documentos/" + documentoId + "/" + version + "-" + sufijo);
    }

    /** El ancla de la bitacora de una tienda en un dia. */
    public static String deAncla(long empresaId, String dia) {
        return comprobar("anclas/" + empresaId + "/" + dia);
    }

    /** Rechaza una clave con algo que no sea lo de arriba. */
    public static String comprobar(String clave) {
        if (clave == null || !VALIDA.matcher(clave).matches() || clave.contains("//") || clave.contains("..")) {
            throw new IllegalArgumentException("Clave de almacen no valida: " + clave);
        }
        return clave;
    }
}
