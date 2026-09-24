package com.facimus.procesos.gestion.service;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.dto.response.HistorialCambioResponse;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.Usuario;

/** Bitacora de cambios de cada proceso: solo se agrega y se consulta. */
public interface HistorialCambioService {

    void registrar(Proceso proceso, Usuario autor, String descripcion);

    /** Anota un cambio que hizo un usuario de la empresa, que se busca por su id. */
    void registrar(Long empresaId, Long usuarioId, Proceso proceso, String descripcion);

    /**
     * D15: anota un cambio de la tienda que no es de ningun proceso, como el alta de un usuario o un rol nuevo. El
     * recurso y su id dicen de que habla la linea.
     */
    void registrarDeTienda(Long empresaId, Long usuarioId, RecursoDeHistorial recurso, Long recursoId,
            String descripcion);

    List<HistorialCambioResponse> listarPorProceso(Long empresaId, Long procesoId);

    /** Todo lo que paso en la tienda, procesos incluidos. Solo lo lee el administrador. */
    PageResponse<HistorialCambioResponse> listarDeLaTienda(Long empresaId, Pageable pageable);
}
