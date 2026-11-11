package com.facimus.procesos.modelado.service;

import java.util.List;

import com.facimus.procesos.modelado.dto.response.ArtefactoResponse;

/** PR 55: anotaciones, grupos, objetos de datos y almacenes de un proceso. */
public interface ArtefactoService {

    ArtefactoResponse crear(Long empresaId, Long usuarioId, Long procesoId, DatosDeArtefacto datos);

    /** El tipo no cambia: cambiaria el elemento BPMN que es. */
    ArtefactoResponse editar(Long empresaId, Long usuarioId, Long artefactoId, DatosDeArtefacto datos, Long version);

    /** Se lleva las asociaciones que salen de el o llegan a el. */
    void eliminar(Long empresaId, Long usuarioId, Long artefactoId);

    ArtefactoResponse obtener(Long empresaId, Long artefactoId);

    List<ArtefactoResponse> listar(Long empresaId, Long procesoId);
}
