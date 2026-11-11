package com.facimus.procesos.modelado.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.facimus.procesos.common.DemasiadosIntentosException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.gestion.event.ProcesoModificado;
import com.facimus.procesos.gestion.event.ProcesoPublicado;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.modelado.dto.response.BloqueoResponse;
import com.facimus.procesos.modelado.model.BloqueoEdicion;
import com.facimus.procesos.modelado.model.Presencia;
import com.facimus.procesos.modelado.repository.BloqueoEdicionRepository;
import com.facimus.procesos.modelado.repository.PresenciaRepository;
import com.facimus.procesos.modelado.service.EnVivoService;

/**
 * PR 58: el bloqueo es suave: dice quien esta editando para no pisarse, y el primero que edita lo tiene mientras siga
 * dando latidos. No impide guardar: de eso se encarga {@code If-Match}.
 */
@Service
@Transactional(readOnly = true)
public class EnVivoServiceImpl implements EnVivoService {

    private static final String BLOQUEO = "bloqueo";

    private final ProcesoRepository procesoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PresenciaRepository presenciaRepository;
    private final BloqueoEdicionRepository bloqueoRepository;
    private final CanalEnVivo canal;
    private final Auditoria auditoria;
    private final Duration vencimiento;
    private final int conexionesPorUsuario;

    public EnVivoServiceImpl(ProcesoRepository procesoRepository, UsuarioRepository usuarioRepository,
            PresenciaRepository presenciaRepository, BloqueoEdicionRepository bloqueoRepository, CanalEnVivo canal,
            Auditoria auditoria, @Value("${modelado.en-vivo.vencimiento-bloqueo:PT60S}") Duration vencimiento,
            @Value("${modelado.en-vivo.conexiones-por-usuario:5}") int conexionesPorUsuario) {
        this.procesoRepository = procesoRepository;
        this.usuarioRepository = usuarioRepository;
        this.presenciaRepository = presenciaRepository;
        this.bloqueoRepository = bloqueoRepository;
        this.canal = canal;
        this.auditoria = auditoria;
        this.vencimiento = vencimiento;
        this.conexionesPorUsuario = conexionesPorUsuario;
    }

    @Override
    @Transactional
    public SseEmitter conectar(Long empresaId, Long usuarioId, Long procesoId) {
        // Sin caducidad: la conexion dura lo que dure la pestana, y los latidos dicen si sigue
        SseEmitter emisor = new SseEmitter(0L);
        conectar(empresaId, usuarioId, procesoId, emisor);
        return emisor;
    }

    @Override
    @Transactional
    public void conectar(Long empresaId, Long usuarioId, Long procesoId, SseEmitter emisor) {
        Proceso proceso = buscar(empresaId, procesoId);
        Usuario usuario = usuario(empresaId, usuarioId);
        LocalDateTime ahora = LocalDateTime.now();
        long abiertas = presenciaRepository.countByUsuarioIdAndEmpresaIdAndUltimoLatidoAfter(usuarioId, empresaId,
                ahora.minusSeconds(60));
        if (abiertas >= conexionesPorUsuario) {
            throw new DemasiadosIntentosException("Tienes demasiadas pestañas abiertas en vivo: cierra alguna.",
                    Duration.ofSeconds(30));
        }
        String conexion = UUID.randomUUID().toString();
        presenciaRepository.save(Presencia.builder().empresa(proceso.getEmpresa()).procesoId(procesoId)
                .usuarioId(usuarioId).nombre(usuario.getNombre()).conexion(conexion).desde(ahora).ultimoLatido(ahora)
                .build());
        canal.registrar(conexion, empresaId, procesoId, emisor);
        Runnable alCerrar = () -> desconectar(conexion, empresaId, procesoId);
        emisor.onCompletion(alCerrar);
        emisor.onTimeout(alCerrar);
        emisor.onError(error -> alCerrar.run());
        canal.publicar(empresaId, procesoId, CanalEnVivo.PRESENCIA, Map.of());
    }

    /** Lo llama el contenedor al cerrarse la conexion, fuera de la peticion que la abrio. */
    private void desconectar(String conexion, Long empresaId, Long procesoId) {
        if (canal.quitar(conexion)) {
            ContextoDeTienda.deLaTienda(empresaId, () -> {
                presenciaRepository.borrarConexion(conexion);
                canal.publicar(empresaId, procesoId, CanalEnVivo.PRESENCIA, Map.of());
            });
        }
    }

    @Override
    public BloqueoResponse estado(Long empresaId, Long usuarioId, Long procesoId) {
        buscar(empresaId, procesoId);
        return respuesta(vigente(empresaId, procesoId).orElse(null), usuarioId);
    }

    @Override
    @Transactional
    public BloqueoResponse adquirir(Long empresaId, Long usuarioId, Long procesoId) {
        Proceso proceso = bloquear(empresaId, procesoId);
        Optional<BloqueoEdicion> vigente = vigente(empresaId, procesoId);
        if (vigente.isPresent() && !vigente.get().getUsuarioId().equals(usuarioId)) {
            return respuesta(vigente.get(), usuarioId);
        }
        boolean nuevo = vigente.isEmpty();
        BloqueoEdicion bloqueo = poner(proceso, usuarioId, nuevo);
        if (nuevo) {
            canal.publicar(empresaId, procesoId, BLOQUEO, datos(bloqueo));
        }
        return respuesta(bloqueo, usuarioId);
    }

