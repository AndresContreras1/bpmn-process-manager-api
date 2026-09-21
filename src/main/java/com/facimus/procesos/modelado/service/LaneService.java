package com.facimus.procesos.modelado.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.repository.RolProcesoRepository;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;

import lombok.RequiredArgsConstructor;

/** HU-22 y HU-24: lanes (divisiones internas de un pool, asociadas a un rol de proceso). */
@Service
@RequiredArgsConstructor
public class LaneService {

    private final LaneRepository laneRepository;
    private final PoolRepository poolRepository;
    private final RolProcesoRepository rolProcesoRepository;
    private final NodoFlujoRepository nodoFlujoRepository;

    @Transactional
    public Lane crear(Long empresaId, Long poolId, String nombre, Long rolProcesoId) {
        Pool pool = poolRepository.findByIdAndEmpresaId(poolId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pool no encontrado."));
        RolProceso rolProceso = rolProcesoRepository.findByIdAndEmpresaId(rolProcesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Rol de proceso no encontrado."));
        int orden = laneRepository.findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(poolId, empresaId).size();

        Lane lane = new Lane();
        lane.setEmpresa(pool.getEmpresa());
        lane.setPool(pool);
        lane.setNombre(nombre);
        lane.setRolProceso(rolProceso);
        lane.setOrden(orden);
        return laneRepository.save(lane);
    }

    @Transactional
    public Lane editar(Long empresaId, Long laneId, String nombre, Long rolProcesoId) {
        Lane lane = obtener(empresaId, laneId);
        RolProceso rolProceso = rolProcesoRepository.findByIdAndEmpresaId(rolProcesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Rol de proceso no encontrado."));
        lane.setNombre(nombre);
        lane.setRolProceso(rolProceso);
        return laneRepository.save(lane);
    }

    @Transactional
    public void eliminar(Long empresaId, Long laneId) {
        Lane lane = obtener(empresaId, laneId);
        if (!nodoFlujoRepository.findAllByLaneIdAndEmpresaId(laneId, empresaId).isEmpty()) {
            throw new ReglaNegocioException("La lane \"" + lane.getNombre() + "\" contiene actividades; no se puede eliminar.");
        }
        laneRepository.delete(lane);
    }

    public List<Lane> listarPorPool(Long empresaId, Long poolId) {
        if (!poolRepository.existsByIdAndEmpresaId(poolId, empresaId)) {
            throw new RecursoNoEncontradoException("Pool no encontrado.");
        }
        return laneRepository.findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(poolId, empresaId);
    }

    public Lane obtener(Long empresaId, Long laneId) {
        return laneRepository.findByIdAndEmpresaId(laneId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Lane no encontrada."));
    }
}
