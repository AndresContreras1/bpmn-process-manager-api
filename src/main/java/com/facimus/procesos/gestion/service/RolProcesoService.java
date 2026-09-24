package com.facimus.procesos.gestion.service;

import org.springframework.data.domain.Pageable;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.dto.response.RolProcesoVistaResponse;

/**
 * HU-17 a HU-20: roles de proceso (solo administrador crea/edita/elimina). Cada cambio queda en el historial de la
 * tienda con su autor, por eso todas las escrituras reciben el usuario que las hace (HU-18.5, HU-19.4).
 */
public interface RolProcesoService {

    PageResponse<RolProcesoVistaResponse> buscar(Long empresaId, String nombre, Pageable pageable);

    RolProcesoVistaResponse crear(Long empresaId, Long usuarioId, String nombre, String descripcion);

    RolProcesoVistaResponse obtener(Long empresaId, Long rolId);

    RolProcesoVistaResponse editar(Long empresaId, Long usuarioId, Long rolId, String nombre, String descripcion,
            Long version);

    void eliminar(Long empresaId, Long usuarioId, Long rolId);
}
