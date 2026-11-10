package com.facimus.procesos.gestion.service.impl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.gestion.dto.response.PasskeyRegistradaResponse;
import com.facimus.procesos.gestion.dto.response.PasskeyResponse;
import com.facimus.procesos.gestion.model.Passkey;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.PasskeyRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.PasskeyGuardada;
import com.facimus.procesos.gestion.service.PasskeyService;
import com.facimus.procesos.gestion.service.PersonaWebAuthn;
import com.facimus.procesos.gestion.service.SegundoFactorService;

@Service
@Transactional(readOnly = true)
public class PasskeyServiceImpl implements PasskeyService {

    /** Lo que dice la etiqueta de una passkey a la que la persona no le puso nombre. */
    static final String ETIQUETA_POR_DEFECTO = "Passkey";

    static final int LARGO_DE_LA_ETIQUETA = 64;

    /** Una persona no necesita mas: son los dispositivos y gestores de claves que usa. */
    static final int MAXIMO_POR_PERSONA = 10;

    private static final String SIN_PASSKEY = "Passkey no encontrada.";

    private final PasskeyRepository passkeyRepository;
    private final UsuarioRepository usuarioRepository;
    private final SegundoFactorService segundoFactorService;
    private final HistorialCambioService historialCambioService;
    private final Auditoria auditoria;

    public PasskeyServiceImpl(PasskeyRepository passkeyRepository, UsuarioRepository usuarioRepository,
            SegundoFactorService segundoFactorService, HistorialCambioService historialCambioService,
            Auditoria auditoria) {
        this.passkeyRepository = passkeyRepository;
        this.usuarioRepository = usuarioRepository;
        this.segundoFactorService = segundoFactorService;
        this.historialCambioService = historialCambioService;
        this.auditoria = auditoria;
    }

    @Override
    public List<PasskeyResponse> listar(Long empresaId, Long usuarioId) {
        return passkeyRepository.findAllByUsuarioIdAndEmpresaIdOrderByCreadaEnAscIdAsc(usuarioId, empresaId).stream()
                .map(PasskeyServiceImpl::respuesta)
                .toList();
    }

    @Override
    @Transactional
    public PasskeyResponse renombrar(Long empresaId, Long usuarioId, Long passkeyId, String etiqueta) {
        Passkey passkey = passkeyRepository.findByIdAndUsuarioIdAndEmpresaId(passkeyId, usuarioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(SIN_PASSKEY));
        passkey.setEtiqueta(etiqueta(etiqueta));
        return respuesta(passkey);
    }

    @Override
    @Transactional
    public void quitar(Long empresaId, Long usuarioId, Long passkeyId) {
        Passkey passkey = passkeyRepository.findByIdAndUsuarioIdAndEmpresaId(passkeyId, usuarioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(SIN_PASSKEY));
        Usuario usuario = passkey.getUsuario();
        passkeyRepository.delete(passkey);
        passkeyRepository.flush();
        segundoFactorService.olvidarCodigosSinFactor(empresaId, usuarioId);
        historialCambioService.registrarDeTienda(empresaId, usuarioId, RecursoDeHistorial.USUARIO, usuarioId,
                "Passkey \"" + passkey.getEtiqueta() + "\" de \"" + usuario.getNombre() + "\" quitada.");
        auditoria.registrar(empresaId, usuarioId, "PASSKEY_QUITADA", "USUARIO", usuarioId,
                Map.of("passkey", passkeyId));
    }

    @Override
    @Transactional
    public PasskeyRegistradaResponse registrada(Long empresaId, Long usuarioId, byte[] credencialId) {
        Passkey passkey = passkeyRepository.findByCredencialId(credencialId)
                .filter(guardada -> guardada.getUsuario().getId().equals(usuarioId)
                        && guardada.getEmpresa().getId().equals(empresaId))
                .orElseThrow(() -> new RecursoNoEncontradoException(SIN_PASSKEY));
        historialCambioService.registrarDeTienda(empresaId, usuarioId, RecursoDeHistorial.USUARIO, usuarioId,
                "Passkey \"" + passkey.getEtiqueta() + "\" de \"" + passkey.getUsuario().getNombre()
                        + "\" registrada.");
        auditoria.registrar(empresaId, usuarioId, "PASSKEY_REGISTRADA", "USUARIO", usuarioId,
                Map.of("passkey", passkey.getId()));
        return new PasskeyRegistradaResponse(respuesta(passkey),
                segundoFactorService.codigosDelPrimerFactor(empresaId, usuarioId));
    }

    @Override
    public Optional<PersonaWebAuthn> personaPorCorreo(String email) {
        return usuarioRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .filter(PasskeyServiceImpl::puedeEntrar)
                .map(PasskeyServiceImpl::persona);
    }

    @Override
    public Optional<PersonaWebAuthn> personaPorHandle(byte[] handle) {
        return usuarioRepository.findByWebauthnId(handle)
                .filter(PasskeyServiceImpl::puedeEntrar)
                .map(PasskeyServiceImpl::persona);
    }

