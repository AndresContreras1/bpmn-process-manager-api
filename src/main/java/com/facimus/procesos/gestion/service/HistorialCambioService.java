package com.facimus.procesos.gestion.service;

import java.util.List;

import com.facimus.procesos.gestion.dto.response.HistorialCambioResponse;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;

/** Bitacora de cambios de cada proceso: solo se agrega y se consulta. */
public interface HistorialCambioService {

    void registrar(Proceso proceso, Usuario autor, String descripcion);

    /** Anota un cambio que hizo un usuario de la empresa, que se busca por su id. */
    void registrar(Long empresaId, Long usuarioId, Proceso proceso, String descripcion);

    List<HistorialCambioResponse> listarPorProceso(Long empresaId, Long procesoId);
}
