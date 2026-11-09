package com.facimus.procesos.gestion.service.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.facimus.procesos.common.EnlaceNoValidoException;
import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.IdentidadExternaResponse;
import com.facimus.procesos.gestion.dto.response.ResultadoEntradaExterna;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.mapper.UsuarioMapper;
import com.facimus.procesos.gestion.model.ConfiguracionTienda;
import com.facimus.procesos.gestion.model.DominioVerificado;
import com.facimus.procesos.gestion.model.IdentidadExterna;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.model.VinculoPendiente;
import com.facimus.procesos.gestion.repository.ConfiguracionTiendaRepository;
import com.facimus.procesos.gestion.repository.DominioVerificadoRepository;
import com.facimus.procesos.gestion.repository.IdentidadExternaRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.repository.VinculoPendienteRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.IdentidadExternaService;

@Service
@Transactional(readOnly = true)
public class IdentidadExternaServiceImpl implements IdentidadExternaService {

    /** Lo que espera un vinculo a que su dueno lo confirme. */
    static final Duration VIGENCIA_DEL_VINCULO = Duration.ofMinutes(10);

    static final String CORREO_NO_VERIFICADO = "correo-no-verificado";
    static final String CUENTA_INACTIVA = "cuenta-inactiva";
    static final String SIN_CUENTA = "sin-cuenta";

    private static final int BYTES_DEL_TOKEN = 32;

    private final IdentidadExternaRepository identidadRepository;
    private final VinculoPendienteRepository vinculoRepository;
    private final DominioVerificadoRepository dominioRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConfiguracionTiendaRepository configuracionRepository;
    private final UsuarioMapper usuarioMapper;
    private final PasswordEncoder passwordEncoder;
    private final HistorialCambioService historialCambioService;
    private final SecureRandom aleatorio = new SecureRandom();

    @SuppressWarnings("java:S107") // las piezas de la entrada externa, cada una por su nombre
    public IdentidadExternaServiceImpl(IdentidadExternaRepository identidadRepository,
            VinculoPendienteRepository vinculoRepository, DominioVerificadoRepository dominioRepository,
            UsuarioRepository usuarioRepository, ConfiguracionTiendaRepository configuracionRepository,
            UsuarioMapper usuarioMapper, PasswordEncoder passwordEncoder,
            HistorialCambioService historialCambioService) {
        this.identidadRepository = identidadRepository;
        this.vinculoRepository = vinculoRepository;
        this.dominioRepository = dominioRepository;
        this.usuarioRepository = usuarioRepository;
        this.configuracionRepository = configuracionRepository;
        this.usuarioMapper = usuarioMapper;
        this.passwordEncoder = passwordEncoder;
        this.historialCambioService = historialCambioService;
    }

    @Override
    @Transactional
    public ResultadoEntradaExterna entrar(String proveedor, String sujeto, String email, boolean correoVerificado,
            String nombre) {
        Optional<IdentidadExterna> vinculada = identidadRepository.findByProveedorAndSujeto(proveedor, sujeto);
        if (vinculada.isPresent()) {
            Usuario usuario = vinculada.get().getUsuario();
            return puedeEntrar(usuario) ? ResultadoEntradaExterna.entra(usuarioMapper.toResponse(usuario))
                    : ResultadoEntradaExterna.rechazada(CUENTA_INACTIVA);
        }
        // Un correo que el proveedor no comprobo puede ser de cualquiera: no abre la cuenta de quien lo tenga.
        if (!correoVerificado || !StringUtils.hasText(email)) {
            return ResultadoEntradaExterna.rechazada(CORREO_NO_VERIFICADO);
        }
        String correo = email.strip().toLowerCase(Locale.ROOT);
        Optional<Usuario> conEseCorreo = usuarioRepository.findByEmail(correo);
        if (conEseCorreo.isPresent()) {
            Usuario usuario = conEseCorreo.get();
            if (!puedeEntrar(usuario)) {
                return ResultadoEntradaExterna.rechazada(CUENTA_INACTIVA);
            }
            return ResultadoEntradaExterna.vincular(pendiente(usuario, proveedor, sujeto, correo));
        }
        String dominio = correo.substring(correo.indexOf('@') + 1);
        return dominioRepository.findByDominioAndVerificadoEnIsNotNull(dominio)
                .map(verificado -> ResultadoEntradaExterna.entra(unir(verificado, proveedor, sujeto, correo, nombre)))
                .orElseGet(() -> ResultadoEntradaExterna.rechazada(SIN_CUENTA));
    }

