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

    /** El prefijo bajo el que va cada archivo de una tienda. */
    public static String deEmpresa(long empresaId) {
        return "empresas/" + empresaId + "/";
    }

    /** La clave de una version de un documento: no dice nada del archivo, solo de donde es. */
    public static String deDocumento(long empresaId, long documentoId, int version, String sufijo) {
        return comprobar(deEmpresa(empresaId) + "documentos/" + documentoId + "/" + version + "-" + sufijo);
    }

    /** PR 62: un archivo de un manual: su HTML o su PDF. */
    public static String deManual(long empresaId, long manualId, String formato) {
        return comprobar(deEmpresa(empresaId) + "manuales/" + manualId + "/" + formato);
    }

    /** PR 66: la carpeta de una exportacion completa de la tienda; al vencer se borra entera. */
    public static String carpetaDeExportacion(long empresaId, long exportacionId) {
        return comprobar(deEmpresa(empresaId) + "exportaciones/" + exportacionId + "/");
    }

    /** PR 66: el ZIP de una exportacion, dentro de su carpeta. */
    public static String deExportacion(long empresaId, long exportacionId) {
        return comprobar(carpetaDeExportacion(empresaId, exportacionId) + "organizacion");
    }

    /**
     * PR 66: el certificado de borrado de una tienda. Va fuera de su prefijo, como sus anclas: lo que se borra con la
     * tienda no puede ser lo que prueba que se borro.
     */
    public static String deCertificado(long empresaId) {
        return comprobar("certificados/" + empresaId + "/borrado");
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
