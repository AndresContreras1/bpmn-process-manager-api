package com.facimus.procesos.modelado.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.gestion.dto.response.ProcesoLectura;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.mapper.ActividadMapper;
import com.facimus.procesos.modelado.mapper.ArcoMapper;
import com.facimus.procesos.modelado.mapper.CorrelacionMapper;
import com.facimus.procesos.modelado.mapper.GatewayMapper;
import com.facimus.procesos.modelado.mapper.LaneMapper;
import com.facimus.procesos.modelado.mapper.MensajeMapper;
import com.facimus.procesos.modelado.mapper.PoolMapper;
import com.facimus.procesos.modelado.repository.ActividadRepository;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.CorrelacionRepository;
import com.facimus.procesos.modelado.repository.GatewayRepository;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;
import com.facimus.procesos.modelado.service.DiagramaService;

import lombok.RequiredArgsConstructor;

/**
 * Una consulta por tipo de elemento, filtrada por proceso y empresa: el numero de sentencias no crece con el tamano
 * del diagrama. Los DTO llevan los id de sus padres, que Hibernate lee de la llave foranea sin cargar la relacion.
 * El proceso entra por la puerta de lectura, asi que tambien se dibuja uno compartido por otra empresa (HU-23).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiagramaServiceImpl implements DiagramaService {

    private final ProcesoService procesoService;
    private final PoolRepository poolRepository;
    private final LaneRepository laneRepository;
    private final ActividadRepository actividadRepository;
    private final GatewayRepository gatewayRepository;
    private final ArcoRepository arcoRepository;
    private final MensajeRepository mensajeRepository;
    private final CorrelacionRepository correlacionRepository;
    private final PoolMapper poolMapper;
    private final LaneMapper laneMapper;
    private final ActividadMapper actividadMapper;
    private final GatewayMapper gatewayMapper;
    private final ArcoMapper arcoMapper;
    private final MensajeMapper mensajeMapper;
    private final CorrelacionMapper correlacionMapper;

    @Override
    public DiagramaResponse obtener(Long empresaId, Long procesoId) {
        // Responde 404 si el proceso esta eliminado, o si no es de la empresa ni se lo compartieron.
        ProcesoLectura lectura = procesoService.obtenerParaLectura(empresaId, procesoId);
        // Los elementos son de la empresa duena, que en un proceso compartido no es la del usuario.
        Long duena = lectura.empresaPropietariaId();
        return new DiagramaResponse(lectura.proceso(), lectura.compartido(),
                poolMapper.toResponses(poolRepository
                        .findAllByProcesoIdAndEmpresaIdOrderByOrdenAsc(procesoId, duena)),
                laneMapper.toResponses(laneRepository.delProcesoEnOrden(procesoId, duena)),
                actividadMapper.toResponses(actividadRepository
                        .findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, duena)),
                gatewayMapper.toResponses(gatewayRepository
                        .findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, duena)),
                arcoMapper.toResponses(arcoRepository
                        .findAllByPool_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, duena)),
                mensajeMapper.toResponses(mensajeRepository
                        .findAllByProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, duena)),
                correlacionMapper.toResponses(correlacionRepository
                        .findAllByMensaje_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, duena)));
    }
}
