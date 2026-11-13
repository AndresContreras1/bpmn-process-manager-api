package com.facimus.procesos.common.almacen;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;

/**
 * PR 61: donde viven los archivos que no van en la base: los documentos de cada tienda y el ancla diaria de la
 * bitacora. Cada clave empieza por la tienda ({@code empresas/<id>/...}), asi que borrar una tienda es borrar su
 * prefijo. En produccion es un bucket S3 (o compatible, como SeaweedFS); sin uno configurado, una carpeta del disco.
 */
public interface Almacen {

    /** Guarda el archivo con esa clave. Si ya habia uno con la misma clave, lo reemplaza. */
    void guardar(String clave, Path archivo, String tipo);

    /** El contenido de una clave. Quien lo abre lo cierra. */
    InputStream abrir(String clave);

    /**
     * Borra cada objeto que empieza por ese prefijo; no falla si no habia ninguno.
     *
     * @return cuantos objetos borro (PR 66: el certificado de borrado de una tienda lo cuenta)
     */
    int borrarPrefijo(String prefijo);

    /**
     * Guarda algo que nadie puede cambiar ni borrar hasta esa fecha, ni con las credenciales de la API: en S3, con
     * Object Lock en modo compliance. Es para el ancla de la bitacora, que existe para probar que no la tocaron.
     *
     * @return {@code true} si el almacen garantiza el bloqueo; un disco no puede
     */
    boolean guardarInmutable(String clave, byte[] contenido, Instant hasta);

    /**
     * PR 66: lo que se guardo con {@link #guardarInmutable}, leido de donde quedo: en S3, el bucket con Object Lock.
     * Quien lo abre lo cierra.
     */
    InputStream abrirInmutable(String clave);

    /** Que almacen es, para el log y el runbook. */
    String descripcion();
}
