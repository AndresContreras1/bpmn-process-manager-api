package com.facimus.procesos.gestion.service.impl;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.security.Base32;
import com.facimus.procesos.common.security.Factor;
import com.facimus.procesos.common.security.Totp;
import com.facimus.procesos.gestion.dto.response.CodigosDeRecuperacionResponse;
import com.facimus.procesos.gestion.dto.response.InscripcionTotpResponse;
import com.facimus.procesos.gestion.dto.response.SegundoFactorResponse;
import com.facimus.procesos.gestion.model.CodigoDeRecuperacion;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.SegundoFactor;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.CodigoDeRecuperacionRepository;
import com.facimus.procesos.gestion.repository.SegundoFactorRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.SegundoFactorService;
import com.facimus.procesos.gestion.service.SesionService;

@Service
@Transactional(readOnly = true)
public class SegundoFactorServiceImpl implements SegundoFactorService {

    /** El nombre con que la aplicacion de autenticacion muestra la cuenta. */
    static final String EMISOR = "BPMN Process Manager";

    /** 160 bits: lo que recomienda el RFC 4226 para el secreto de HMAC-SHA1. */
    private static final int BYTES_DEL_SECRETO = 20;

    static final int CODIGOS = 10;

    /** 10 caracteres de Base32: 50 bits por codigo. */
    private static final int LARGO_DEL_CODIGO = 10;

    private static final String SIN_USUARIO = "Usuario no encontrado.";

    private final SegundoFactorRepository segundoFactorRepository;
    private final CodigoDeRecuperacionRepository codigoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CifradoDeSecretos cifrado;
    private final HistorialCambioService historialCambioService;
    private final SesionService sesionService;
    private final Clock reloj;
    private final SecureRandom aleatorio = new SecureRandom();

    @SuppressWarnings("java:S107") // las piezas del segundo factor, cada una por su nombre
    public SegundoFactorServiceImpl(SegundoFactorRepository segundoFactorRepository,
            CodigoDeRecuperacionRepository codigoRepository, UsuarioRepository usuarioRepository,
            CifradoDeSecretos cifrado, HistorialCambioService historialCambioService, SesionService sesionService,
            Clock reloj) {
        this.segundoFactorRepository = segundoFactorRepository;
        this.codigoRepository = codigoRepository;
        this.usuarioRepository = usuarioRepository;
        this.cifrado = cifrado;
        this.historialCambioService = historialCambioService;
        this.sesionService = sesionService;
        this.reloj = reloj;
    }

    @Override
    public SegundoFactorResponse estado(Long empresaId, Long usuarioId) {
        boolean totp = loPide(empresaId, usuarioId);
        return new SegundoFactorResponse(totp,
                totp ? codigoRepository.countByUsuarioIdAndEmpresaIdAndUsadoEnIsNull(usuarioId, empresaId) : 0);
    }

    @Override
    public boolean loPide(Long empresaId, Long usuarioId) {
        return segundoFactorRepository.existsByUsuarioIdAndEmpresaIdAndConfirmadoEnIsNotNull(usuarioId, empresaId);
    }

    @Override
    @Transactional
    public InscripcionTotpResponse inscribir(Long empresaId, Long usuarioId) {
        Usuario usuario = buscar(empresaId, usuarioId);
        Optional<SegundoFactor> anterior = segundoFactorRepository.findByUsuarioIdAndEmpresaId(usuarioId, empresaId);
        if (anterior.filter(SegundoFactor::estaConfirmado).isPresent()) {
            throw new ReglaNegocioException("Ya tienes una aplicación de autenticación activa: quítala antes de "
                    + "inscribir otra.");
        }
        byte[] secreto = new byte[BYTES_DEL_SECRETO];
        aleatorio.nextBytes(secreto);
        SegundoFactor factor = anterior.orElseGet(() -> SegundoFactor.builder()
                .empresa(usuario.getEmpresa())
                .usuario(usuario)
                .build());
        factor.setSecretoCifrado(cifrado.cifrar(secreto, usuarioId));
        factor.setCreadoEn(LocalDateTime.now());
        factor.setUltimoPaso(0);
        segundoFactorRepository.save(factor);
        String base32 = Base32.codificar(secreto);
        return new InscripcionTotpResponse(base32, uri(usuario.getEmail(), base32));
    }

