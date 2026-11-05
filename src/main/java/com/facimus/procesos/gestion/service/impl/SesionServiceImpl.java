package com.facimus.procesos.gestion.service.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.SesionInvalidaException;
import com.facimus.procesos.gestion.dto.response.SesionIniciada;
import com.facimus.procesos.gestion.event.SesionesCerradas;
import com.facimus.procesos.gestion.mapper.UsuarioMapper;
import com.facimus.procesos.gestion.model.ConfiguracionTienda;
import com.facimus.procesos.gestion.model.RefreshToken;
import com.facimus.procesos.gestion.model.Sesion;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ConfiguracionTiendaRepository;
import com.facimus.procesos.gestion.repository.RefreshTokenRepository;
import com.facimus.procesos.gestion.repository.SesionRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.SesionService;

/**
 * Las sesiones de la API, con los limites de NIST SP 800-63B-4 para AAL2 que cada tienda ajusta: cada refresh token
 * vence si no se renueva en la inactividad de la tienda, y ninguno pasa del fin de la sesion, a las horas de su
 * duracion desde el login. Ahi se vuelve a entrar con la clave.
 */
@Service
@Transactional(readOnly = true)
public class SesionServiceImpl implements SesionService {

    private static final Logger log = LoggerFactory.getLogger(SesionServiceImpl.class);

    /** 256 bits aleatorios: adivinar un refresh token no es viable. */
    private static final int BYTES_DEL_TOKEN = 32;

    /** Los de una tienda sin configuracion, que no deberia existir: los topes de AAL2. */
    private static final Limites TOPES = new Limites(Duration.ofHours(1), Duration.ofHours(24));

    private final SesionRepository sesionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioMapper usuarioMapper;
    private final ApplicationEventPublisher eventos;
    private final ConfiguracionTiendaRepository configuracionRepository;
    private final SecureRandom aleatorio = new SecureRandom();

    public SesionServiceImpl(SesionRepository sesionRepository, RefreshTokenRepository refreshTokenRepository,
            UsuarioRepository usuarioRepository, UsuarioMapper usuarioMapper, ApplicationEventPublisher eventos,
            ConfiguracionTiendaRepository configuracionRepository) {
        this.sesionRepository = sesionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioMapper = usuarioMapper;
        this.eventos = eventos;
        this.configuracionRepository = configuracionRepository;
    }

    @Override
    @Transactional
    public SesionIniciada iniciar(Long empresaId, Long usuarioId) {
        Usuario usuario = usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
        LocalDateTime ahora = LocalDateTime.now();
        Sesion sesion = sesionRepository.save(Sesion.builder()
                .empresa(usuario.getEmpresa())
                .usuario(usuario)
                .codigo(UUID.randomUUID().toString())
                .fechaInicio(ahora)
                .build());
        return emitir(sesion, usuario, limites(empresaId), ahora);
    }

    /** Sin rollback al rechazar: el cierre por un token reutilizado tiene que quedar guardado aunque responda 401. */
    @Override
    @Transactional(noRollbackFor = SesionInvalidaException.class)
    public SesionIniciada renovar(String refreshToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .orElseThrow(SesionInvalidaException::new);
        Sesion sesion = token.getSesion();
        Usuario usuario = sesion.getUsuario();
        LocalDateTime ahora = LocalDateTime.now();
        if (!sesion.estaAbierta() || !usuario.isActivo() || !token.getFechaExpiracion().isAfter(ahora)) {
            throw new SesionInvalidaException();
        }
        Limites limites = limites(sesion.getEmpresa().getId());
        if (!limites.fin(sesion).isAfter(ahora)) {
            // Llego al fin que la tienda pone, quizas uno que acorto despues del login: se vuelve a entrar.
            cerrarSesiones(List.of(sesion), ahora);
            throw new SesionInvalidaException();
        }
        if (refreshTokenRepository.marcarUsado(token.getId(), ahora) == 0) {
            // Ya se habia usado, asi que circula una copia: se cierra la sesion para quien la tenga y para el dueno.
            log.warn("Refresh token reutilizado: se cierra la sesion {} del usuario {}.", sesion.getCodigo(),
                    usuario.getId());
            cerrarSesiones(List.of(sesion), ahora);
            throw new SesionInvalidaException();
        }
        return emitir(sesion, usuario, limites, ahora);
    }

