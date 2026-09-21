package com.facimus.procesos.modelado.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;

import lombok.RequiredArgsConstructor;

/** HU-14 a HU-16: gateways (puntos de decision: exclusivo, paralelo o inclusivo). */
@Service
@RequiredArgsConstructor
public class GatewayService {

    private final NodoFlujoRepository nodoFlujoRepository;
    private final LaneRepository laneRepository;
    private final ArcoRepository arcoRepository;

    @Transactional
    public Gateway crear(Long empresaId, Long laneId, String nombre, TipoGateway tipoGateway, int posX, int posY) {
        Lane lane = laneRepository.findByIdAndEmpresaId(laneId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Lane no encontrada."));
        Long procesoId = lane.getPool().getProceso().getId();
        if (nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaId(nombre, procesoId, empresaId)) {
            throw new ReglaNegocioException("Ya existe un nodo con el nombre \"" + nombre + "\" en este proceso.");
        }

        Gateway gateway = new Gateway();
        gateway.setEmpresa(lane.getEmpresa());
        gateway.setLane(lane);
        gateway.setNombre(nombre);
        gateway.setTipoGateway(tipoGateway);
        gateway.setPosicionX(posX);
        gateway.setPosicionY(posY);
        return (Gateway) nodoFlujoRepository.save(gateway);
    }

    @Transactional
    public Gateway editar(Long empresaId, Long gatewayId, String nombre, TipoGateway tipoGateway, int posX, int posY) {
        Gateway gateway = obtener(empresaId, gatewayId);
        gateway.setNombre(nombre);
        gateway.setTipoGateway(tipoGateway);
        gateway.setPosicionX(posX);
        gateway.setPosicionY(posY);
        return (Gateway) nodoFlujoRepository.save(gateway);
    }

    @Transactional
    public void eliminar(Long empresaId, Long gatewayId) {
        Gateway gateway = obtener(empresaId, gatewayId);
        arcoRepository.deleteAll(arcoRepository.findAllByOrigenIdAndEmpresaId(gatewayId, empresaId));
        arcoRepository.deleteAll(arcoRepository.findAllByDestinoIdAndEmpresaId(gatewayId, empresaId));
        nodoFlujoRepository.delete(gateway);
    }

    public Gateway obtener(Long empresaId, Long gatewayId) {
        return nodoFlujoRepository.findByIdAndEmpresaId(gatewayId, empresaId)
                .filter(Gateway.class::isInstance)
                .map(Gateway.class::cast)
                .orElseThrow(() -> new RecursoNoEncontradoException("Gateway no encontrado."));
    }

    public List<Gateway> listarPorLane(Long empresaId, Long laneId) {
        if (!laneRepository.existsByIdAndEmpresaId(laneId, empresaId)) {
            throw new RecursoNoEncontradoException("Lane no encontrada.");
        }
        return nodoFlujoRepository.findAllByLaneIdAndEmpresaId(laneId, empresaId).stream()
                .filter(Gateway.class::isInstance)
                .map(Gateway.class::cast)
                .toList();
    }
}
