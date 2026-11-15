package com.facimus.procesos.ejecucion.service;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.ejecucion.dto.response.TemporizadorResponse;

/**
 * PR 68 (D35): los temporizadores. Los arma el motor y la publicacion; aqui se disparan los vencidos, se sincronizan
 * los inicios temporizados con la version vigente y se leen.
 */
public interface TemporizadorService {

    /**
     * Dispara los de produccion que ya vencieron con el reloj de pared, de todas las tiendas, uno por transaccion. Lo
     * llama el planificador como sistema. Devuelve cuantos saltaron.
     */
    int dispararVencidos();

    /** Dispara los de prueba de una tienda que vencen en ese tick o antes. Lo llama el tick del reloj de la tienda. */
    int dispararEnTick(Long empresaId, int tick);

    /**
     * Deja armados los inicios temporizados de la version vigente del proceso, y solo esos: los de una version que ya
     * no es la vigente se cancelan. Lo llaman la publicacion y el retiro de una version.
     */
    void sincronizarInicios(Long empresaId, Long procesoId);

    /** Cancela los inicios temporizados de un proceso que se dio de baja. */
    void cancelarInicios(Long empresaId, Long procesoId);

    /** Los temporizadores de un caso, en el orden en que se armaron. */
    List<TemporizadorResponse> delCaso(Long empresaId, Long casoId);

    /** Los inicios temporizados de un proceso, del mas nuevo al mas viejo, por paginas. */
    PageResponse<TemporizadorResponse> delProceso(Long empresaId, Long procesoId, Pageable pagina);
}
