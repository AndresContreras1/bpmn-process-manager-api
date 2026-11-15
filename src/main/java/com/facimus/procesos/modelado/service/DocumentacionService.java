package com.facimus.procesos.modelado.service;

import java.util.List;

import com.facimus.procesos.modelado.dto.response.CaracterizacionResponse;
import com.facimus.procesos.modelado.dto.response.DocumentacionResponse;
import com.facimus.procesos.modelado.dto.response.VistaPreviaResponse;

/** PR 62: la documentacion en Markdown de un proceso y de sus elementos, y su ficha de caracterizacion. */
public interface DocumentacionService {

    /** Lo escrito del proceso: la del proceso entero y la de cada elemento. */
    List<DocumentacionResponse> listar(Long empresaId, Long procesoId);

    /** Escribe la del proceso o la de un elemento; un texto vacio la borra. */
    DocumentacionResponse guardar(Long empresaId, Long usuarioId, Long procesoId, String elemento, String markdown,
            Long version);

    /** El HTML que quedaria guardado, para verlo antes. */
    VistaPreviaResponse vistaPrevia(String markdown);

    CaracterizacionResponse caracterizacion(Long empresaId, Long procesoId);

    CaracterizacionResponse guardarCaracterizacion(Long empresaId, Long usuarioId, Long procesoId,
            DatosDeCaracterizacion ficha);
}
