package com.facimus.procesos.modelado.service;

import java.io.InputStream;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.modelado.dto.response.ImportacionResponse;

/** PR 54: un archivo BPMN de otra herramienta (o de esta) al modelo. */
public interface ImportacionBpmnService {

    /**
     * Un proceso nuevo, en borrador, con lo que trae el archivo.
     *
     * @param nombre el del proceso; vacio, el que trae el archivo
     */
    ImportacionResponse importar(Long empresaId, Long usuarioId, RolAcceso rol, InputStream archivo, String nombre);

    /** El borrador de un proceso de la tienda pasa a ser lo que trae el archivo, por diferencias. */
    ImportacionResponse reemplazar(Long empresaId, Long usuarioId, RolAcceso rol, Long procesoId,
            InputStream archivo);
}
