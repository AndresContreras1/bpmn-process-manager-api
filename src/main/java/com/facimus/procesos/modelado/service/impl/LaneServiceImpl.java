package com.facimus.procesos.modelado.service.impl;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.repository.RolProcesoRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.LaneResponse;
import com.facimus.procesos.modelado.mapper.LaneMapper;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;
import com.facimus.procesos.modelado.service.LaneService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LaneServiceImpl implements LaneService {

    private final HistorialCambioService historialCambioService;
    private final LaneRepository laneRepository;
    private final PoolRepository poolRepository;
    private final RolProcesoRepository rolProcesoRepository;
    private final NodoFlujoRepository nodoFlujoRepository;
    private final LaneMapper laneMapper;

    @Override
    @Transactional
    public LaneResponse crear(Long empresaId, Long usuarioId, Long poolId, String nombre, Long rolProcesoId) {
        Pool pool = poolRepository.findByIdAndEmpresaId(poolId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pool no encontrado."));
        // R-33: de una caja negra solo se ve lo que entra y lo que sale; por dentro no hay nada que repartir.
        if (pool.isCajaNegra()) {
            throw new ReglaNegocioException(ReglasDePools.CAJA_NEGRA_SIN_LANES);
        }
        RolProceso rolProceso = rolProceso(empresaId, rolProcesoId);
        int orden = laneRepository.siguienteOrden(poolId, empresaId);

        Lane lane = laneRepository.save(Lane.builder()
                .empresa(pool.getEmpresa())
                .pool(pool)
                .nombre(nombre)
                .rolProceso(rolProceso)
                .orden(orden)
                .build());
        historialCambioService.registrar(empresaId, usuarioId, pool.getProceso(),
                "Lane \"" + nombre + "\" agregada al pool \"" + pool.getNombre() + "\".");
        return laneMapper.toResponse(lane);
    }

    @Override
    @Transactional
    public LaneResponse editar(Long empresaId, Long usuarioId, Long laneId, String nombre, Long rolProcesoId,
            Long version) {
        Lane lane = buscar(empresaId, laneId);
        lane.verificarVersion(version);
        lane.setNombre(nombre);
        lane.setRolProceso(rolProceso(empresaId, rolProcesoId));
        historialCambioService.registrar(empresaId, usuarioId, lane.getPool().getProceso(),
                "Lane \"" + nombre + "\" editada.");
        return laneMapper.toResponse(laneRepository.saveAndFlush(lane));
    }

    @Override
    @Transactional
    public List<LaneResponse> reordenar(Long empresaId, Long usuarioId, Long poolId, List<Long> ids) {
        Pool pool = poolRepository.findByIdAndEmpresaId(poolId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pool no encontrado."));
        List<Lane> lanes = laneRepository.findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(poolId, empresaId);
        Map<Long, Lane> porId = ReglasDeOrden.exigirLaListaCompleta(ids, lanes, Lane::getId,
                "La lista de orden debe contener exactamente las lanes del pool.");

        for (int puesto = 0; puesto < ids.size(); puesto++) {
            porId.get(ids.get(puesto)).setOrden(puesto);
        }
        laneRepository.saveAll(lanes);
        historialCambioService.registrar(empresaId, usuarioId, pool.getProceso(),
                "Lanes del pool \"" + pool.getNombre() + "\" reordenadas.");
        return laneMapper.toResponses(ids.stream().map(porId::get).toList());
    }

    @Override
    @Transactional
    public void eliminar(Long empresaId, Long usuarioId, Long laneId) {
        Lane lane = buscar(empresaId, laneId);
        if (nodoFlujoRepository.existsByLaneIdAndEmpresaId(laneId, empresaId)) {
            throw new ReglaNegocioException("La lane \"" + lane.getNombre() + "\" contiene actividades; no se puede eliminar.");
        }
        laneRepository.delete(lane);
        historialCambioService.registrar(empresaId, usuarioId, lane.getPool().getProceso(),
                "Lane \"" + lane.getNombre() + "\" eliminada.");
    }

    @Override
    public List<LaneResponse> listarPorPool(Long empresaId, Long poolId) {
        if (!poolRepository.existsByIdAndEmpresaId(poolId, empresaId)) {
            throw new RecursoNoEncontradoException("Pool no encontrado.");
        }
        return laneMapper.toResponses(laneRepository.findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(poolId, empresaId));
    }

    @Override
    public LaneResponse obtener(Long empresaId, Long laneId) {
        return laneMapper.toResponse(buscar(empresaId, laneId));
    }

    private Lane buscar(Long empresaId, Long laneId) {
        return laneRepository.findByIdAndEmpresaId(laneId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Lane no encontrada."));
    }

    private RolProceso rolProceso(Long empresaId, Long rolProcesoId) {
        // Un rol eliminado ya no se asigna: para las lanes no existe.
        return rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(rolProcesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Rol de proceso no encontrado."));
    }
}
