package com.facimus.procesos.modelado.service.impl;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.facimus.procesos.gestion.event.ProcesoEliminado;
import com.facimus.procesos.modelado.repository.ActividadRepository;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.CorrelacionRepository;
import com.facimus.procesos.modelado.repository.GatewayRepository;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;

import lombok.RequiredArgsConstructor;

/**
 * HU-06: un proceso dado de baja se lleva su modelo. Los elementos quedan en la base, dados de baja, y ya no se ven ni
 * se cambian por su id. Corre de forma sincrona dentro de la transaccion que da de baja el proceso: si falla, el
 * proceso tampoco se da de baja.
 */
@Component
@RequiredArgsConstructor
public class ModeloDelProcesoListener {

    private final CorrelacionRepository correlacionRepository;
    private final MensajeRepository mensajeRepository;
    private final ArcoRepository arcoRepository;
    private final ActividadRepository actividadRepository;
    private final GatewayRepository gatewayRepository;
    private final LaneRepository laneRepository;
    private final PoolRepository poolRepository;

    /** De hijos a padres, como cualquier borrado: ningun elemento activo queda apuntando a uno dado de baja. */
    @EventListener
    public void darDeBajaElModelo(ProcesoEliminado evento) {
        Long procesoId = evento.procesoId();
        Long empresaId = evento.empresaId();
        correlacionRepository.deleteAll(
                correlacionRepository.findAllByMensaje_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId));
        mensajeRepository.deleteAll(mensajeRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId));
        arcoRepository.deleteAll(arcoRepository.findAllByPool_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId));
        actividadRepository.deleteAll(
                actividadRepository.findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId));
        gatewayRepository.deleteAll(
                gatewayRepository.findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId));
        laneRepository.deleteAll(laneRepository.delProcesoEnOrden(procesoId, empresaId));
        poolRepository.deleteAll(poolRepository.findAllByProcesoIdAndEmpresaIdOrderByOrdenAsc(procesoId, empresaId));
    }
}
