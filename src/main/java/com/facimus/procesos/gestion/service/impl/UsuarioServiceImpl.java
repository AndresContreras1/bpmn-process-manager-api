package com.facimus.procesos.gestion.service.impl;

import java.util.Locale;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.dto.response.CredencialesUsuario;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.mapper.UsuarioMapper;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.SesionService;
import com.facimus.procesos.gestion.service.UsuarioService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioMapper usuarioMapper;
    private final SesionService sesionService;
    private final HistorialCambioService historialCambioService;

    @Override
    @Transactional
    public UsuarioResponse crearColaborador(Long empresaId, Long autorId, String nombre, String email,
            String password, RolAcceso rolAcceso) {
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
        // En el registro de la tienda no hay nadie mas: el primer administrador firma su propia alta.
        historialCambioService.registrarDeTienda(empresaId, autorId == null ? usuario.getId() : autorId,
                RecursoDeHistorial.USUARIO, usuario.getId(),
                "Usuario \"" + nombre + "\" creado con rol " + rolAcceso + ".");
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
    public UsuarioResponse actualizar(Long empresaId, Long autorId, Long usuarioId, RolAcceso rolAcceso,
            Boolean activo, Long version) {
        Usuario usuario = buscar(empresaId, usuarioId);
        usuario.verificarVersion(version);
        boolean desactiva = Boolean.FALSE.equals(activo);
        if (desactiva) {
            impedirQueSeDesactive(autorId, usuarioId);
        }
        if (desactiva || (rolAcceso != null && rolAcceso != RolAcceso.ADMINISTRADOR)) {
            conservarUnAdministrador(empresaId, usuario);
        }
        boolean cambiaElRol = rolAcceso != null && rolAcceso != usuario.getRolAcceso();
        if (rolAcceso != null) {
            usuario.setRolAcceso(rolAcceso);
        }
        if (activo != null) {
            usuario.setActivo(activo);
        }
        UsuarioResponse actualizado = usuarioMapper.toResponse(usuarioRepository.saveAndFlush(usuario));
        historialCambioService.registrarDeTienda(empresaId, autorId, RecursoDeHistorial.USUARIO, usuarioId,
                queLePaso(usuario, cambiaElRol, desactiva));
        if (cambiaElRol || !usuario.isActivo()) {
            // Los tokens ya emitidos llevan el rol y el estado de antes: el usuario vuelve a entrar con los nuevos.
            sesionService.cerrarTodas(empresaId, usuarioId);
        }
        return actualizado;
    }

    @Override
    @Transactional
    public void desactivar(Long empresaId, Long autorId, Long usuarioId) {
        Usuario usuario = buscar(empresaId, usuarioId);
        impedirQueSeDesactive(autorId, usuarioId);
        conservarUnAdministrador(empresaId, usuario);
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
        historialCambioService.registrarDeTienda(empresaId, autorId, RecursoDeHistorial.USUARIO, usuarioId,
                "Usuario \"" + usuario.getNombre() + "\" desactivado.");
        sesionService.cerrarTodas(empresaId, usuarioId);
    }

    /** Lo que se anota en el historial: el rol nuevo, la baja, o las dos cosas si el cambio trae las dos. */
    private static String queLePaso(Usuario usuario, boolean cambiaElRol, boolean desactiva) {
        String quien = "Usuario \"" + usuario.getNombre() + "\" ";
        if (cambiaElRol && desactiva) {
            return quien + "desactivado y con rol " + usuario.getRolAcceso() + ".";
        }
        if (cambiaElRol) {
            return quien + "con rol " + usuario.getRolAcceso() + ".";
        }
        return desactiva ? quien + "desactivado." : quien + "reactivado.";
    }

    @Override
    public Optional<CredencialesUsuario> buscarCredenciales(String email) {
        return usuarioRepository.findByEmail(normalizarCorreo(email))
                .filter(Usuario::isActivo)
                .map(usuario -> new CredencialesUsuario(usuarioMapper.toResponse(usuario), usuario.getPasswordHash()));
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

    private static void impedirQueSeDesactive(Long autorId, Long usuarioId) {
        if (usuarioId.equals(autorId)) {
            throw new ReglaNegocioException("No puede desactivar su propia cuenta.");
        }
    }

    /** Antes de que el usuario deje de ser administrador activo: si es el ultimo de la tienda, el cambio no procede. */
    private void conservarUnAdministrador(Long empresaId, Usuario usuario) {
        if (!usuario.isActivo() || usuario.getRolAcceso() != RolAcceso.ADMINISTRADOR) {
            return;
        }
        // Con la tienda bloqueada, dos administradores que se quitan el rol a la vez no cuentan al mismo tiempo: el
        // segundo espera al primero y ya ve su cambio.
        empresaRepository.bloquear(empresaId);
        if (usuarioRepository.countByEmpresaIdAndRolAccesoAndActivoTrue(empresaId, RolAcceso.ADMINISTRADOR) <= 1) {
            throw new ReglaNegocioException("La tienda tiene que conservar al menos un administrador activo.");
        }
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
