package com.facimus.procesos.modelado.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.GatewayResponse;
import com.facimus.procesos.modelado.mapper.GatewayMapper;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.GatewayRepository;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;
import com.facimus.procesos.modelado.service.GatewayService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GatewayServiceImpl implements GatewayService {

    private final HistorialCambioService historialCambioService;
    private final GatewayRepository gatewayRepository;
    private final NodoFlujoRepository nodoFlujoRepository;
    private final LaneRepository laneRepository;
    private final ArcoRepository arcoRepository;
    private final GatewayMapper gatewayMapper;

    @Override
    @Transactional
    public GatewayResponse crear(Long empresaId, Long usuarioId, Long laneId, String nombre, TipoGateway tipoGateway,
            int posX, int posY) {
        Lane lane = laneRepository.findByIdAndEmpresaId(laneId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Lane no encontrada."));
        Long procesoId = lane.getPool().getProceso().getId();
        if (nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaId(nombre, procesoId,
                empresaId)) {
            throw new ReglaNegocioException("Ya existe un nodo con el nombre \"" + nombre + "\" en este proceso.");
        }

        Gateway gateway = gatewayRepository.save(Gateway.builder()
                .empresa(lane.getEmpresa())
                .lane(lane)
                .nombre(nombre)
                .tipoGateway(tipoGateway)
                .posicionX(posX)
                .posicionY(posY)
                .build());
        historialCambioService.registrar(empresaId, usuarioId, lane.getPool().getProceso(),
                "Gateway \"" + nombre + "\" agregado.");
        return gatewayMapper.toResponse(gateway);
    }

    @Override
    @Transactional
    public GatewayResponse editar(Long empresaId, Long usuarioId, Long gatewayId, String nombre,
            TipoGateway tipoGateway, int posX, int posY, Long version) {
        Gateway gateway = buscar(empresaId, gatewayId);
        gateway.verificarVersion(version);
        Long procesoId = gateway.getLane().getPool().getProceso().getId();
        if (nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaIdAndIdNot(nombre, procesoId,
                empresaId, gatewayId)) {
            throw new ReglaNegocioException("Ya existe un nodo con el nombre \"" + nombre + "\" en este proceso.");
        }
        if (tipoGateway.eligePorCondicion() && tieneSalidasSinCondicion(empresaId, gatewayId)) {
            throw new ReglaNegocioException(
                    "Todos los arcos que salen de un gateway exclusivo o inclusivo requieren condicion.");
        }
        gateway.setNombre(nombre);
        gateway.setTipoGateway(tipoGateway);
        gateway.setPosicionX(posX);
        gateway.setPosicionY(posY);
        historialCambioService.registrar(empresaId, usuarioId, gateway.getLane().getPool().getProceso(),
                "Gateway \"" + nombre + "\" editado.");
        return gatewayMapper.toResponse(gatewayRepository.saveAndFlush(gateway));
    }

    @Override
    @Transactional
    public void eliminar(Long empresaId, Long usuarioId, Long gatewayId) {
        Gateway gateway = buscar(empresaId, gatewayId);
        arcoRepository.deleteAll(arcoRepository.findAllByOrigenIdAndEmpresaId(gatewayId, empresaId));
        arcoRepository.deleteAll(arcoRepository.findAllByDestinoIdAndEmpresaId(gatewayId, empresaId));
        gatewayRepository.delete(gateway);
        historialCambioService.registrar(empresaId, usuarioId, gateway.getLane().getPool().getProceso(),
                "Gateway \"" + gateway.getNombre() + "\" eliminado.");
    }

    @Override
    public GatewayResponse obtener(Long empresaId, Long gatewayId) {
        return gatewayMapper.toResponse(buscar(empresaId, gatewayId));
    }

    @Override
    public List<GatewayResponse> listarPorLane(Long empresaId, Long laneId) {
        if (!laneRepository.existsByIdAndEmpresaId(laneId, empresaId)) {
            throw new RecursoNoEncontradoException("Lane no encontrada.");
        }
        return gatewayMapper.toResponses(gatewayRepository.findAllByLaneIdAndEmpresaId(laneId, empresaId));
    }

    private boolean tieneSalidasSinCondicion(Long empresaId, Long gatewayId) {
        return arcoRepository.findAllByOrigenIdAndEmpresaId(gatewayId, empresaId).stream()
                .anyMatch(arco -> !StringUtils.hasText(arco.getCondicion()));
    }

    private Gateway buscar(Long empresaId, Long gatewayId) {
        return gatewayRepository.findByIdAndEmpresaId(gatewayId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Gateway no encontrado."));
    }
}
