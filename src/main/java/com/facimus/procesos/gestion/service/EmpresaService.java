package com.facimus.procesos.gestion.service;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/** HU-01: registro de empresa + usuario administrador inicial. */
@Service
@RequiredArgsConstructor
public class EmpresaService {

    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Empresa registrar(String nombre, String nit, String correoContacto,
            String nombreAdmin, String emailAdmin, String passwordAdmin) {
        if (empresaRepository.existsByNit(nit)) {
            throw new ReglaNegocioException("Ya existe una empresa registrada con el NIT " + nit + ".");
        }

        Empresa empresa = new Empresa();
        empresa.setNombre(nombre);
        empresa.setNit(nit);
        empresa.setCorreoContacto(correoContacto);
        empresa.setFechaRegistro(LocalDate.now());
        empresa = empresaRepository.save(empresa);

        Usuario admin = new Usuario();
        admin.setEmpresa(empresa);
        admin.setNombre(nombreAdmin);
        admin.setEmail(emailAdmin);
        admin.setPasswordHash(passwordEncoder.encode(passwordAdmin));
        admin.setRolAcceso(RolAcceso.ADMINISTRADOR);
        admin.setActivo(true);
        usuarioRepository.save(admin);

        return empresa;
    }

    public Optional<Empresa> buscarPorNit(String nit) {
        return empresaRepository.findByNit(nit);
    }
}