    @Override
    public Optional<UsuarioResponse> usuarioDelVinculo(String token) {
        return vinculoRepository.findByTokenHash(Huella.de(token))
                .filter(vinculo -> vinculo.getVenceEn().isAfter(LocalDateTime.now()))
                .map(VinculoPendiente::getUsuario)
                .filter(IdentidadExternaServiceImpl::puedeEntrar)
                .map(usuarioMapper::toResponse);
    }

    @Override
    @Transactional
    public void vincular(String token) {
        VinculoPendiente vinculo = vinculoRepository.findByTokenHash(Huella.de(token))
                .filter(guardado -> guardado.getVenceEn().isAfter(LocalDateTime.now()))
                .filter(guardado -> vinculoRepository.gastar(guardado.getId()) == 1)
                .orElseThrow(() -> new EnlaceNoValidoException());
        if (identidadRepository.findByProveedorAndSujeto(vinculo.getProveedor(), vinculo.getSujeto()).isPresent()) {
            throw new ReglaNegocioException("Esa cuenta de " + nombreDe(vinculo.getProveedor())
                    + " ya está vinculada.");
        }
        Usuario usuario = vinculo.getUsuario();
        guardar(usuario, vinculo.getProveedor(), vinculo.getSujeto(), vinculo.getEmail());
        historialCambioService.registrarDeTienda(usuario.getEmpresa().getId(), usuario.getId(),
                RecursoDeHistorial.USUARIO, usuario.getId(),
                "Cuenta de " + nombreDe(vinculo.getProveedor()) + " vinculada a \"" + usuario.getNombre() + "\".");
    }

    @Override
    public List<IdentidadExternaResponse> identidades(Long empresaId, Long usuarioId) {
        return identidadRepository.findAllByUsuarioIdAndEmpresaIdOrderByVinculadaEnAscIdAsc(usuarioId, empresaId)
                .stream()
                .map(identidad -> new IdentidadExternaResponse(identidad.getId(), identidad.getProveedor(),
                        identidad.getEmail(), identidad.getVinculadaEn()))
                .toList();
    }

