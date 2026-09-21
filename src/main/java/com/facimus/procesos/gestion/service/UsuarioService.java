package com.facimus.procesos.gestion.service;

import java.util.List;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/** HU-02: alta y administracion de colaboradores. HU-03: inicio de sesion. */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private static final String CREDENCIALES_INVALIDAS = "Correo o contrasena incorrectos.";

    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Usuario crearColaborador(Long empresaId, String nombre, String email, String password,
            RolAcceso rolAcceso) {
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        return crearUsuario(empresa, nombre, email, password, rolAcceso);
    }

    /**
     * Unico punto que da de alta usuarios, tanto colaboradores como el administrador de una empresa nueva.
     * El correo es el usuario del login, que todavia no conoce la empresa: tiene que ser unico en todo el sistema.
     */
    @Transactional
    public Usuario crearUsuario(Empresa empresa, String nombre, String email, String password, RolAcceso rolAcceso) {
        String correo = normalizarCorreo(email);
        validarCorreoDisponible(correo);

        Usuario usuario = new Usuario();
        usuario.setEmpresa(empresa);
        usuario.setNombre(nombre);
        usuario.setEmail(correo);
        usuario.setPasswordHash(passwordEncoder.encode(password));
        usuario.setRolAcceso(rolAcceso);
        usuario.setActivo(true);
        return usuarioRepository.save(usuario);
    }

    public void validarCorreoDisponible(String email) {
        if (usuarioRepository.existsByEmail(normalizarCorreo(email))) {
            throw new ReglaNegocioException("Ya existe un usuario registrado con el correo " + email + ".");
        }
    }

    @Transactional
    public Usuario cambiarRolAcceso(Long empresaId, Long usuarioId, RolAcceso nuevoRol) {
        return actualizar(empresaId, usuarioId, nuevoRol, null);
    }

    @Transactional
    public Usuario actualizar(Long empresaId, Long usuarioId, RolAcceso rolAcceso, Boolean activo) {
        Usuario usuario = obtener(empresaId, usuarioId);
        if (rolAcceso != null) {
            usuario.setRolAcceso(rolAcceso);
        }
        if (activo != null) {
            usuario.setActivo(activo);
        }
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public void desactivar(Long empresaId, Long usuarioId) {
        Usuario usuario = obtener(empresaId, usuarioId);
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
    }

    public Usuario autenticar(String email, String password) {
        Usuario usuario = usuarioRepository.findByEmail(normalizarCorreo(email))
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new ReglaNegocioException(CREDENCIALES_INVALIDAS));
        if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw new ReglaNegocioException(CREDENCIALES_INVALIDAS);
        }
        return usuario;
    }

    public List<Usuario> listarPorEmpresa(Long empresaId) {
        return usuarioRepository.findAllByEmpresaIdAndActivoTrue(empresaId);
    }

    public Usuario obtener(Long empresaId, Long usuarioId) {
        return usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
    }

    /** Ana@Acme.com y ana@acme.com son el mismo usuario: se guarda y se busca siempre en minusculas. */
    private static String normalizarCorreo(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
