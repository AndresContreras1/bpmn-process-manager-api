package com.facimus.procesos.modelado.service.impl;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.mapper.ActividadMapper;
import com.facimus.procesos.modelado.mapper.ArcoMapper;
import com.facimus.procesos.modelado.mapper.CorrelacionMapper;
import com.facimus.procesos.modelado.mapper.EventoMapper;
import com.facimus.procesos.modelado.mapper.GatewayMapper;
import com.facimus.procesos.modelado.mapper.LaneMapper;
import com.facimus.procesos.modelado.mapper.MensajeMapper;
import com.facimus.procesos.modelado.mapper.PoolMapper;
import com.facimus.procesos.modelado.repository.ActividadRepository;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.CorrelacionRepository;
import com.facimus.procesos.modelado.repository.EventoRepository;
import com.facimus.procesos.modelado.repository.GatewayRepository;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;

import lombok.RequiredArgsConstructor;

/**
 * El modelo vivo de un proceso, en una consulta por tipo de elemento: el numero de sentencias no crece con el tamano
 * del diagrama. Los DTO llevan los id de sus padres, que Hibernate lee de la llave foranea sin cargar la relacion.
 * No comprueba permisos: recibe el proceso que ya paso por la puerta de lectura y la empresa duena de sus elementos,
 * que en un proceso compartido no es la del usuario.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ArmadoDelDiagrama {

    private final PoolRepository poolRepository;
    private final LaneRepository laneRepository;
    private final ActividadRepository actividadRepository;
    private final GatewayRepository gatewayRepository;
    private final EventoRepository eventoRepository;
    private final ArcoRepository arcoRepository;
    private final MensajeRepository mensajeRepository;
    private final CorrelacionRepository correlacionRepository;
    private final PoolMapper poolMapper;
    private final LaneMapper laneMapper;
    private final ActividadMapper actividadMapper;
    private final GatewayMapper gatewayMapper;
    private final EventoMapper eventoMapper;
    private final ArcoMapper arcoMapper;
    private final MensajeMapper mensajeMapper;
    private final CorrelacionMapper correlacionMapper;

    DiagramaResponse armar(ProcesoResponse proceso, boolean compartido, Long empresaDuenaId) {
        Long procesoId = proceso.id();
        return new DiagramaResponse(proceso, compartido,
                poolMapper.toResponses(poolRepository
                        .findAllByProcesoIdAndEmpresaIdOrderByOrdenAsc(procesoId, empresaDuenaId)),
                laneMapper.toResponses(laneRepository.delProcesoEnOrden(procesoId, empresaDuenaId)),
                actividadMapper.toResponses(actividadRepository
                        .findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaDuenaId)),
                gatewayMapper.toResponses(gatewayRepository
                        .findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaDuenaId)),
                eventoMapper.toResponses(eventoRepository
                        .findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaDuenaId)),
                arcoMapper.toResponses(arcoRepository
                        .findAllByPool_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaDuenaId)),
                mensajeMapper.toResponses(mensajeRepository
                        .findAllByProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaDuenaId)),
                correlacionMapper.toResponses(correlacionRepository
                        .findAllByMensaje_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaDuenaId)));
    }
}