    @Override
    @Transactional
    public CodigosDeRecuperacionResponse confirmar(Long empresaId, Long usuarioId, String codigo) {
        SegundoFactor factor = segundoFactorRepository.findByUsuarioIdAndEmpresaId(usuarioId, empresaId)
                .filter(guardado -> !guardado.estaConfirmado())
                .orElseThrow(() -> new ReglaNegocioException("No hay una aplicación de autenticación por confirmar: "
                        + "inscribe una primero."));
        OptionalLong paso = Totp.verificar(cifrado.descifrar(factor.getSecretoCifrado(), usuarioId), codigo,
                Instant.now(reloj), factor.getUltimoPaso());
        if (paso.isEmpty()) {
            throw new SolicitudInvalidaException("El código no es válido: revisa que la hora del teléfono esté bien "
                    + "y escribe el que muestra ahora la aplicación.");
        }
        factor.setUltimoPaso(paso.getAsLong());
        factor.setConfirmadoEn(LocalDateTime.now());
        Usuario usuario = factor.getUsuario();
        historialCambioService.registrarDeTienda(empresaId, usuarioId, RecursoDeHistorial.USUARIO, usuarioId,
                "Segundo factor de \"" + usuario.getNombre() + "\" activado.");
        return new CodigosDeRecuperacionResponse(nuevosCodigos(usuario));
    }

    @Override
    @Transactional
    public Optional<Factor> verificar(Long empresaId, Long usuarioId, String codigo) {
        Optional<SegundoFactor> guardado = segundoFactorRepository.findByUsuarioIdAndEmpresaId(usuarioId, empresaId)
                .filter(SegundoFactor::estaConfirmado);
        if (guardado.isEmpty() || codigo == null) {
            return Optional.empty();
        }
        SegundoFactor factor = guardado.get();
        String limpio = codigo.strip();
        if (limpio.length() == Totp.DIGITOS) {
            OptionalLong paso = Totp.verificar(cifrado.descifrar(factor.getSecretoCifrado(), usuarioId), limpio,
                    Instant.now(reloj), factor.getUltimoPaso());
            // Aceptar el paso es atomico: el mismo codigo en dos peticiones a la vez entra en una sola.
            return paso.isPresent() && segundoFactorRepository.aceptarPaso(factor.getId(), paso.getAsLong()) == 1
                    ? Optional.of(Factor.TOTP) : Optional.empty();
        }
        return codigoRepository.findByUsuarioIdAndEmpresaIdAndCodigoHashAndUsadoEnIsNull(usuarioId, empresaId,
                        cifrado.huella(normalizar(limpio)))
                .filter(recuperacion -> codigoRepository.marcarUsado(recuperacion.getId(), LocalDateTime.now()) == 1)
                .map(recuperacion -> Factor.CODIGO_DE_RECUPERACION);
    }

    @Override
    @Transactional
    public CodigosDeRecuperacionResponse renovarCodigos(Long empresaId, Long usuarioId) {
        SegundoFactor factor = segundoFactorRepository.findByUsuarioIdAndEmpresaId(usuarioId, empresaId)
                .filter(SegundoFactor::estaConfirmado)
                .orElseThrow(() -> new ReglaNegocioException("Los códigos de recuperación son del segundo factor: "
                        + "activa primero una aplicación de autenticación."));
        Usuario usuario = factor.getUsuario();
        codigoRepository.borrarLosDe(empresaId, usuarioId);
        historialCambioService.registrarDeTienda(empresaId, usuarioId, RecursoDeHistorial.USUARIO, usuarioId,
                "Códigos de recuperación de \"" + usuario.getNombre() + "\" renovados.");
        return new CodigosDeRecuperacionResponse(nuevosCodigos(usuario));
    }

