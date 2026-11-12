package com.facimus.procesos.gestion.service.impl;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.facimus.procesos.common.DemasiadosIntentosException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.notificaciones.EnvioPorCorreo;
import com.facimus.procesos.common.notificaciones.NotificacionPedida;
import com.facimus.procesos.common.notificaciones.TipoDeNotificacion;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.security.ContextoDeTienda.Alcance;
import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.gestion.dto.response.ConteoDeNotificacionesResponse;
import com.facimus.procesos.gestion.dto.response.NotificacionResponse;
import com.facimus.procesos.gestion.dto.response.PreferenciaDeNotificacionResponse;
import com.facimus.procesos.gestion.model.Notificacion;
import com.facimus.procesos.gestion.model.PreferenciaNotificacion;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.NotificacionRepository;
import com.facimus.procesos.gestion.repository.PreferenciaNotificacionRepository;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.NotificacionService;

import tools.jackson.databind.json.JsonMapper;

/**
 * PR 63: crear una notificacion es guardarla, mandarla al flujo en vivo de su destinatario y, si lo eligio, dejar su
 * correo en la cola, las tres cosas en la misma transaccion: una notificacion que no quedo no sale por ningun lado.
 * Lo que espera al resumen del dia lo junta un reloj diario. Nada de esto guarda el texto de un comentario.
 */
@Service
@Transactional(readOnly = true)
public class NotificacionServiceImpl implements NotificacionService {

    private static final String NO_ENCONTRADA = "Notificación no encontrada.";

    private final NotificacionRepository notificacionRepository;
    private final PreferenciaNotificacionRepository preferenciaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProcesoRepository procesoRepository;
    private final CanalDeNotificaciones canal;
    private final ColaDeTrabajos cola;
    private final JsonMapper json;
    private final Clock reloj;
    private final Duration retencion;
    private final int conexionesPorUsuario;

    @SuppressWarnings("java:S107")
    public NotificacionServiceImpl(NotificacionRepository notificacionRepository,
            PreferenciaNotificacionRepository preferenciaRepository, UsuarioRepository usuarioRepository,
            ProcesoRepository procesoRepository, CanalDeNotificaciones canal, ColaDeTrabajos cola, JsonMapper json,
            Clock reloj, @Value("${notificaciones.retencion:90d}") Duration retencion,
            @Value("${notificaciones.flujo.conexiones-por-usuario:5}") int conexionesPorUsuario) {
        this.notificacionRepository = notificacionRepository;
        this.preferenciaRepository = preferenciaRepository;
        this.usuarioRepository = usuarioRepository;
        this.procesoRepository = procesoRepository;
        this.canal = canal;
        this.cola = cola;
        this.json = json;
        this.reloj = reloj;
        this.retencion = retencion;
        this.conexionesPorUsuario = conexionesPorUsuario;
    }

    @Override
    @Transactional
    public void notificar(NotificacionPedida pedido) {
        // Corre despues del commit de lo que paso, en otro hilo: lo que escribe es de la tienda del pedido y de ninguna
        // otra
        Alcance anterior = ContextoDeTienda.fijar(Alcance.deLaTienda(pedido.empresaId()));
        try {
            crear(pedido);
        } finally {
            ContextoDeTienda.restaurar(anterior);
        }
    }

    private void crear(NotificacionPedida pedido) {
        Long empresaId = pedido.empresaId();
        List<Long> ids = pedido.destinatarios().stream().filter(id -> !id.equals(pedido.autorId())).distinct()
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        List<Usuario> personas = usuarioRepository.findAllByEmpresaIdAndIdIn(empresaId, ids).stream()
                .filter(Usuario::isActivo).sorted(Comparator.comparing(Usuario::getId)).toList();
        Map<Long, EnvioPorCorreo> elegidos = preferenciaRepository
                .findAllByEmpresaIdAndTipoAndUsuarioIdIn(empresaId, pedido.tipo(), ids).stream()
                .collect(Collectors.toMap(PreferenciaNotificacion::getUsuarioId, PreferenciaNotificacion::getEnvio));
        String proceso = pedido.procesoId() == null ? null : procesoRepository
                .findByIdAndEmpresaId(pedido.procesoId(), empresaId).map(Proceso::getNombre).orElse(null);
        String autor = pedido.autorId() == null ? null : usuarioRepository
                .findByIdAndEmpresaId(pedido.autorId(), empresaId).map(Usuario::getNombre).orElse(null);
        LocalDateTime ahora = ahora();
        for (Usuario persona : personas) {
            EnvioPorCorreo envio = elegidos.getOrDefault(persona.getId(), pedido.tipo().porDefecto());
            Notificacion notificacion = notificacionRepository.save(Notificacion.builder()
                    .empresa(persona.getEmpresa()).usuarioId(persona.getId()).tipo(pedido.tipo())
                    .autorId(pedido.autorId()).procesoId(pedido.procesoId()).referenciaId(pedido.referenciaId())
                    .envio(envio).creadaEn(ahora).build());
            if (envio == EnvioPorCorreo.INMEDIATO) {
                cola.encolar(empresaId, CorreosDeNotificaciones.TRABAJO_DE_CORREO, json.writeValueAsString(
                        new PedidoDeAviso(persona.getId(), List.of(notificacion.getId()), false)));
            }
            canal.publicar(empresaId, persona.getId(), CanalDeNotificaciones.NOTIFICACION,
                    respuesta(notificacion, proceso, autor));
            canal.publicar(empresaId, persona.getId(), CanalDeNotificaciones.CONTEO, contar(empresaId,
                    persona.getId()));
        }
    }

