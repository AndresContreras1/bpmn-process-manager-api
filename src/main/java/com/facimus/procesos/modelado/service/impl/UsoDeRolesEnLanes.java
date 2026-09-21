package com.facimus.procesos.modelado.service.impl;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.gestion.service.UsoDeRoles;
import com.facimus.procesos.modelado.repository.LaneRepository;

import lombok.RequiredArgsConstructor;

/** Un rol de proceso se usa cuando una lane lo tiene asignado. */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsoDeRolesEnLanes implements UsoDeRoles {

    private final LaneRepository laneRepository;

    @Override
    public long contarUsos(Long empresaId, Long rolId) {
        return laneRepository.countByRolProcesoIdAndEmpresaId(rolId, empresaId);
    }

    @Override
    public List<String> procesosQueLoUsan(Long empresaId, Long rolId) {
        return laneRepository.findAllByRolProcesoIdAndEmpresaId(rolId, empresaId).stream()
                .map(lane -> lane.getPool().getProceso().getNombre())
                .distinct()
                .toList();
    }
}
