package com.facimus.procesos.modelado.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.modelado.dto.response.PoolResponse;
import com.facimus.procesos.modelado.mapper.PoolMapper;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;
import com.facimus.procesos.modelado.service.PoolService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PoolServiceImpl implements PoolService {

    private final PoolRepository poolRepository;
    private final ProcesoRepository procesoRepository;
    private final LaneRepository laneRepository;
    private final NodoFlujoRepository nodoFlujoRepository;
    private final PoolMapper poolMapper;

    @Override
    @Transactional
    public PoolResponse crear(Long empresaId, Long procesoId, String nombre, TipoParticipante tipoParticipante,
            boolean cajaNegra) {
        Proceso proceso = procesoRepository.findByIdAndEmpresaId(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
        int orden = poolRepository.findAllByProcesoIdAndEmpresaIdOrderByOrdenAsc(procesoId, empresaId).size();

        Pool pool = poolRepository.save(Pool.builder()
                .empresa(proceso.getEmpresa())
                .proceso(proceso)
                .nombre(nombre)
                .tipoParticipante(tipoParticipante)
                .cajaNegra(cajaNegra)
                .orden(orden)
                .build());
        return poolMapper.toResponse(pool);
    }

    @Override
    @Transactional
    public PoolResponse editar(Long empresaId, Long poolId, String nombre, TipoParticipante tipoParticipante) {
        Pool pool = buscar(empresaId, poolId);
        pool.setNombre(nombre);
        pool.setTipoParticipante(tipoParticipante);
        return poolMapper.toResponse(poolRepository.save(pool));
    }

    @Override
    @Transactional
    public void eliminar(Long empresaId, Long poolId) {
        Pool pool = buscar(empresaId, poolId);
        if (nodoFlujoRepository.existsByLane_Pool_IdAndEmpresaId(poolId, empresaId)) {
            throw new ReglaNegocioException("El pool \"" + pool.getNombre() + "\" tiene lanes con actividades; no se puede eliminar.");
        }
        laneRepository.deleteAll(laneRepository.findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(poolId, empresaId));
        poolRepository.delete(pool);
    }

    @Override
    public List<PoolResponse> listarPorProceso(Long empresaId, Long procesoId) {
        if (!procesoRepository.existsByIdAndEmpresaId(procesoId, empresaId)) {
            throw new RecursoNoEncontradoException("Proceso no encontrado.");
        }
        return poolMapper.toResponses(poolRepository.findAllByProcesoIdAndEmpresaIdOrderByOrdenAsc(procesoId, empresaId));
    }

    @Override
    public PoolResponse obtener(Long empresaId, Long poolId) {
        return poolMapper.toResponse(buscar(empresaId, poolId));
    }

    private Pool buscar(Long empresaId, Long poolId) {
        return poolRepository.findByIdAndEmpresaId(poolId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pool no encontrado."));
    }
}
