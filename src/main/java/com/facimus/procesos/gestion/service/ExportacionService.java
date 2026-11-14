package com.facimus.procesos.gestion.service;

import java.io.InputStream;

import org.springframework.data.domain.Pageable;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.dto.response.ExportacionResponse;

/**
 * PR 66: la exportacion completa de una tienda, para llevarse lo suyo. El administrador la pide, la cola arma un ZIP
 * con cada proceso, documento, comentario, caso, persona y la bitacora, con un manifiesto de huellas, y el enlace para
 * bajarlo le llega por correo, firmado y por 24 horas. Despues el ZIP se borra.
 */
public interface ExportacionService {

    /** Pide una exportacion; la cola la arma. Si la tienda ya tiene una en curso, 409. */
    ExportacionResponse pedir(Long empresaId, Long usuarioId);

    /** Si la tienda tiene una en curso: la baja no pide otra, porque el enlace de esa ya va a llegar. */
    boolean hayEnCurso(Long empresaId);

    /** Las de la tienda, de la mas nueva a la mas vieja, de a una pagina. */
    PageResponse<ExportacionResponse> listar(Long empresaId, Pageable pagina);

    ExportacionResponse obtener(Long empresaId, Long exportacionId);

    /**
     * El ZIP, con el enlace firmado del correo. Un enlace que no vale, vencido, de algo que ya no esta o de alguien que
     * ya no es un administrador activo de la tienda no existe.
     */
    Descarga descargar(Long empresaId, Long exportacionId, long vence, String firma);

    /** El trabajo de la cola: arma el ZIP, lo deja en el almacen y manda el enlace por correo. */
    void generar(Long empresaId, Long exportacionId);

    /**
     * Borra del almacen las que vencieron y da por fallidas las que la cola no termino a tiempo, en todas las tiendas.
     * Devuelve cuantas cambio.
     */
    int limpiar();

    /** Lo que se baja: el nombre del archivo, su tamano y su contenido, que cierra quien lo lee. */
    record Descarga(String nombre, long tamano, InputStream contenido) {
    }
}
