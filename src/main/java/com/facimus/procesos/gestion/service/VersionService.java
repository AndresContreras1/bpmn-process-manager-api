package com.facimus.procesos.gestion.service;

import java.util.List;
import java.util.Optional;

import com.facimus.procesos.gestion.dto.response.VersionResponse;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.VersionProceso;

/**
 * Las versiones publicadas de un proceso (D2). Publicar es de ProcesoService, que es quien conoce el estado del
 * proceso; aqui se guarda la instantanea, se leen las versiones y se retiran.
 */
public interface VersionService {

    /**
     * Que numero le tocaria a la proxima version. Se pregunta antes de tomar la instantanea, porque el diagrama que
     * se guarda dice de que version es.
     */
    int siguienteNumero(Long empresaId, Long procesoId);

    /**
     * Guarda la instantanea como la version numero del proceso. El proceso llega ya comprobado: quien publica es
     * ProcesoService, que valida el diagnostico y que haya cambios.
     */
    void publicar(Proceso proceso, int numero, Long usuarioId, Instantanea instantanea);

    List<VersionResponse> listar(Long empresaId, Long procesoId);

    VersionResponse obtener(Long empresaId, Long procesoId, int numero);

    /** El diagrama de esa version, en el JSON en que se guardo. */
    String definicion(Long empresaId, Long procesoId, int numero);

    /**
     * El diagrama de la version vigente, para quien solo puede ver lo publicado (HU-23). Vacio si el proceso no
     * tiene ninguna, porque nunca se publico o porque se retiraron todas.
     */
    Optional<String> definicionVigente(Long empresaPropietariaId, Long procesoId);

    /**
     * La version vigente de un proceso propio y activo: la unica sobre la que se abren casos nuevos (R-47). Se
     * devuelve la entidad y no un DTO porque el caso se ata a ella, y de ella cuelgan el proceso y la tienda.
     */
    Optional<VersionProceso> vigente(Long empresaId, Long procesoId);

    /** Retira una version: deja de ser la vigente y el proceso pasa a la anterior que siga en pie, si queda alguna. */
    VersionResponse retirar(Long empresaId, Long procesoId, int numero, Long usuarioId);
}
