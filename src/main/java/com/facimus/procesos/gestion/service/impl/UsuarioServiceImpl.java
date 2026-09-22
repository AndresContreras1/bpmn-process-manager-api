package com.facimus.procesos.gestion.service.impl;

import java.util.Locale;

import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.mapper.UsuarioMapper;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.UsuarioService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsuarioServiceImpl implements UsuarioService {

    private static final String CREDENCIALES_INVALIDAS = "Correo o contrasena incorrectos.";

    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioMapper usuarioMapper;

    @Override
    @Transactional
    public UsuarioResponse crearColaborador(Long empresaId, String nombre, String email, String password,
            RolAcceso rolAcceso) {
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        String correo = normalizarCorreo(email);
        validarCorreoDisponible(correo);

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .empresa(empresa)
                .nombre(nombre)
                .email(correo)
                .passwordHash(passwordEncoder.encode(password))
                .rolAcceso(rolAcceso)
                .build());
        return usuarioMapper.toResponse(usuario);
    }

    @Override
    public void validarCorreoDisponible(String email) {
        if (usuarioRepository.existsByEmail(normalizarCorreo(email))) {
            throw new ReglaNegocioException("Ya existe un usuario registrado con el correo " + email + ".");
        }
    }

    @Override
    @Transactional
    public UsuarioResponse actualizar(Long empresaId, Long usuarioId, RolAcceso rolAcceso, Boolean activo) {
        Usuario usuario = buscar(empresaId, usuarioId);
        if (rolAcceso != null) {
            usuario.setRolAcceso(rolAcceso);
        }
        if (activo != null) {
            usuario.setActivo(activo);
        }
        return usuarioMapper.toResponse(usuarioRepository.save(usuario));
    }

    @Override
    @Transactional
    public void desactivar(Long empresaId, Long usuarioId) {
        Usuario usuario = buscar(empresaId, usuarioId);
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
    }

    @Override
    public UsuarioResponse autenticar(String email, String password) {
        Usuario usuario = usuarioRepository.findByEmail(normalizarCorreo(email))
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new ReglaNegocioException(CREDENCIALES_INVALIDAS));
        if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw new ReglaNegocioException(CREDENCIALES_INVALIDAS);
        }
        return usuarioMapper.toResponse(usuario);
    }

    @Override
    public PageResponse<UsuarioResponse> buscar(Long empresaId, Pageable pageable) {
        return PageResponse.from(usuarioRepository.findAllByEmpresaIdAndActivoTrue(empresaId, pageable)
                .map(usuarioMapper::toResponse));
    }

    @Override
    public UsuarioResponse obtener(Long empresaId, Long usuarioId) {
        return usuarioMapper.toResponse(buscar(empresaId, usuarioId));
    }

    private Usuario buscar(Long empresaId, Long usuarioId) {
        return usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
    }

    /** Ana@Acme.com y ana@acme.com son el mismo usuario: se guarda y se busca siempre en minusculas. */
    private static String normalizarCorreo(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
