package com.facimus.procesos.gestion.service;

import java.util.List;
import java.util.Set;

import com.facimus.procesos.gestion.dto.response.CarpetaResponse;

/** PR 59: el arbol de carpetas del repositorio de procesos. */
public interface CarpetaService {

    List<CarpetaResponse> listar(Long empresaId);

    CarpetaResponse crear(Long empresaId, String nombre, Long padreId);

    CarpetaResponse editar(Long empresaId, Long carpetaId, String nombre, Long padreId, Long version);

    /** Solo se borra vacia: un proceso o una subcarpeta adentro la mantienen. */
    void eliminar(Long empresaId, Long carpetaId);

    /** La carpeta y todas las de adentro. */
    Set<Long> conSubcarpetas(Long empresaId, Long carpetaId);
}