    @Override
    public PageResponse<NotificacionResponse> listar(Long empresaId, Long usuarioId, boolean soloNoLeidas,
            Pageable pagina) {
        Page<Notificacion> encontradas = soloNoLeidas
                ? notificacionRepository.findAllByEmpresaIdAndUsuarioIdAndLeidaEnIsNullOrderByCreadaEnDescIdDesc(
                        empresaId, usuarioId, pagina)
                : notificacionRepository.findAllByEmpresaIdAndUsuarioIdOrderByCreadaEnDescIdDesc(empresaId,
                        usuarioId, pagina);
        Set<Long> procesoIds = encontradas.stream().map(Notificacion::getProcesoId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> autorIds = encontradas.stream().map(Notificacion::getAutorId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> procesos = procesoIds.isEmpty() ? Map.of()
                : procesoRepository.findAllByIdInAndEmpresaId(procesoIds, empresaId).stream()
                        .collect(Collectors.toMap(Proceso::getId, Proceso::getNombre));
        Map<Long, String> autores = autorIds.isEmpty() ? Map.of()
                : usuarioRepository.findAllByEmpresaIdAndIdIn(empresaId, autorIds).stream()
                        .collect(Collectors.toMap(Usuario::getId, Usuario::getNombre));
        return PageResponse.from(encontradas.map(notificacion -> respuesta(notificacion,
                procesos.get(notificacion.getProcesoId()), autores.get(notificacion.getAutorId()))));
    }

    @Override
    public ConteoDeNotificacionesResponse contar(Long empresaId, Long usuarioId) {
        return new ConteoDeNotificacionesResponse(
                notificacionRepository.countByEmpresaIdAndUsuarioIdAndLeidaEnIsNull(empresaId, usuarioId));
    }

    @Override
    @Transactional
    public NotificacionResponse marcarLeida(Long empresaId, Long usuarioId, Long notificacionId) {
        // La de otra persona no existe para quien pregunta, aunque sea de su tienda
        Notificacion notificacion = notificacionRepository
                .findByIdAndEmpresaIdAndUsuarioId(notificacionId, empresaId, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADA));
        if (notificacion.getLeidaEn() == null) {
            notificacion.setLeidaEn(ahora());
            notificacionRepository.save(notificacion);
            canal.publicar(empresaId, usuarioId, CanalDeNotificaciones.CONTEO, contar(empresaId, usuarioId));
        }
        String proceso = notificacion.getProcesoId() == null ? null : procesoRepository
                .findByIdAndEmpresaId(notificacion.getProcesoId(), empresaId).map(Proceso::getNombre).orElse(null);
        String autor = notificacion.getAutorId() == null ? null : usuarioRepository
                .findByIdAndEmpresaId(notificacion.getAutorId(), empresaId).map(Usuario::getNombre).orElse(null);
        return respuesta(notificacion, proceso, autor);
    }

    @Override
    @Transactional
    public ConteoDeNotificacionesResponse marcarTodasLeidas(Long empresaId, Long usuarioId) {
        if (notificacionRepository.marcarLeidas(empresaId, usuarioId, ahora()) > 0) {
            canal.publicar(empresaId, usuarioId, CanalDeNotificaciones.CONTEO, new ConteoDeNotificacionesResponse(0));
        }
        return contar(empresaId, usuarioId);
    }

    @Override
    public SseEmitter conectar(Long empresaId, Long usuarioId) {
        // Sin caducidad: dura lo que dure la pestana, y los latidos dicen si sigue
        SseEmitter emisor = new SseEmitter(0L);
        conectar(empresaId, usuarioId, emisor);
        return emisor;
    }

    @Override
    public void conectar(Long empresaId, Long usuarioId, SseEmitter emisor) {
        if (canal.abiertas(empresaId, usuarioId) >= conexionesPorUsuario) {
            throw new DemasiadosIntentosException("Tienes demasiadas pestañas siguiendo tus notificaciones: cierra "
                    + "alguna.", Duration.ofSeconds(30));
        }
        String conexion = UUID.randomUUID().toString();
        canal.registrar(conexion, empresaId, usuarioId, emisor);
        Runnable alCerrar = () -> canal.quitar(conexion);
        emisor.onCompletion(alCerrar);
        emisor.onTimeout(alCerrar);
        emisor.onError(error -> alCerrar.run());
        try {
            // Lo primero que recibe es cuantas tiene sin leer, para pintar la campana sin otra peticion
            emisor.send(SseEmitter.event().name(CanalDeNotificaciones.CONTEO).data(contar(empresaId, usuarioId)));
        } catch (IOException | IllegalStateException cortada) {
            canal.quitar(conexion);
            emisor.completeWithError(cortada);
        }
    }

    @Override
    public List<PreferenciaDeNotificacionResponse> preferencias(Long empresaId, Long usuarioId) {
        Map<TipoDeNotificacion, EnvioPorCorreo> elegidas = new EnumMap<>(TipoDeNotificacion.class);
        preferenciaRepository.findAllByEmpresaIdAndUsuarioId(empresaId, usuarioId)
                .forEach(preferencia -> elegidas.put(preferencia.getTipo(), preferencia.getEnvio()));
        List<PreferenciaDeNotificacionResponse> respuesta = new ArrayList<>();
        for (TipoDeNotificacion tipo : TipoDeNotificacion.values()) {
            EnvioPorCorreo elegida = elegidas.get(tipo);
            respuesta.add(new PreferenciaDeNotificacionResponse(tipo, elegida == null ? tipo.porDefecto() : elegida,
                    elegida == null));
        }
        return respuesta;
    }

    @Override
    @Transactional
    public List<PreferenciaDeNotificacionResponse> guardarPreferencias(Long empresaId, Long usuarioId,
            Map<TipoDeNotificacion, EnvioPorCorreo> preferencias) {
        Usuario usuario = usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
        Map<TipoDeNotificacion, PreferenciaNotificacion> actuales = new EnumMap<>(TipoDeNotificacion.class);
        preferenciaRepository.findAllByEmpresaIdAndUsuarioId(empresaId, usuarioId)
                .forEach(preferencia -> actuales.put(preferencia.getTipo(), preferencia));
        LocalDateTime ahora = ahora();
        preferencias.forEach((tipo, envio) -> {
            PreferenciaNotificacion preferencia = actuales.get(tipo);
            if (preferencia == null) {
                preferencia = PreferenciaNotificacion.builder().empresa(usuario.getEmpresa()).usuarioId(usuarioId)
                        .tipo(tipo).build();
            }
            preferencia.setEnvio(envio);
            preferencia.setFechaModificacion(ahora);
            preferenciaRepository.save(preferencia);
        });
        return preferencias(empresaId, usuarioId);
    }

    @Override
    @Transactional
    public int encolarResumenes() {
        // Corre como sistema: junta lo de todas las tiendas, y cada correo queda en la cola como trabajo de la suya
        Map<List<Long>, List<Long>> porPersona = new LinkedHashMap<>();
        for (Object[] fila : notificacionRepository.pendientesDelResumen()) {
            porPersona.computeIfAbsent(List.of((Long) fila[0], (Long) fila[1]), clave -> new ArrayList<>())
                    .add((Long) fila[2]);
        }
        porPersona.forEach((persona, notificaciones) -> cola.encolar(persona.get(0), CorreosDeNotificaciones.TRABAJO_DE_CORREO,
                json.writeValueAsString(new PedidoDeAviso(persona.get(1), notificaciones, true))));
        return porPersona.size();
    }

    @Override
    @Transactional
    public int olvidarLeidas() {
        return notificacionRepository.borrarLeidasAntesDe(ahora().minus(retencion));
    }

    @Override
    @Transactional
    public void borrarDe(Long empresaId, Long usuarioId) {
        notificacionRepository.borrarLasDe(empresaId, usuarioId);
        preferenciaRepository.borrarLasDe(empresaId, usuarioId);
    }

    static NotificacionResponse respuesta(Notificacion notificacion, String proceso, String autor) {
        return new NotificacionResponse(notificacion.getId(), notificacion.getTipo(), notificacion.getProcesoId(),
                proceso, notificacion.getReferenciaId(), notificacion.getAutorId(), autor, notificacion.getCreadaEn(),
                notificacion.getLeidaEn());
    }

    /** PostgreSQL guarda microsegundos: lo que se escribe es lo que se leera despues. */
    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
    }
}
