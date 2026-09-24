package com.facimus.procesos.gestion.service.impl;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.EmpresaResponse;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.mapper.EmpresaMapper;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.UsuarioService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmpresaServiceImpl implements EmpresaService {

    private final EmpresaRepository empresaRepository;
    private final UsuarioService usuarioService;
    private final ConfiguracionTiendaService configuracionTiendaService;
    private final HistorialCambioService historialCambioService;
    private final EmpresaMapper empresaMapper;

    @Override
    @Transactional
    public EmpresaResponse registrar(String nombre, String nit, String correoContacto,
            String nombreAdmin, String emailAdmin, String passwordAdmin) {
        if (empresaRepository.existsByNit(nit)) {
            throw new ReglaNegocioException("Ya existe una empresa registrada con el NIT " + nit + ".");
        }
        // El correo del administrador sera su usuario de login: se valida antes de crear la empresa.
        usuarioService.validarCorreoDisponible(emailAdmin);

        Empresa empresa = empresaRepository.save(Empresa.builder()
                .nombre(nombre)
                .nit(nit)
                .correoContacto(correoContacto)
                .fechaRegistro(LocalDate.now())
                .build());

        // Sin autor: el primer administrador todavia no existe, y al crearse firma su propia alta.
        UsuarioResponse administrador = usuarioService.crearColaborador(empresa.getId(), null, nombreAdmin,
                emailAdmin, passwordAdmin, RolAcceso.ADMINISTRADOR);
        configuracionTiendaService.crearPara(empresa.getId());
        historialCambioService.registrarDeTienda(empresa.getId(), administrador.id(), RecursoDeHistorial.EMPRESA,
                empresa.getId(), "Tienda \"" + nombre + "\" registrada.");
        return empresaMapper.toResponse(empresa);
    }

    @Override
    public EmpresaResponse obtener(Long empresaId, Long id) {
        if (!empresaId.equals(id)) {
            throw new RecursoNoEncontradoException("Empresa no encontrada.");
        }
        return empresaMapper.toResponse(empresaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada.")));
    }
}
