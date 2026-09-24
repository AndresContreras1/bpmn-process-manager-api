package com.facimus.procesos.modelado.service;

import java.util.List;

import com.facimus.procesos.modelado.dto.response.MensajeResponse;

/** HU-25 a HU-27: mensajes (comunicacion entre pools). */
public interface MensajeService {

    MensajeResponse crear(Long empresaId, Long usuarioId, Long procesoId, DatosDeMensaje datos);

    /**
     * Cambia todo lo del mensaje menos sus pools: cambiar de participante es trazar otro mensaje. Si los datos traen
     * un pool distinto del guardado, la edicion se rechaza en vez de ignorarlo en silencio.
     */
    MensajeResponse editar(Long empresaId, Long usuarioId, Long mensajeId, DatosDeMensaje datos, Long version);

    void eliminar(Long empresaId, Long usuarioId, Long mensajeId);

    List<MensajeResponse> listarPorProceso(Long empresaId, Long procesoId);

    MensajeResponse obtener(Long empresaId, Long mensajeId);
}
