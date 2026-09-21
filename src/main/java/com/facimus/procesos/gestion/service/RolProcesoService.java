package com.facimus.procesos.gestion.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.RolProcesoRepository;
import com.facimus.procesos.gestion.service.dto.RolProcesoVista;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.repository.LaneRepository;

import lombok.RequiredArgsConstructor;

/** HU-17 a HU-20: roles de proceso (funciones, no personas, asignables a Lanes). */
@Service
@RequiredArgsConstructor
public class RolProcesoService {

    private final RolProcesoRepository rolProcesoRepository;
    private final EmpresaRepository empresaRepository;
    private final LaneRepository laneRepository;

    @Transactional
    public RolProceso crear(Long empresaId, String nombre, String descripcion) {
        validarNombreUnico(empresaId, nombre, null);
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));

        RolProceso rol = new RolProceso();
        rol.setEmpresa(empresa);
        rol.setNombre(nombre);
        rol.setDescripcion(descripcion);
        rol.setActivo(true);
        return rolProcesoRepository.save(rol);
    }

    @Transactional
    public RolProceso editar(Long empresaId, Long rolId, String nombre, String descripcion) {
        RolProceso rol = obtener(empresaId, rolId);
        validarNombreUnico(empresaId, nombre, rolId);
        rol.setNombre(nombre);
        rol.setDescripcion(descripcion);
        return rolProcesoRepository.save(rol);
    }

    @Transactional
    public void eliminar(Long empresaId, Long rolId) {
        RolProceso rol = obtener(empresaId, rolId);
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

    public List<RolProcesoVista> listarConUso(Long empresaId) {
        return rolProcesoRepository.findAllByEmpresaIdAndActivoTrue(empresaId).stream()
                .map(rol -> {
                    long usos = laneRepository.countByRolProcesoIdAndEmpresaId(rol.getId(), empresaId);
                    return new RolProcesoVista(rol, usos, usos > 0);
                })
                .toList();
    }

    public long contarUsos(Long empresaId, Long rolId) {
        obtener(empresaId, rolId);
        return laneRepository.countByRolProcesoIdAndEmpresaId(rolId, empresaId);
    }

    public RolProceso obtener(Long empresaId, Long rolId) {
        return rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(rolId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Rol de proceso no encontrado."));
    }

    private void validarNombreUnico(Long empresaId, String nombre, Long rolIdActual) {
        boolean existe = rolProcesoRepository.findAllByEmpresaIdAndActivoTrue(empresaId).stream()
                .anyMatch(r -> r.getNombre().equalsIgnoreCase(nombre) && !r.getId().equals(rolIdActual));
        if (existe) {
            throw new ReglaNegocioException("Ya existe un rol de proceso con el nombre \"" + nombre + "\" en esta empresa.");
        }
    }
}