    @Override
    @Transactional
    public void asignarHandle(String email, byte[] handle) {
        Usuario usuario = usuarioRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .filter(PasskeyServiceImpl::puedeEntrar)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
        if (usuario.getWebauthnId() == null) {
            usuario.setWebauthnId(handle.clone());
        } else if (!Arrays.equals(usuario.getWebauthnId(), handle)) {
            throw new ReglaNegocioException("La persona ya tiene su identificador de WebAuthn.");
        }
    }

    @Override
    public Optional<PasskeyGuardada> buscar(byte[] credencialId) {
        return passkeyRepository.findByCredencialId(credencialId).map(PasskeyServiceImpl::guardada);
    }

    @Override
    public List<PasskeyGuardada> delHandle(byte[] handle) {
        return usuarioRepository.findByWebauthnId(handle)
                .map(usuario -> passkeyRepository.findAllByUsuarioIdAndEmpresaIdOrderByCreadaEnAscIdAsc(
                        usuario.getId(), usuario.getEmpresa().getId()))
                .orElse(List.of())
                .stream()
                .map(PasskeyServiceImpl::guardada)
                .toList();
    }

    @Override
    @Transactional
    public void guardarNueva(PasskeyGuardada nueva) {
        Usuario usuario = usuarioRepository.findByWebauthnId(nueva.handle())
                .filter(PasskeyServiceImpl::puedeEntrar)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
        Long empresaId = usuario.getEmpresa().getId();
        if (passkeyRepository.countByUsuarioIdAndEmpresaId(usuario.getId(), empresaId) >= MAXIMO_POR_PERSONA) {
            throw new ReglaNegocioException("Ya tienes " + MAXIMO_POR_PERSONA + " passkeys: quita una que ya no "
                    + "uses antes de agregar otra.");
        }
        passkeyRepository.save(Passkey.builder()
                .empresa(usuario.getEmpresa())
                .usuario(usuario)
                .credencialId(nueva.credencialId().clone())
                .clavePublica(nueva.clavePublica().clone())
                .contadorDeFirmas(nueva.contadorDeFirmas())
                .uvInicializado(nueva.uvInicializado())
                .transportes(nueva.transportes() == null ? "" : nueva.transportes())
                .respaldoPosible(nueva.respaldoPosible())
                .respaldada(nueva.respaldada())
                .objetoDeAtestacion(nueva.objetoDeAtestacion().clone())
                .datosDelCliente(nueva.datosDelCliente().clone())
                .etiqueta(etiqueta(nueva.etiqueta()))
                .creadaEn(LocalDateTime.now())
                .build());
    }

    @Override
    @Transactional
    public boolean registrarUso(PasskeyGuardada usada) {
        return passkeyRepository.findByCredencialId(usada.credencialId())
                .map(passkey -> passkeyRepository.registrarUso(passkey.getId(), usada.contadorDeFirmas(),
                        usada.respaldada(), usada.uvInicializado(), local(usada.ultimoUsoEn())) == 1)
                .orElse(false);
    }

    @Override
    @Transactional
    public void borrar(byte[] credencialId) {
        passkeyRepository.findByCredencialId(credencialId).ifPresent(passkeyRepository::delete);
    }

    /** Sin etiqueta, "Passkey"; con una muy larga, sus primeros 64 caracteres. */
    static String etiqueta(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            return ETIQUETA_POR_DEFECTO;
        }
        String limpia = etiqueta.strip().replaceAll("\\p{Cntrl}", "");
        return limpia.length() > LARGO_DE_LA_ETIQUETA ? limpia.substring(0, LARGO_DE_LA_ETIQUETA) : limpia;
    }

    /** Ni desactivada ni anonimizada: lo mismo que pide el login con clave. */
    private static boolean puedeEntrar(Usuario usuario) {
        return usuario.isActivo() && usuario.getAnonimizadoEn() == null;
    }

    private static PersonaWebAuthn persona(Usuario usuario) {
        return new PersonaWebAuthn(usuario.getEmpresa().getId(), usuario.getId(), usuario.getEmail(),
                usuario.getNombre(), usuario.getWebauthnId() == null ? null : usuario.getWebauthnId().clone());
    }

    private static PasskeyResponse respuesta(Passkey passkey) {
        return new PasskeyResponse(passkey.getId(), passkey.getEtiqueta(), passkey.getCreadaEn(),
                passkey.getUltimoUsoEn(), passkey.isRespaldada());
    }

    private static PasskeyGuardada guardada(Passkey passkey) {
        return new PasskeyGuardada(passkey.getCredencialId().clone(), passkey.getUsuario().getWebauthnId().clone(),
                passkey.getClavePublica().clone(), passkey.getContadorDeFirmas(), passkey.isUvInicializado(),
                passkey.getTransportes(), passkey.isRespaldoPosible(), passkey.isRespaldada(),
                passkey.getObjetoDeAtestacion().clone(), passkey.getDatosDelCliente().clone(), passkey.getEtiqueta(),
                instante(passkey.getCreadaEn()), instante(passkey.getUltimoUsoEn()));
    }

    private static Instant instante(LocalDateTime momento) {
        return momento == null ? null : momento.atZone(ZoneId.systemDefault()).toInstant();
    }

    private static LocalDateTime local(Instant momento) {
        return momento == null ? LocalDateTime.now() : LocalDateTime.ofInstant(momento, ZoneId.systemDefault());
    }
}
