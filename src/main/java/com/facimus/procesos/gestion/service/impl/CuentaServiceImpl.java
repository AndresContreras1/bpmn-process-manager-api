package com.facimus.procesos.gestion.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.EnlaceNoValidoException;
import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.correo.Redactor;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.model.EnlaceDeUnUso;
import com.facimus.procesos.gestion.model.PropositoDeEnlace;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EnlaceDeUnUsoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.CuentaService;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.SesionService;
import com.facimus.procesos.gestion.service.UsuarioService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CuentaServiceImpl implements CuentaService {

    private final UsuarioRepository usuarioRepository;
    private final EnlaceDeUnUsoRepository enlaceRepository;
    private final UsuarioService usuarioService;
    private final SesionService sesionService;
    private final HistorialCambioService historialCambioService;
    private final PasswordEncoder passwordEncoder;
    private final ColaDeTrabajos cola;
    private final JsonMapper jsonMapper;
    private final Clock reloj;

    @Override
    @Transactional
    public void pedirVerificacion(Long empresaId, Long usuarioId) {
        Usuario usuario = usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
        if (usuario.getCorreoVerificadoEn() == null) {
            encolar(new PedidoDeCorreo(PropositoDeEnlace.VERIFICAR_CORREO, empresaId, usuarioId, usuario.getEmail(),
                    null, null, idioma()));
        }
    }

    @Override
    @Transactional
    public void verificarCorreo(String token) {
        EnlaceDeUnUso enlace = usar(token, PropositoDeEnlace.VERIFICAR_CORREO);
        Usuario usuario = enlace.getUsuario();
        if (usuario.getCorreoVerificadoEn() == null) {
            usuario.setCorreoVerificadoEn(ahora());
        }
    }

    @Override
    @Transactional
    public void pedirRecuperacion(String email) {
        usuarioRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .filter(Usuario::isActivo)
                .ifPresent(usuario -> encolar(new PedidoDeCorreo(PropositoDeEnlace.RECUPERAR_CLAVE,
                        usuario.getEmpresa().getId(), usuario.getId(), usuario.getEmail(), null, null, idioma())));
    }

    @Override
    @Transactional
    public void recuperarClave(String token, String nueva) {
        EnlaceDeUnUso enlace = usar(token, PropositoDeEnlace.RECUPERAR_CLAVE);
        Usuario usuario = enlace.getUsuario();
        if (!usuario.isActivo()) {
            throw new EnlaceNoValidoException();
        }
        usuario.setPasswordHash(passwordEncoder.encode(nueva));
        usuario.setDebeCambiarClave(false);
        // Quien abrio el enlace probo de paso que el correo es suyo.
        if (usuario.getCorreoVerificadoEn() == null) {
            usuario.setCorreoVerificadoEn(ahora());
        }
        Long empresaId = enlace.getEmpresa().getId();
        historialCambioService.registrarDeTienda(empresaId, usuario.getId(), RecursoDeHistorial.USUARIO,
                usuario.getId(), "Contraseña restablecida con el enlace del correo.");
        // Quien la conocia, o tenia una sesion abierta con ella, deja de poder usarla.
        sesionService.cerrarTodas(empresaId, usuario.getId());
    }

    @Override
    @Transactional
    public void invitar(Long empresaId, Long autorId, String email, RolAcceso rolAcceso) {
        Usuario autor = usuarioRepository.findByIdAndEmpresaId(autorId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
        if (autor.getCorreoVerificadoEn() == null) {
            throw new ReglaNegocioException("Verifica tu correo antes de invitar a alguien: el enlace te llegó al "
                    + "registrar la tienda, y puedes pedir otro.");
        }
        String correo = email.trim().toLowerCase(Locale.ROOT);
        usuarioService.validarCorreoDisponible(correo);
        encolar(new PedidoDeCorreo(PropositoDeEnlace.INVITACION, empresaId, null, correo, rolAcceso, autorId,
                idioma()));
        historialCambioService.registrarDeTienda(empresaId, autorId, RecursoDeHistorial.EMPRESA, empresaId,
                "Invitación por correo a " + correo + " con rol " + rolAcceso + ".");
    }

    @Override
    @Transactional
    public UsuarioResponse aceptarInvitacion(String token, String nombre, String password) {
        EnlaceDeUnUso enlace = usar(token, PropositoDeEnlace.INVITACION);
        UsuarioResponse creado = usuarioService.crearColaborador(enlace.getEmpresa().getId(), null, nombre,
                enlace.getEmail(), password, enlace.getRolAcceso());
        // Llego al enlace con ese correo: queda verificado desde ya.
        usuarioRepository.findByIdAndEmpresaId(creado.id(), creado.empresaId())
                .ifPresent(usuario -> usuario.setCorreoVerificadoEn(ahora()));
        return creado;
    }

    @Override
    @Transactional
    public int olvidarEnlacesVencidos() {
        return enlaceRepository.borrarVencidosAntesDe(ahora().minusDays(7));
    }

    /**
     * El enlace del token, si sirve para esto, marcado como usado. Lo marca con un UPDATE condicionado: de dos usos a la
     * vez, el segundo no cambia nada y falla. Los demas enlaces vigentes del usuario para lo mismo dejan de servir.
     */
    private EnlaceDeUnUso usar(String token, PropositoDeEnlace proposito) {
        LocalDateTime ahora = ahora();
        EnlaceDeUnUso enlace = enlaceRepository.findByTokenHash(Huella.de(token))
                .filter(encontrado -> encontrado.getProposito() == proposito && encontrado.sirve(ahora))
                .orElseThrow(EnlaceNoValidoException::new);
        if (enlaceRepository.marcarUsado(enlace.getId(), ahora) == 0) {
            throw new EnlaceNoValidoException();
        }
        if (enlace.getUsuario() != null) {
            enlaceRepository.anularVigentes(enlace.getUsuario().getId(), proposito, ahora);
        }
        return enlace;
    }

    private void encolar(PedidoDeCorreo pedido) {
        cola.encolar(pedido.empresaId(), PedidoDeCorreo.TIPO, jsonMapper.writeValueAsString(pedido));
    }

    /** El idioma de quien hizo la peticion, si los correos lo hablan; si no, el espanol. */
    private static String idioma() {
        return Redactor.idioma(LocaleContextHolder.getLocale()).getLanguage();
    }

    /** PostgreSQL guarda microsegundos: lo que se escribe es lo que se leera despues. */
    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
    }
}