    @Override
    @Transactional
    public void cerrar(Long empresaId, Long usuarioId, String codigo) {
        sesionRepository.findByCodigoAndUsuarioIdAndEmpresaId(codigo, usuarioId, empresaId)
                .filter(Sesion::estaAbierta)
                .ifPresent(sesion -> sesion.setFechaCierre(LocalDateTime.now()));
        // Aunque la base ya no la tenga abierta, el access token que la nombra deja de servir desde ahora.
        eventos.publishEvent(new SesionesCerradas(List.of(codigo)));
    }

    @Override
    @Transactional
    public void cerrarConToken(String refreshToken) {
        refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .map(RefreshToken::getSesion)
                .ifPresent(sesion -> cerrarSesiones(List.of(sesion), LocalDateTime.now()));
    }

    @Override
    @Transactional
    public void cerrarTodas(Long empresaId, Long usuarioId) {
        cerrarSesiones(sesionRepository.findAllByUsuarioIdAndEmpresaIdAndFechaCierreIsNull(usuarioId, empresaId),
                LocalDateTime.now());
    }

    @Override
    public List<String> cerradasEnLosUltimos(Duration ventana) {
        return sesionRepository.codigosCerradosDesde(LocalDateTime.now().minus(ventana));
    }

    private void cerrarSesiones(List<Sesion> sesiones, LocalDateTime ahora) {
        List<Sesion> abiertas = sesiones.stream().filter(Sesion::estaAbierta).toList();
        if (abiertas.isEmpty()) {
            return;
        }
        abiertas.forEach(sesion -> sesion.setFechaCierre(ahora));
        eventos.publishEvent(new SesionesCerradas(abiertas.stream().map(Sesion::getCodigo).toList()));
    }

    /** Un refresh token nuevo de la sesion: vence tras la inactividad de la tienda, y nunca despues del fin. */
    private SesionIniciada emitir(Sesion sesion, Usuario usuario, Limites limites, LocalDateTime ahora) {
        LocalDateTime fin = limites.fin(sesion);
        LocalDateTime inactiva = ahora.plus(limites.inactividad());
        LocalDateTime vence = inactiva.isBefore(fin) ? inactiva : fin;
        byte[] bytes = new byte[BYTES_DEL_TOKEN];
        aleatorio.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepository.save(RefreshToken.builder()
                .empresa(sesion.getEmpresa())
                .sesion(sesion)
                .tokenHash(hash(token))
                .fechaEmision(ahora)
                .fechaExpiracion(vence)
                .build());
        return new SesionIniciada(token, sesion.getCodigo(), usuarioMapper.toResponse(usuario),
                Duration.between(ahora, vence), Duration.between(ahora, fin));
    }

    /** Los de hoy: una sesion abierta toma la duracion nueva de la tienda en su proxima renovacion. */
    private Limites limites(Long empresaId) {
        return configuracionRepository.findByEmpresaId(empresaId)
                .map(Limites::de)
                .orElse(TOPES);
    }

    /** Cuanto aguanta una sesion sin renovarse, y cuanto dura desde el login. */
    private record Limites(Duration inactividad, Duration duracion) {

        static Limites de(ConfiguracionTienda configuracion) {
            return new Limites(Duration.ofMinutes(configuracion.getInactividadSesionMinutos()),
                    Duration.ofHours(configuracion.getDuracionSesionHoras()));
        }

        LocalDateTime fin(Sesion sesion) {
            return sesion.getFechaInicio().plus(duracion);
        }
    }

    /** SHA-256 y no BCrypt: el token ya es aleatorio, y el hash tiene que servir para buscarlo en la base. */
    private static String hash(String token) {
        return Huella.de(token);
    }
}
