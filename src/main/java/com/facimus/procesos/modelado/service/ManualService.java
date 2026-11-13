package com.facimus.procesos.modelado.service;

import java.util.List;

import com.facimus.procesos.modelado.dto.response.EnlaceDeDescargaResponse;
import com.facimus.procesos.modelado.dto.response.ManualResponse;

/** PR 62: el manual de una version publicada, en HTML y PDF, armado en la cola. */
public interface ManualService {

    /** Pide el manual de una version; la vigente si no se dice cual. La cola lo arma. */
    ManualResponse pedir(Long empresaId, Long usuarioId, Long procesoId, Integer version);

    List<ManualResponse> listar(Long empresaId, Long procesoId);

    ManualResponse obtener(Long empresaId, Long manualId);

    /** Un enlace firmado de unos minutos para bajar el manual en {@code pdf} o {@code html}. */
    EnlaceDeDescargaResponse enlace(Long empresaId, Long manualId, String formato);

    /** Lo que se baja con un enlace firmado. Un enlace que no vale, o vencido, no existe. */
    Descarga descargar(Long empresaId, Long manualId, String formato, long vence, String firma);

    /** El trabajo de la cola: arma el HTML y el PDF y los deja en el almacen. */
    void generar(Long empresaId, Long manualId);
}
