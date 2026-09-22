package com.facimus.procesos.modelado.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.gestion.service.UsoDeRoles;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.ProcesosDelRol;

import lombok.RequiredArgsConstructor;

/** Un rol de proceso se usa cuando una lane de un proceso activo lo tiene asignado; los eliminados ya no cuentan. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsoDeRolesEnLanes implements UsoDeRoles {

    private final LaneRepository laneRepository;

    @Override
    public long contarProcesos(Long empresaId, Long rolId) {
        return laneRepository.contarProcesosActivosDelRol(empresaId, rolId);
    }

    @Override
    public List<String> procesosQueLoUsan(Long empresaId, Long rolId) {
        return laneRepository.nombresDeProcesosActivosDelRol(empresaId, rolId);
    }

    @Override
    public Map<Long, Long> contarProcesosPorRol(Long empresaId, Collection<Long> rolIds) {
        if (rolIds.isEmpty()) {
            return Map.of();
        }
        return laneRepository.contarProcesosActivosPorRol(empresaId, rolIds).stream()
                .collect(Collectors.toMap(ProcesosDelRol::rolId, ProcesosDelRol::procesos));
    }
}
