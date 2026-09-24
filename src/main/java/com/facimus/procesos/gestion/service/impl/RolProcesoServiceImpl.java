package com.facimus.procesos.gestion.service.impl;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.dto.response.RolProcesoVistaResponse;
import com.facimus.procesos.gestion.mapper.RolProcesoMapper;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.RolProcesoRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.UsoDeRoles;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RolProcesoServiceImpl implements RolProcesoService {

    private final RolProcesoRepository rolProcesoRepository;
    private final EmpresaRepository empresaRepository;
    private final UsoDeRoles usoDeRoles;
    private final HistorialCambioService historialCambioService;
    private final RolProcesoMapper rolProcesoMapper;

    @Override
    public PageResponse<RolProcesoVistaResponse> buscar(Long empresaId, String nombre, Pageable pageable) {
        Page<RolProceso> roles = StringUtils.hasText(nombre)
                ? rolProcesoRepository.findAllByEmpresaIdAndActivoTrueAndNombreContainingIgnoreCase(empresaId, nombre,
                        pageable)
                : rolProcesoRepository.findAllByEmpresaIdAndActivoTrue(empresaId, pageable);
        // El uso de todos los roles de la pagina en una sola consulta, no una por rol.
        Map<Long, Long> usos = usoDeRoles.contarProcesosPorRol(empresaId, roles.map(RolProceso::getId).getContent());
        return PageResponse.from(roles.map(rol -> rolProcesoMapper.toResponse(rol, usos.getOrDefault(rol.getId(), 0L))));
    }

    @Override
    @Transactional
    public RolProcesoVistaResponse crear(Long empresaId, Long usuarioId, String nombre, String descripcion) {
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
        historialCambioService.registrarDeTienda(empresaId, usuarioId, RecursoDeHistorial.ROL, rol.getId(),
                "Rol de proceso \"" + nombre + "\" creado.");
        return rolProcesoMapper.toResponse(rol, 0);
    }

    @Override
    public RolProcesoVistaResponse obtener(Long empresaId, Long rolId) {
        return conUso(empresaId, buscarActivo(empresaId, rolId));
    }

    @Override
    @Transactional
    public RolProcesoVistaResponse editar(Long empresaId, Long usuarioId, Long rolId, String nombre,
            String descripcion, Long version) {
        RolProceso rol = buscarActivo(empresaId, rolId);
        rol.verificarVersion(version);
        if (rolProcesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrueAndIdNot(empresaId, nombre, rolId)) {
            throw nombreRepetido(nombre);
        }
        String anterior = rol.getNombre();
        rol.setNombre(nombre);
        rol.setDescripcion(descripcion);
        historialCambioService.registrarDeTienda(empresaId, usuarioId, RecursoDeHistorial.ROL, rolId,
                anterior.equals(nombre) ? "Rol de proceso \"" + nombre + "\" editado."
                        : "Rol de proceso \"" + anterior + "\" renombrado a \"" + nombre + "\".");
        return conUso(empresaId, rolProcesoRepository.saveAndFlush(rol));
    }

    @Override
    @Transactional
    public void eliminar(Long empresaId, Long usuarioId, Long rolId) {
        RolProceso rol = buscarActivo(empresaId, rolId);
        List<String> procesos = usoDeRoles.procesosQueLoUsan(empresaId, rolId);
        if (!procesos.isEmpty()) {
            throw new ReglaNegocioException(
                    "El rol \"" + rol.getNombre() + "\" esta en uso en los procesos: " + String.join(", ", procesos)
                            + ". No se puede eliminar.");
        }
        rol.setActivo(false);
        rolProcesoRepository.save(rol);
        historialCambioService.registrarDeTienda(empresaId, usuarioId, RecursoDeHistorial.ROL, rolId,
                "Rol de proceso \"" + rol.getNombre() + "\" eliminado.");
    }

    private RolProcesoVistaResponse conUso(Long empresaId, RolProceso rol) {
        return rolProcesoMapper.toResponse(rol, usoDeRoles.contarProcesos(empresaId, rol.getId()));
    }

    private RolProceso buscarActivo(Long empresaId, Long rolId) {
        return rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(rolId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Rol de proceso no encontrado."));
    }

    private static ReglaNegocioException nombreRepetido(String nombre) {
        return new ReglaNegocioException("Ya existe un rol de proceso con el nombre \"" + nombre + "\" en esta empresa.");
    }
}
