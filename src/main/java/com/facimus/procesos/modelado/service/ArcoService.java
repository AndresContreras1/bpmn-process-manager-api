package com.facimus.procesos.modelado.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.NodoFlujo;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;

import lombok.RequiredArgsConstructor;

/** HU-11 a HU-13: arcos (flujo entre actividades y gateways dentro de un mismo pool). */
@Service
@RequiredArgsConstructor
public class ArcoService {

    private final ArcoRepository arcoRepository;
    private final NodoFlujoRepository nodoFlujoRepository;
    private final PoolRepository poolRepository;

    @Transactional
    public Arco crear(Long empresaId, Long origenId, Long destinoId, String etiqueta, String condicion) {
        if (origenId.equals(destinoId)) {
            throw new ReglaNegocioException("Un arco no puede tener el mismo nodo como origen y destino.");
        }
        NodoFlujo origen = nodoFlujoRepository.findByIdAndEmpresaId(origenId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Nodo de origen no encontrado."));
        NodoFlujo destino = nodoFlujoRepository.findByIdAndEmpresaId(destinoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Nodo de destino no encontrado."));

        Pool poolOrigen = origen.getLane().getPool();
        Pool poolDestino = destino.getLane().getPool();
        if (!poolOrigen.getId().equals(poolDestino.getId())) {
            throw new ReglaNegocioException("El origen y el destino de un arco deben pertenecer al mismo pool.");
        }
        if (arcoRepository.existsByOrigenIdAndDestinoIdAndEmpresaId(origenId, destinoId, empresaId)) {
            throw new ReglaNegocioException("Ya existe un arco entre estos dos nodos.");
        }
        if (destino instanceof Gateway gatewayDestino
                && (gatewayDestino.getTipoGateway() == TipoGateway.EXCLUSIVO
                        || gatewayDestino.getTipoGateway() == TipoGateway.INCLUSIVO)
                && !StringUtils.hasText(condicion)) {
            throw new ReglaNegocioException("Un arco hacia un gateway exclusivo o inclusivo requiere condicion.");
        }

        Arco arco = new Arco();
        arco.setEmpresa(origen.getEmpresa());
        arco.setOrigen(origen);
        arco.setDestino(destino);
        arco.setPool(poolOrigen);
        arco.setEtiqueta(etiqueta);
        arco.setCondicion(condicion);
        return arcoRepository.save(arco);
    }

    @Transactional
    public Arco editar(Long empresaId, Long arcoId, String etiqueta, String condicion) {
        Arco arco = obtener(empresaId, arcoId);
        arco.setEtiqueta(etiqueta);
        arco.setCondicion(condicion);
        return arcoRepository.save(arco);
    }

    @Transactional
    public void eliminar(Long empresaId, Long arcoId) {
        arcoRepository.delete(obtener(empresaId, arcoId));
    }

    public Arco obtener(Long empresaId, Long arcoId) {
        return arcoRepository.findByIdAndEmpresaId(arcoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Arco no encontrado."));
    }

    public List<Arco> listarPorPool(Long empresaId, Long poolId) {
        if (!poolRepository.existsByIdAndEmpresaId(poolId, empresaId)) {
            throw new RecursoNoEncontradoException("Pool no encontrado.");
        }
        return arcoRepository.findAllByPoolIdAndEmpresaId(poolId, empresaId);
    }
}
