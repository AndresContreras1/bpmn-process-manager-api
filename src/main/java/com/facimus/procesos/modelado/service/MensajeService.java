package com.facimus.procesos.modelado.service;

import java.util.List;

import com.facimus.procesos.modelado.dto.response.MensajeResponse;

/** HU-25 a HU-27: mensajes (comunicacion entre pools). */
public interface MensajeService {

    MensajeResponse crear(Long empresaId, Long procesoId, String nombre, String contenido, Long poolOrigenId,
            Long poolDestinoId);

    MensajeResponse editar(Long empresaId, Long mensajeId, String nombre, String contenido);

    void eliminar(Long empresaId, Long mensajeId);

    List<MensajeResponse> listarPorProceso(Long empresaId, Long procesoId);

    MensajeResponse obtener(Long empresaId, Long mensajeId);
}