    @Override
    @Transactional
    public void desvincular(Long empresaId, Long usuarioId, Long identidadId) {
        IdentidadExterna identidad = identidadRepository.findByIdAndUsuarioIdAndEmpresaId(identidadId, usuarioId,
                        empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cuenta vinculada no encontrada."));
        Usuario usuario = identidad.getUsuario();
        boolean esLaUltima = identidadRepository.findAllByUsuarioIdAndEmpresaIdOrderByVinculadaEnAscIdAsc(usuarioId,
                empresaId).size() == 1;
        if (esLaUltima && exigeEntradaExterna(empresaId, usuario.getRolAcceso())) {
            throw new ReglaNegocioException("Tu tienda entra solo con Google o Microsoft: sin esta cuenta no podrías "
                    + "volver a entrar.");
        }
        identidadRepository.delete(identidad);
        historialCambioService.registrarDeTienda(empresaId, usuarioId, RecursoDeHistorial.USUARIO, usuarioId,
                "Cuenta de " + nombreDe(identidad.getProveedor()) + " desvinculada de \"" + usuario.getNombre()
                        + "\".");
    }

    @Override
    public boolean exigeEntradaExterna(Long empresaId, RolAcceso rol) {
        return rol != RolAcceso.ADMINISTRADOR && configuracionRepository.findByEmpresaId(empresaId)
                .map(ConfiguracionTienda::isSoloEntradaExterna)
                .orElse(false);
    }

    @Override
    @Transactional
    public void borrarDe(Long empresaId, Long usuarioId) {
        vinculoRepository.borrarLosDe(empresaId, usuarioId);
        identidadRepository.borrarLasDe(empresaId, usuarioId);
    }

    /** El token en claro sale una vez, hacia la web; la base solo guarda su SHA-256. */
    private String pendiente(Usuario usuario, String proveedor, String sujeto, String correo) {
        LocalDateTime ahora = LocalDateTime.now();
        vinculoRepository.borrarVencidos(ahora);
        byte[] bytes = new byte[BYTES_DEL_TOKEN];
        aleatorio.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        vinculoRepository.save(VinculoPendiente.builder()
                .empresa(usuario.getEmpresa())
                .usuario(usuario)
                .proveedor(proveedor)
                .sujeto(sujeto)
                .email(correo)
                .tokenHash(Huella.de(token))
                .creadoEn(ahora)
                .venceEn(ahora.plus(VIGENCIA_DEL_VINCULO))
                .build());
        return token;
    }

    /**
     * Alguien de un dominio verificado que entra por primera vez: queda en la tienda con el rol por defecto y el correo
     * verificado, porque el proveedor lo verifico. Su contrasena es aleatoria y nadie la conoce: si algun dia la
     * quiere, la recupera por correo.
     */
    private UsuarioResponse unir(DominioVerificado dominio, String proveedor, String sujeto, String correo,
            String nombre) {
        byte[] clave = new byte[BYTES_DEL_TOKEN];
        aleatorio.nextBytes(clave);
        String suNombre = StringUtils.hasText(nombre) ? nombre.strip() : correo.substring(0, correo.indexOf('@'));
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .empresa(dominio.getEmpresa())
                .nombre(suNombre.length() > 120 ? suNombre.substring(0, 120) : suNombre)
                .email(correo)
                .passwordHash(passwordEncoder.encode(Base64.getEncoder().encodeToString(clave)))
                .rolAcceso(dominio.getRolPorDefecto())
                .correoVerificadoEn(LocalDateTime.now())
                .build());
        guardar(usuario, proveedor, sujeto, correo);
        historialCambioService.registrarDeTienda(dominio.getEmpresa().getId(), usuario.getId(),
                RecursoDeHistorial.USUARIO, usuario.getId(),
                "\"" + usuario.getNombre() + "\" se unió con " + nombreDe(proveedor) + " por el dominio "
                        + dominio.getDominio() + ", con rol " + dominio.getRolPorDefecto() + ".");
        return usuarioMapper.toResponse(usuario);
    }

    private void guardar(Usuario usuario, String proveedor, String sujeto, String correo) {
        identidadRepository.save(IdentidadExterna.builder()
                .empresa(usuario.getEmpresa())
                .usuario(usuario)
                .proveedor(proveedor)
                .sujeto(sujeto)
                .email(correo)
                .vinculadaEn(LocalDateTime.now())
                .build());
    }

    /** Como con la clave: activa, sin anonimizar y de una tienda que no se borro. Una en baja entra a leer. */
    private static boolean puedeEntrar(Usuario usuario) {
        return usuario.isActivo() && usuario.getAnonimizadoEn() == null
                && usuario.getEmpresa().getBorradaEn() == null;
    }

    /** Como se le dice a la persona el proveedor: Google, Microsoft o su id si es otro. */
    static String nombreDe(String proveedor) {
        return switch (proveedor) {
            case "google" -> "Google";
            case "microsoft" -> "Microsoft";
            default -> proveedor;
        };
    }
}
