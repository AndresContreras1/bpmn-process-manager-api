package com.facimus.procesos.modelado.service;

import java.io.InputStream;
import java.util.List;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.modelado.dto.response.ImportacionResponse;
import com.facimus.procesos.modelado.dto.response.RevisionBorradorResponse;
import com.facimus.procesos.modelado.model.MotivoDeRevision;

/**
 * PR 40: guardar el borrador entero desde un editor: de a uno, comparando con lo que el cliente leyo, sin escribir si
 * no cambio nada, y dejando puntos de guardado a los que volver.
 */
public interface GuardadoDelModeloService {

    /**
     * El borrador pasa a ser el archivo.
     *
     * @param ifMatch la huella que el cliente leyo; si el borrador ya es otro, 412. Vacio, guarda sin comparar
     */
    ImportacionResponse guardar(Long empresaId, Long usuarioId, RolAcceso rol, Long procesoId, InputStream archivo,
            String ifMatch);

    /** Un punto de guardado ahora, como al cerrar el editor; si el borrador no cambio desde el ultimo, ese mismo. */
    RevisionBorradorResponse puntoDeGuardado(Long empresaId, Long usuarioId, Long procesoId, MotivoDeRevision motivo);

    List<RevisionBorradorResponse> revisiones(Long empresaId, Long procesoId);

    /** El XML de una revision. */
    String modelo(Long empresaId, Long procesoId, Long revisionId);

    /** El borrador vuelve a como estaba en la revision; lo de ahora queda en otra, por si acaso. */
    ImportacionResponse restaurar(Long empresaId, Long usuarioId, RolAcceso rol, Long procesoId, Long revisionId);
}
