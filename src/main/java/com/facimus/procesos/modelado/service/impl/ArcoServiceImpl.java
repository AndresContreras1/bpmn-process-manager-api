package com.facimus.procesos.modelado.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.modelado.dto.response.ArcoResponse;
import com.facimus.procesos.modelado.mapper.ArcoMapper;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.NodoFlujo;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;
import com.facimus.procesos.modelado.service.ArcoService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArcoServiceImpl implements ArcoService {

    private final ArcoRepository arcoRepository;
    private final NodoFlujoRepository nodoFlujoRepository;
    private final PoolRepository poolRepository;
    private final ArcoMapper arcoMapper;

    @Override
    @Transactional
    public ArcoResponse crear(Long empresaId, Long origenId, Long destinoId, String etiqueta, String condicion) {
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

        Arco arco = arcoRepository.save(Arco.builder()
                .empresa(origen.getEmpresa())
                .origen(origen)
                .destino(destino)
                .pool(poolOrigen)
                .etiqueta(etiqueta)
                .condicion(condicion)
                .build());
        return arcoMapper.toResponse(arco);
    }

    @Override
    @Transactional
    public ArcoResponse editar(Long empresaId, Long arcoId, String etiqueta, String condicion, Long version) {
        Arco arco = buscar(empresaId, arcoId);
        arco.verificarVersion(version);
        arco.setEtiqueta(etiqueta);
        arco.setCondicion(condicion);
        return arcoMapper.toResponse(arcoRepository.saveAndFlush(arco));
    }

    @Override
    @Transactional
    public void eliminar(Long empresaId, Long arcoId) {
        arcoRepository.delete(buscar(empresaId, arcoId));
    }

    @Override
    public ArcoResponse obtener(Long empresaId, Long arcoId) {
        return arcoMapper.toResponse(buscar(empresaId, arcoId));
    }

    @Override
    public List<ArcoResponse> listarPorPool(Long empresaId, Long poolId) {
        if (!poolRepository.existsByIdAndEmpresaId(poolId, empresaId)) {
            throw new RecursoNoEncontradoException("Pool no encontrado.");
        }
        return arcoMapper.toResponses(arcoRepository.findAllByPoolIdAndEmpresaId(poolId, empresaId));
    }

    private Arco buscar(Long empresaId, Long arcoId) {
        return arcoRepository.findByIdAndEmpresaId(arcoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Arco no encontrado."));
    }
}
