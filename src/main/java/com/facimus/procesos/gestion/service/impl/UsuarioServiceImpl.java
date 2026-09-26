package com.facimus.procesos.gestion.service.impl;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.CredencialesUsuario;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.mapper.UsuarioMapper;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.repository.UsuarioSpecifications;
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

    private static final SecureRandom ALEATORIO = new SecureRandom();

    @Override
    @Transactional
    public UsuarioResponse crearColaborador(Long empresaId, Long autorId, String nombre, String email,
            String password, RolAcceso rolAcceso) {
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        String correo = normalizarCorreo(email);
        validarCorreoDisponible(correo);

        // D17: sin contrasena, la API genera una temporal y la devuelve una sola vez.
        boolean temporal = !StringUtils.hasText(password);
        String clave = temporal ? claveTemporal() : password;

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .empresa(empresa)
                .nombre(nombre)
                .email(correo)
                .passwordHash(passwordEncoder.encode(clave))
                .rolAcceso(rolAcceso)
                .debeCambiarClave(temporal)
                .build());
        // En el registro de la tienda no hay nadie mas: el primer administrador firma su propia alta.
        historialCambioService.registrarDeTienda(empresaId, autorId == null ? usuario.getId() : autorId,
                RecursoDeHistorial.USUARIO, usuario.getId(),
                "Usuario \"" + nombre + "\" creado con rol " + rolAcceso + ".");
        UsuarioResponse respuesta = usuarioMapper.toResponse(usuario);
        return temporal ? respuesta.conClaveTemporal(clave) : respuesta;
    }

    @Override
    public void validarCorreoDisponible(String email) {
        if (usuarioRepository.existsByEmail(normalizarCorreo(email))) {
            throw new ReglaNegocioException("Ya existe un usuario registrado con el correo " + email + ".");
        }
    }

    @Override
    @Transactional
    public UsuarioResponse actualizar(Long empresaId, Long autorId, Long usuarioId, String nombre,
            RolAcceso rolAcceso, Boolean activo, Long version) {
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
        boolean cambiaElNombre = nombre != null && !nombre.equals(usuario.getNombre());
        if (cambiaElNombre) {
            usuario.setNombre(nombre);
        }
        if (rolAcceso != null) {
            usuario.setRolAcceso(rolAcceso);
        }
        if (activo != null) {
            usuario.setActivo(activo);
        }
        UsuarioResponse actualizado = usuarioMapper.toResponse(usuarioRepository.saveAndFlush(usuario));
        historialCambioService.registrarDeTienda(empresaId, autorId, RecursoDeHistorial.USUARIO, usuarioId,
                queLePaso(usuario, cambiaElNombre, cambiaElRol, desactiva));
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

    /** Lo que se anota en el historial: lo que cambio de verdad, que puede ser mas de una cosa a la vez. */
    private static String queLePaso(Usuario usuario, boolean cambiaElNombre, boolean cambiaElRol, boolean desactiva) {
        List<String> cambios = new ArrayList<>();
        if (cambiaElNombre) {
            cambios.add("renombrado");
        }
        if (cambiaElRol) {
            cambios.add("con rol " + usuario.getRolAcceso());
        }
        if (desactiva) {
            cambios.add("desactivado");
        } else if (!cambiaElNombre && !cambiaElRol) {
            cambios.add("reactivado");
        }
        return "Usuario \"" + usuario.getNombre() + "\" " + String.join(", ", cambios) + ".";
    }

    @Override
    @Transactional
    public UsuarioResponse restablecerClave(Long empresaId, Long autorId, Long usuarioId) {
        Usuario usuario = buscar(empresaId, usuarioId);
        String clave = claveTemporal();
        usuario.setPasswordHash(passwordEncoder.encode(clave));
        usuario.setDebeCambiarClave(true);
        usuarioRepository.saveAndFlush(usuario);

        historialCambioService.registrarDeTienda(empresaId, autorId, RecursoDeHistorial.USUARIO, usuarioId,
                "Contraseña de \"" + usuario.getNombre() + "\" restablecida.");
        // Quien estuviera dentro con la clave vieja deja de estarlo.
        sesionService.cerrarTodas(empresaId, usuarioId);
        return usuarioMapper.toResponse(usuario).conClaveTemporal(clave);
    }

    @Override
    @Transactional
    public UsuarioResponse cambiarClavePropia(Long empresaId, Long usuarioId, String actual, String nueva) {
        Usuario usuario = buscar(empresaId, usuarioId);
        if (!passwordEncoder.matches(actual, usuario.getPasswordHash())) {
            throw new SolicitudInvalidaException("La contraseña actual no coincide.");
        }
        if (passwordEncoder.matches(nueva, usuario.getPasswordHash())) {
            throw new SolicitudInvalidaException("La contraseña nueva tiene que ser distinta de la actual.");
        }
        usuario.setPasswordHash(passwordEncoder.encode(nueva));
        usuario.setDebeCambiarClave(false);
        usuarioRepository.saveAndFlush(usuario);

        historialCambioService.registrarDeTienda(empresaId, usuarioId, RecursoDeHistorial.USUARIO, usuarioId,
                "Usuario \"" + usuario.getNombre() + "\" cambió su contraseña.");
        return usuarioMapper.toResponse(usuario);
    }

    @Override
    public Optional<CredencialesUsuario> buscarCredenciales(String email) {
        return usuarioRepository.findByEmail(normalizarCorreo(email))
                .filter(Usuario::isActivo)
                .map(usuario -> new CredencialesUsuario(usuarioMapper.toResponse(usuario), usuario.getPasswordHash()));
    }

    @Override
    public PageResponse<UsuarioResponse> buscar(Long empresaId, String nombre, boolean incluirInactivos,
            Pageable pageable) {
        return PageResponse.from(usuarioRepository
                .findAll(UsuarioSpecifications.conFiltros(empresaId, nombre, incluirInactivos), pageable)
                .map(usuarioMapper::toResponse));
    }

    @Override
    public UsuarioResponse obtener(Long empresaId, Long usuarioId) {
        return usuarioMapper.toResponse(buscar(empresaId, usuarioId));
    }

    /**
     * Una contrasena temporal que una persona pueda leer y teclear: doce caracteres del alfabeto de URL, sacados de
     * la misma fuente aleatoria que los tokens de refresco. No se guarda en claro en ningun sitio.
     */
    private static String claveTemporal() {
        byte[] bytes = new byte[9];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
