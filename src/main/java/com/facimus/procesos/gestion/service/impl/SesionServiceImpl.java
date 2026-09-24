package com.facimus.procesos.gestion.service.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.SesionInvalidaException;
import com.facimus.procesos.gestion.dto.response.SesionIniciada;
import com.facimus.procesos.gestion.event.SesionesCerradas;
import com.facimus.procesos.gestion.mapper.UsuarioMapper;
import com.facimus.procesos.gestion.model.RefreshToken;
import com.facimus.procesos.gestion.model.Sesion;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.RefreshTokenRepository;
import com.facimus.procesos.gestion.repository.SesionRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.SesionService;

@Service
@Transactional(readOnly = true)
public class SesionServiceImpl implements SesionService {

    private static final Logger log = LoggerFactory.getLogger(SesionServiceImpl.class);

    /** 256 bits aleatorios: adivinar un refresh token no es viable. */
    private static final int BYTES_DEL_TOKEN = 32;

    private final SesionRepository sesionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioMapper usuarioMapper;
    private final ApplicationEventPublisher eventos;
    private final Duration vigenciaRefresh;
    private final SecureRandom aleatorio = new SecureRandom();

    public SesionServiceImpl(SesionRepository sesionRepository, RefreshTokenRepository refreshTokenRepository,
            UsuarioRepository usuarioRepository, UsuarioMapper usuarioMapper, ApplicationEventPublisher eventos,
            @Value("${jwt.refresh-expiration-seconds}") long vigenciaRefreshSegundos) {
        this.sesionRepository = sesionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioMapper = usuarioMapper;
        this.eventos = eventos;
        this.vigenciaRefresh = Duration.ofSeconds(vigenciaRefreshSegundos);
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
        return new SesionIniciada(emitir(sesion, ahora), sesion.getCodigo(), usuarioMapper.toResponse(usuario));
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
        if (refreshTokenRepository.marcarUsado(token.getId(), ahora) == 0) {
            // Ya se habia usado, asi que circula una copia: se cierra la sesion para quien la tenga y para el dueno.
            log.warn("Refresh token reutilizado: se cierra la sesion {} del usuario {}.", sesion.getCodigo(),
                    usuario.getId());
            cerrarSesiones(List.of(sesion), ahora);
            throw new SesionInvalidaException();
        }
        return new SesionIniciada(emitir(sesion, ahora), sesion.getCodigo(), usuarioMapper.toResponse(usuario));
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
    public void cerrarConToken(Long empresaId, Long usuarioId, String refreshToken) {
        refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .map(RefreshToken::getSesion)
                .filter(sesion -> sesion.getUsuario().getId().equals(usuarioId)
                        && sesion.getEmpresa().getId().equals(empresaId))
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

    private String emitir(Sesion sesion, LocalDateTime ahora) {
        byte[] bytes = new byte[BYTES_DEL_TOKEN];
        aleatorio.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepository.save(RefreshToken.builder()
                .empresa(sesion.getEmpresa())
                .sesion(sesion)
                .tokenHash(hash(token))
                .fechaEmision(ahora)
                .fechaExpiracion(ahora.plus(vigenciaRefresh))
                .build());
        return token;
    }

    /** SHA-256 y no BCrypt: el token ya es aleatorio, y el hash tiene que servir para buscarlo en la base. */
    private static String hash(String token) {
        return Huella.de(token);
    }
}