    @Override
    @Transactional
    public BloqueoResponse latir(Long empresaId, Long usuarioId, Long procesoId) {
        Proceso proceso = bloquear(empresaId, procesoId);
        Optional<BloqueoEdicion> vigente = vigente(empresaId, procesoId);
        if (vigente.isPresent() && !vigente.get().getUsuarioId().equals(usuarioId)) {
            throw new ReglaNegocioException(vigente.get().getNombre() + " tomó el control de este proceso.");
        }
        return respuesta(poner(proceso, usuarioId, vigente.isEmpty()), usuarioId);
    }

    @Override
    @Transactional
    public BloqueoResponse soltar(Long empresaId, Long usuarioId, Long procesoId) {
        bloquear(empresaId, procesoId);
        Optional<BloqueoEdicion> actual = bloqueoRepository.findByProcesoIdAndEmpresaId(procesoId, empresaId);
        if (actual.isPresent() && actual.get().getUsuarioId().equals(usuarioId)) {
            bloqueoRepository.delete(actual.get());
            canal.publicar(empresaId, procesoId, BLOQUEO, new HashMap<>());
            return respuesta(null, usuarioId);
        }
        return respuesta(vigente(empresaId, procesoId).orElse(null), usuarioId);
    }

    @Override
    @Transactional
    public BloqueoResponse tomarControl(Long empresaId, Long usuarioId, Long procesoId) {
        Proceso proceso = bloquear(empresaId, procesoId);
        Optional<BloqueoEdicion> anterior = vigente(empresaId, procesoId);
        Long deQuien = anterior.map(BloqueoEdicion::getUsuarioId).orElse(null);
        String nombreAnterior = anterior.map(BloqueoEdicion::getNombre).orElse(null);
        BloqueoEdicion bloqueo = poner(proceso, usuarioId, true);
        if (deQuien != null && !deQuien.equals(usuarioId)) {
            auditoria.registrar(empresaId, usuarioId, "CONTROL_TOMADO", "PROCESO", procesoId,
                    Map.of("anterior", deQuien));
            Map<String, Object> aviso = datos(bloqueo);
            aviso.put("anteriorId", deQuien);
            aviso.put("anterior", nombreAnterior);
            canal.publicar(empresaId, procesoId, "control-tomado", aviso);
        } else {
            canal.publicar(empresaId, procesoId, BLOQUEO, datos(bloqueo));
        }
        return respuesta(bloqueo, usuarioId);
    }

    /** Quien lo guarda en la fila del proceso; {@code estrena} reinicia desde cuando. */
    private BloqueoEdicion poner(Proceso proceso, Long usuarioId, boolean estrena) {
        Long empresaId = proceso.getEmpresa().getId();
        LocalDateTime ahora = LocalDateTime.now();
        BloqueoEdicion bloqueo = bloqueoRepository.findByProcesoIdAndEmpresaId(proceso.getId(), empresaId)
                .orElseGet(() -> BloqueoEdicion.builder().empresa(proceso.getEmpresa()).procesoId(proceso.getId())
                        .build());
        if (estrena || bloqueo.getDesde() == null) {
            bloqueo.setDesde(ahora);
        }
        bloqueo.setUsuarioId(usuarioId);
        bloqueo.setNombre(usuario(empresaId, usuarioId).getNombre());
        bloqueo.setUltimoLatido(ahora);
        return bloqueoRepository.save(bloqueo);
    }

    /** El bloqueo, si sigue con latido. */
    private Optional<BloqueoEdicion> vigente(Long empresaId, Long procesoId) {
        return bloqueoRepository.findByProcesoIdAndEmpresaId(procesoId, empresaId)
                .filter(bloqueo -> bloqueo.getUltimoLatido().plus(vencimiento).isAfter(LocalDateTime.now()));
    }

    private BloqueoResponse respuesta(BloqueoEdicion bloqueo, Long usuarioId) {
        if (bloqueo == null) {
            return new BloqueoResponse(true, false, null, null, null, null);
        }
        return new BloqueoResponse(false, bloqueo.getUsuarioId().equals(usuarioId), bloqueo.getUsuarioId(),
                bloqueo.getNombre(), bloqueo.getDesde(), bloqueo.getUltimoLatido().plus(vencimiento));
    }

    private static Map<String, Object> datos(BloqueoEdicion bloqueo) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("usuarioId", bloqueo.getUsuarioId());
        datos.put("nombre", bloqueo.getNombre());
        return datos;
    }

    /** Lo guardado y lo publicado, para quien este mirando. Sale cuando se confirma lo que se esta haciendo. */
    @EventListener
    public void alModificarse(ProcesoModificado evento) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("autor", evento.autor());
        canal.publicar(evento.empresaId(), evento.procesoId(), "guardado", datos);
    }

    @EventListener
    public void alPublicarse(ProcesoPublicado evento) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("numero", evento.numero());
        datos.put("autor", evento.autor());
        canal.publicar(evento.empresaId(), evento.procesoId(), "publicado", datos);
    }

    private Proceso buscar(Long empresaId, Long procesoId) {
        return procesoRepository.findByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
    }

    private Proceso bloquear(Long empresaId, Long procesoId) {
        return procesoRepository.bloquear(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
    }

    private Usuario usuario(Long empresaId, Long usuarioId) {
        return usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
    }
}
