package com.facimus.procesos.gestion.service;

import java.util.List;

import com.facimus.procesos.gestion.dto.response.RolProcesoVistaResponse;

/** HU-17 a HU-20: roles de proceso (solo administrador crea/edita/elimina). */
public interface RolProcesoService {

    List<RolProcesoVistaResponse> listarConUso(Long empresaId);

    RolProcesoVistaResponse crear(Long empresaId, String nombre, String descripcion);

    RolProcesoVistaResponse obtener(Long empresaId, Long rolId);

    RolProcesoVistaResponse editar(Long empresaId, Long rolId, String nombre, String descripcion);

    void eliminar(Long empresaId, Long rolId);
}
