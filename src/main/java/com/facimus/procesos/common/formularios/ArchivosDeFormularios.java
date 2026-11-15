package com.facimus.procesos.common.formularios;

/**
 * PR 69: si un documento sirve como respuesta de un campo {@code ARCHIVO}. Los documentos son de {@code modelado}, que
 * implementa esto, y los pide {@code ejecucion} al completar una tarea: el puerto vive aqui para que ninguno de los dos
 * mire al otro.
 */
public interface ArchivosDeFormularios {

    /** Si el documento es del proceso, en esta tienda, y su ultima version ya paso la revision y se puede bajar. */
    boolean disponible(Long empresaId, Long procesoId, Long documentoId);
}
