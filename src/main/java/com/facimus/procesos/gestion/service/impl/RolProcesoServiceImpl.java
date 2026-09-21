package com.facimus.procesos.gestion.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.dto.response.RolProcesoVistaResponse;
import com.facimus.procesos.gestion.mapper.RolProcesoMapper;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.RolProcesoRepository;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.repository.LaneRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RolProcesoServiceImpl implements RolProcesoService {

    private final RolProcesoRepository rolProcesoRepository;
    private final EmpresaRepository empresaRepository;
    private final LaneRepository laneRepository;
    private final RolProcesoMapper rolProcesoMapper;

    @Override
    public List<RolProcesoVistaResponse> listarConUso(Long empresaId) {
        return rolProcesoRepository.findAllByEmpresaIdAndActivoTrue(empresaId).stream()
                .map(rol -> conUso(empresaId, rol))
                .toList();
    }

    @Override
    @Transactional
    public RolProcesoVistaResponse crear(Long empresaId, String nombre, String descripcion) {
        if (rolProcesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(empresaId, nombre)) {
            throw nombreRepetido(nombre);
        }
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));

        RolProceso rol = rolProcesoRepository.save(RolProceso.builder()
                .empresa(empresa)
                .nombre(nombre)
                .descripcion(descripcion)
                .build());
        return rolProcesoMapper.toResponse(rol, 0);
    }

    @Override
    public RolProcesoVistaResponse obtener(Long empresaId, Long rolId) {
        return conUso(empresaId, buscarActivo(empresaId, rolId));
    }

    @Override
    @Transactional
    public RolProcesoVistaResponse editar(Long empresaId, Long rolId, String nombre, String descripcion) {
        RolProceso rol = buscarActivo(empresaId, rolId);
        if (rolProcesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrueAndIdNot(empresaId, nombre, rolId)) {
            throw nombreRepetido(nombre);
        }
        rol.setNombre(nombre);
        rol.setDescripcion(descripcion);
        return conUso(empresaId, rolProcesoRepository.save(rol));
    }

    @Override
    @Transactional
    public void eliminar(Long empresaId, Long rolId) {
        RolProceso rol = buscarActivo(empresaId, rolId);
        List<Lane> lanesQueLoUsan = laneRepository.findAllByRolProcesoIdAndEmpresaId(rolId, empresaId);
        if (!lanesQueLoUsan.isEmpty()) {
            String procesos = lanesQueLoUsan.stream()
                    .map(lane -> lane.getPool().getProceso().getNombre())
                    .distinct()
                    .collect(Collectors.joining(", "));
            throw new ReglaNegocioException(
                    "El rol \"" + rol.getNombre() + "\" esta en uso en los procesos: " + procesos
                            + ". No se puede eliminar.");
        }
        rol.setActivo(false);
        rolProcesoRepository.save(rol);
    }

    private RolProcesoVistaResponse conUso(Long empresaId, RolProceso rol) {
        return rolProcesoMapper.toResponse(rol, laneRepository.countByRolProcesoIdAndEmpresaId(rol.getId(), empresaId));
    }

    private RolProceso buscarActivo(Long empresaId, Long rolId) {
        return rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(rolId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Rol de proceso no encontrado."));
    }

    private static ReglaNegocioException nombreRepetido(String nombre) {
        return new ReglaNegocioException("Ya existe un rol de proceso con el nombre \"" + nombre + "\" en esta empresa.");
    }
}