    @Override
    @Transactional
    public void quitarPropio(Long empresaId, Long usuarioId) {
        Usuario usuario = buscar(empresaId, usuarioId);
        if (borrar(empresaId, usuarioId)) {
            historialCambioService.registrarDeTienda(empresaId, usuarioId, RecursoDeHistorial.USUARIO, usuarioId,
                    "Segundo factor de \"" + usuario.getNombre() + "\" quitado.");
        }
    }

    @Override
    @Transactional
    public void quitarDeOtro(Long empresaId, Long autorId, Long usuarioId) {
        if (autorId.equals(usuarioId)) {
            throw new ReglaNegocioException("Tu propio segundo factor se quita desde tu cuenta, con "
                    + "DELETE /api/v1/auth/segundo-factor/totp.");
        }
        Usuario usuario = buscar(empresaId, usuarioId);
        if (borrar(empresaId, usuarioId)) {
            historialCambioService.registrarDeTienda(empresaId, autorId, RecursoDeHistorial.USUARIO, usuarioId,
                    "Segundo factor de \"" + usuario.getNombre() + "\" quitado por un administrador.");
            // Quien tuviera el telefono o los codigos ya no esta dentro con ellos.
            sesionService.cerrarTodas(empresaId, usuarioId);
        }
    }

    @Override
    @Transactional
    public void borrarDe(Long empresaId, Long usuarioId) {
        borrar(empresaId, usuarioId);
    }

    /** Si habia algo que quitar. */
    private boolean borrar(Long empresaId, Long usuarioId) {
        codigoRepository.borrarLosDe(empresaId, usuarioId);
        return segundoFactorRepository.borrarElDe(empresaId, usuarioId) > 0;
    }

    private List<String> nuevosCodigos(Usuario usuario) {
        LocalDateTime ahora = LocalDateTime.now();
        List<String> codigos = new ArrayList<>(CODIGOS);
        List<CodigoDeRecuperacion> guardados = new ArrayList<>(CODIGOS);
        while (codigos.size() < CODIGOS) {
            String codigo = codigo();
            if (codigos.contains(codigo)) {
                continue;
            }
            codigos.add(codigo);
            guardados.add(CodigoDeRecuperacion.builder()
                    .empresa(usuario.getEmpresa())
                    .usuario(usuario)
                    .codigoHash(cifrado.huella(normalizar(codigo)))
                    .creadoEn(ahora)
                    .build());
        }
        codigoRepository.saveAll(guardados);
        return codigos;
    }

    /** Diez caracteres de Base32 en minusculas, en dos grupos de cinco para leerlos sin perderse. */
    private String codigo() {
        byte[] bytes = new byte[7];
        aleatorio.nextBytes(bytes);
        String texto = Base32.codificar(bytes).substring(0, LARGO_DEL_CODIGO).toLowerCase(Locale.ROOT);
        return texto.substring(0, 5) + "-" + texto.substring(5);
    }

    /** Como se escriba, con o sin guion, en mayusculas o con espacios, es el mismo codigo. */
    static String normalizar(String codigo) {
        return codigo.replace("-", "").replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private static String uri(String email, String secreto) {
        String emisor = codificar(EMISOR);
        return "otpauth://totp/" + emisor + ":" + codificar(email) + "?secret=" + secreto + "&issuer=" + emisor
                + "&algorithm=SHA1&digits=" + Totp.DIGITOS + "&period=" + Totp.SEGUNDOS_POR_PASO;
    }

    /** Como pide el formato de las URI de otpauth: los espacios van como %20, no como +. */
    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private Usuario buscar(Long empresaId, Long usuarioId) {
        return usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
                .filter(usuario -> usuario.getAnonimizadoEn() == null)
                .orElseThrow(() -> new RecursoNoEncontradoException(SIN_USUARIO));
    }
}
