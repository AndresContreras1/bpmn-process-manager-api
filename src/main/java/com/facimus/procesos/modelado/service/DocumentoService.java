package com.facimus.procesos.modelado.service;

import java.io.InputStream;
import java.util.List;

import com.facimus.procesos.modelado.dto.response.DocumentoDetalleResponse;
import com.facimus.procesos.modelado.dto.response.DocumentoResponse;
import com.facimus.procesos.modelado.dto.response.EnlaceDeDescargaResponse;

/** PR 61: los documentos de un proceso y de sus elementos. */
public interface DocumentoService {

    /** Un documento nuevo, con su primer archivo. */
    DocumentoResponse subir(Long empresaId, Long usuarioId, Long procesoId, String bpmnId, String nombre,
            InputStream contenido);

    /** Otra version de un documento que ya existe. */
    DocumentoResponse subirVersion(Long empresaId, Long usuarioId, Long documentoId, String nombre,
            InputStream contenido);

    /** Los documentos del proceso, o solo los de un elemento. */
    List<DocumentoResponse> listar(Long empresaId, Long procesoId, String bpmnId);

    DocumentoDetalleResponse obtener(Long empresaId, Long documentoId);

    /** Un enlace firmado para bajar una version disponible: la ultima si no se dice cual. */
    EnlaceDeDescargaResponse enlace(Long empresaId, Long documentoId, Integer numero);

    void eliminar(Long empresaId, Long usuarioId, Long documentoId);

    /** Lo que se baja con un enlace firmado. Un enlace que no vale, o vencido, no existe. */
    Descarga descargar(Long empresaId, Long documentoId, int numero, long vence, String firma);

    /** El trabajo de la cola: el antivirus y, si es un ZIP por dentro, que no sea una bomba. */
    void revisar(Long empresaId, Long versionId);
}
