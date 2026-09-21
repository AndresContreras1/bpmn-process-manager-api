package com.facimus.procesos.gestion.service;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.repository.EmpresaRepository;

import lombok.RequiredArgsConstructor;

/** HU-01: registro de empresa + usuario administrador inicial. */
@Service
@RequiredArgsConstructor
public class EmpresaService {

    private final EmpresaRepository empresaRepository;
    private final UsuarioService usuarioService;

    @Transactional
    public Empresa registrar(String nombre, String nit, String correoContacto,
            String nombreAdmin, String emailAdmin, String passwordAdmin) {
        if (empresaRepository.existsByNit(nit)) {
            throw new ReglaNegocioException("Ya existe una empresa registrada con el NIT " + nit + ".");
        }
        // El correo del administrador sera su usuario de login: se valida antes de crear la empresa.
        usuarioService.validarCorreoDisponible(emailAdmin);

        Empresa empresa = new Empresa();
        empresa.setNombre(nombre);
        empresa.setNit(nit);
        empresa.setCorreoContacto(correoContacto);
        empresa.setFechaRegistro(LocalDate.now());
        empresa = empresaRepository.save(empresa);

        usuarioService.crearUsuario(empresa, nombreAdmin, emailAdmin, passwordAdmin, RolAcceso.ADMINISTRADOR);
        return empresa;
    }

    public Optional<Empresa> buscarPorNit(String nit) {
        return empresaRepository.findByNit(nit);
    }
}
