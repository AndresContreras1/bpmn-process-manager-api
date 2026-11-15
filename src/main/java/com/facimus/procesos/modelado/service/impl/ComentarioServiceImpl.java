package com.facimus.procesos.modelado.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SinPermisoException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.markdown.Markdown;
import com.facimus.procesos.common.model.Accion;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.notificaciones.NotificacionPedida;
import com.facimus.procesos.common.notificaciones.TipoDeNotificacion;
import com.facimus.procesos.gestion.event.UsuarioAnonimizado;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.repository.VersionProcesoRepository;
import com.facimus.procesos.gestion.service.Actor;
import com.facimus.procesos.gestion.service.Permisos;
import com.facimus.procesos.gestion.service.Recurso;
import com.facimus.procesos.modelado.dto.response.ComentarioResponse;
import com.facimus.procesos.modelado.dto.response.HiloResponse;
import com.facimus.procesos.modelado.dto.response.PersonaResponse;
import com.facimus.procesos.modelado.model.Comentario;
import com.facimus.procesos.modelado.model.EstadoHilo;
import com.facimus.procesos.modelado.repository.ComentarioRepository;
import com.facimus.procesos.modelado.repository.ComentarioSpecifications;
import com.facimus.procesos.modelado.service.ComentarioService;

/**
 * PR 63: un hilo es su primer comentario y sus respuestas. Se escribe en Markdown y se guarda tambien el HTML saneado,
 * como la documentacion. Mencionar es solo a quien puede ver el proceso: una mencion de cualquier otra persona, de otra
 * tienda o que no existe responde lo mismo, asi que el error no dice si alguien existe. A quien hay que avisar se le
 * avisa con un pedido que espera en el outbox: si el comentario no queda, el aviso tampoco, y gestion lo convierte en
 * notificaciones despues.
 */
@Service
@Transactional(readOnly = true)
public class ComentarioServiceImpl implements ComentarioService {

    private static final String NO_ENCONTRADO = "Comentario no encontrado.";
    private static final String RECURSO = "COMENTARIO";
    private static final String PROCESO = "procesoId";
    private static final String NO_PUEDE_VER = "No es una persona de la organización que pueda ver este proceso.";

    private final ComentarioRepository comentarioRepository;
    private final ProcesoRepository procesoRepository;
    private final VersionProcesoRepository versionRepository;
    private final UsuarioRepository usuarioRepository;
    private final ElementosDelProceso elementos;
    private final Permisos permisos;
    private final Markdown markdown;
    private final Auditoria auditoria;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    @SuppressWarnings("java:S107")
    public ComentarioServiceImpl(ComentarioRepository comentarioRepository, ProcesoRepository procesoRepository,
            VersionProcesoRepository versionRepository, UsuarioRepository usuarioRepository,
            ElementosDelProceso elementos, Permisos permisos, Markdown markdown, Auditoria auditoria,
            ApplicationEventPublisher eventos, Clock reloj) {
        this.comentarioRepository = comentarioRepository;
        this.procesoRepository = procesoRepository;
        this.versionRepository = versionRepository;
        this.usuarioRepository = usuarioRepository;
        this.elementos = elementos;
        this.permisos = permisos;
        this.markdown = markdown;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    @Override
    public PageResponse<HiloResponse> listar(Long empresaId, Long procesoId, Filtro filtro, Pageable pagina) {
        buscarProceso(empresaId, procesoId);
        Page<Comentario> hilos = comentarioRepository.findAll(
                ComentarioSpecifications.hilos(empresaId, procesoId, vacioANulo(filtro.bpmnId()), filtro.version(),
                        filtro.estado()),
                PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "fechaCreacion", "id")));
        Map<Long, List<Comentario>> respuestas = hilos.isEmpty() ? Map.of()
                : comentarioRepository.respuestasDe(empresaId, hilos.map(Comentario::getId).getContent()).stream()
                        .collect(Collectors.groupingBy(Comentario::getPadreId, LinkedHashMap::new,
                                Collectors.toList()));
        List<Comentario> todos = new ArrayList<>(hilos.getContent());
        respuestas.values().forEach(todos::addAll);
        Map<Long, String> nombres = nombres(empresaId, todos);
        return PageResponse.from(hilos.map(hilo -> new HiloResponse(respuesta(hilo, nombres),
                respuestas.getOrDefault(hilo.getId(), List.of()).stream()
                        .map(una -> respuesta(una, nombres)).toList())));
    }

    @Override
    @Transactional
    public ComentarioResponse comentar(Long empresaId, Long usuarioId, Long procesoId, Nuevo nuevo) {
        Proceso proceso = procesoVisible(empresaId, usuarioId, procesoId);
        String bpmnId = vacioANulo(nuevo.bpmnId());
        if (nuevo.version() != null && versionRepository
                .findByProcesoIdAndNumeroAndEmpresaId(procesoId, nuevo.version(), empresaId).isEmpty()) {
            throw new RecursoNoEncontradoException("El proceso no tiene la versión " + nuevo.version() + ".");
        }
        // Un elemento de una version vieja puede no estar ya en el borrador: ese se toma como lo dibujo la web
        if (bpmnId != null && nuevo.version() == null && !elementos.existe(empresaId, procesoId, bpmnId)) {
            throw new RecursoNoEncontradoException("El proceso no tiene el elemento " + bpmnId + ".");
        }
        List<Long> menciones = menciones(empresaId, procesoId, nuevo.menciones());
        Comentario comentario = comentarioRepository.saveAndFlush(Comentario.builder().empresa(proceso.getEmpresa())
                .procesoId(procesoId).bpmnId(bpmnId).versionNumero(nuevo.version()).autorId(usuarioId)
                .markdown(nuevo.markdown()).html(markdown.aHtml(nuevo.markdown())).menciones(new ArrayList<>(menciones))
                .build());
        avisar(empresaId, TipoDeNotificacion.MENCION, usuarioId, comentario, comentario.getId(), menciones);
        return respuesta(comentario, nombres(empresaId, List.of(comentario)));
    }

    @Override
    @Transactional
    public ComentarioResponse responder(Long empresaId, Long usuarioId, Long comentarioId, String texto,
            List<Long> pedidas) {
        Comentario elegido = buscar(empresaId, comentarioId);
        Comentario hilo = elegido.esRespuesta() ? buscar(empresaId, elegido.getPadreId()) : elegido;
        Proceso proceso = procesoVisible(empresaId, usuarioId, hilo.getProcesoId());
        List<Long> menciones = menciones(empresaId, hilo.getProcesoId(), pedidas);
        Comentario respuesta = comentarioRepository.saveAndFlush(Comentario.builder().empresa(proceso.getEmpresa())
                .procesoId(hilo.getProcesoId()).padreId(hilo.getId()).autorId(usuarioId).markdown(texto)
                .html(markdown.aHtml(texto)).menciones(new ArrayList<>(menciones)).build());
        avisar(empresaId, TipoDeNotificacion.MENCION, usuarioId, respuesta, respuesta.getId(), menciones);
        // Quien ya recibe la mencion no recibe ademas la respuesta
        List<Long> participantes = comentarioRepository.participantes(empresaId, hilo.getId()).stream()
                .filter(id -> !menciones.contains(id)).toList();
        avisar(empresaId, TipoDeNotificacion.RESPUESTA, usuarioId, respuesta, respuesta.getId(),
                quienesPuedenVer(empresaId, hilo.getProcesoId(), participantes));
        return respuesta(respuesta, nombres(empresaId, List.of(respuesta)));
    }

    @Override
    @Transactional
    public ComentarioResponse editar(Long empresaId, Long usuarioId, Long comentarioId, String texto,
            List<Long> pedidas, Long version) {
        Comentario comentario = buscarVivo(empresaId, comentarioId);
        procesoVisible(empresaId, usuarioId, comentario.getProcesoId());
        if (!comentario.getAutorId().equals(usuarioId)) {
            throw new SinPermisoException("Solo quien escribió un comentario lo edita.");
        }
        comentario.verificarVersion(version);
        List<Long> menciones = menciones(empresaId, comentario.getProcesoId(), pedidas);
        List<Long> nuevas = menciones.stream().filter(id -> !comentario.getMenciones().contains(id)).toList();
        comentario.setMarkdown(texto);
        comentario.setHtml(markdown.aHtml(texto));
        comentario.setMenciones(new ArrayList<>(menciones));
        comentario.setEditadoEn(ahora());
        Comentario guardado = comentarioRepository.saveAndFlush(comentario);
        avisar(empresaId, TipoDeNotificacion.MENCION, usuarioId, guardado, guardado.getId(), nuevas);
        return respuesta(guardado, nombres(empresaId, List.of(guardado)));
    }

    @Override
    @Transactional
    public ComentarioResponse resolver(Long empresaId, Long usuarioId, Long comentarioId) {
        Comentario hilo = primeroDelHilo(empresaId, usuarioId, comentarioId);
        if (hilo.resuelto()) {
            throw new ReglaNegocioException("El hilo ya está resuelto.");
        }
        hilo.setResueltoPor(usuarioId);
        hilo.setResueltoEn(ahora());
        Comentario guardado = comentarioRepository.saveAndFlush(hilo);
        auditoria.registrar(empresaId, usuarioId, "COMENTARIO_RESUELTO", RECURSO, hilo.getId(),
                Map.of(PROCESO, hilo.getProcesoId()));
        avisar(empresaId, TipoDeNotificacion.HILO_RESUELTO, usuarioId, guardado, guardado.getId(),
                quienesPuedenVer(empresaId, hilo.getProcesoId(),
                        comentarioRepository.participantes(empresaId, hilo.getId())));
        return respuesta(guardado, nombres(empresaId, List.of(guardado)));
    }

    @Override
    @Transactional
    public ComentarioResponse reabrir(Long empresaId, Long usuarioId, Long comentarioId) {
        Comentario hilo = primeroDelHilo(empresaId, usuarioId, comentarioId);
        if (!hilo.resuelto()) {
            throw new ReglaNegocioException("El hilo ya está abierto.");
        }
        hilo.setResueltoPor(null);
        hilo.setResueltoEn(null);
        Comentario guardado = comentarioRepository.saveAndFlush(hilo);
        auditoria.registrar(empresaId, usuarioId, "COMENTARIO_REABIERTO", RECURSO, hilo.getId(),
                Map.of(PROCESO, hilo.getProcesoId()));
        return respuesta(guardado, nombres(empresaId, List.of(guardado)));
    }

    @Override
    @Transactional
    public void eliminar(Long empresaId, Long usuarioId, RolAcceso rol, Long comentarioId) {
        Comentario comentario = buscarVivo(empresaId, comentarioId);
        procesoVisible(empresaId, usuarioId, comentario.getProcesoId());
        boolean propio = comentario.getAutorId().equals(usuarioId);
        if (!propio && rol != RolAcceso.ADMINISTRADOR) {
            throw new SinPermisoException("Solo quien escribió un comentario, o un administrador, lo borra.");
        }
        // Se queda la fila, vacia: un hilo con respuestas sigue en pie y sus respuestas no pierden a que contestan
        comentario.setMarkdown("");
        comentario.setHtml("");
        comentario.setMenciones(new ArrayList<>());
        comentario.setEliminadoPor(usuarioId);
        comentario.setEliminadoEn(ahora());
        comentarioRepository.saveAndFlush(comentario);
        auditoria.registrar(empresaId, usuarioId, "COMENTARIO_ELIMINADO", RECURSO, comentarioId,
                Map.of(PROCESO, comentario.getProcesoId(), "propio", propio));
    }

    /**
     * PR 35: quien se anonimiza deja de tener nombre. Los comentarios que lo mencionaban con {@code @Nombre} lo
     * mencionan con su seudonimo; su autoria y sus menciones ya salen con el, porque el nombre se lee al mostrarlos.
     */
    @EventListener
    @Transactional
    public void alAnonimizarse(UsuarioAnonimizado anonimizado) {
        String antes = "@" + anonimizado.nombre();
        String despues = "@" + anonimizado.seudonimo();
        for (Comentario comentario : comentarioRepository.mencionandoA(anonimizado.empresaId(),
                anonimizado.usuarioId())) {
            if (comentario.getMarkdown().contains(antes)) {
                comentario.setMarkdown(comentario.getMarkdown().replace(antes, despues));
                comentario.setHtml(markdown.aHtml(comentario.getMarkdown()));
                comentarioRepository.save(comentario);
            }
        }
    }

    /**
     * Las menciones pedidas, sin repetir, si cada una es de alguien activo de la tienda que puede ver el proceso. Si
     * alguna no, un 400 que dice cual, con el mismo motivo para todas.
     */
    private List<Long> menciones(Long empresaId, Long procesoId, List<Long> pedidas) {
        if (pedidas == null || pedidas.isEmpty()) {
            return List.of();
        }
        List<Long> unicas = pedidas.stream().distinct().toList();
        Set<Long> pueden = new HashSet<>(quienesPuedenVer(empresaId, procesoId, unicas));
        Map<String, String> errores = new TreeMap<>();
        for (int i = 0; i < pedidas.size(); i++) {
            if (!pueden.contains(pedidas.get(i))) {
                errores.put("menciones[" + i + "]", NO_PUEDE_VER);
            }
        }
        if (!errores.isEmpty()) {
            throw new SolicitudInvalidaException("Solo se puede mencionar a quien puede ver este proceso.", errores);
        }
        return unicas;
    }

    /** De esas personas, las que siguen activas en la tienda y pueden ver el proceso. */
    private List<Long> quienesPuedenVer(Long empresaId, Long procesoId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return usuarioRepository.findAllByEmpresaIdAndIdIn(empresaId, ids).stream().filter(Usuario::isActivo)
                .filter(usuario -> permisos.puede(new Actor(usuario.getId(), empresaId, usuario.getRolAcceso()),
                        Accion.VER, Recurso.proceso(procesoId)))
                .map(Usuario::getId).sorted().toList();
    }

    /** Un pedido de notificaciones que sale con este commit, si queda alguien a quien avisar. */
    private void avisar(Long empresaId, TipoDeNotificacion tipo, Long autorId, Comentario comentario,
            Long referenciaId, List<Long> destinatarios) {
        List<Long> otros = destinatarios.stream().filter(id -> !id.equals(autorId)).toList();
        if (!otros.isEmpty()) {
            eventos.publishEvent(new NotificacionPedida(empresaId, tipo, autorId, comentario.getProcesoId(),
                    referenciaId, otros));
        }
    }

    private Comentario buscar(Long empresaId, Long comentarioId) {
        return comentarioRepository.findByIdAndEmpresaId(comentarioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADO));
    }

    /** Uno borrado ya no se edita ni se vuelve a borrar. */
    private Comentario buscarVivo(Long empresaId, Long comentarioId) {
        Comentario comentario = buscar(empresaId, comentarioId);
        if (comentario.eliminado()) {
            throw new RecursoNoEncontradoException(NO_ENCONTRADO);
        }
        return comentario;
    }

    /** Solo el primero de un hilo se resuelve y se reabre: el estado es del hilo. */
    private Comentario primeroDelHilo(Long empresaId, Long usuarioId, Long comentarioId) {
        Comentario comentario = buscar(empresaId, comentarioId);
        procesoVisible(empresaId, usuarioId, comentario.getProcesoId());
        if (comentario.esRespuesta()) {
            throw new ReglaNegocioException("Un hilo se resuelve o se reabre desde su primer comentario.");
        }
        return comentario;
    }

    /**
     * El proceso donde se escribe, si quien escribe lo ve. Comentar no trae ver: con una concesion que reserva ver, quien
     * no la tiene no escribe en un proceso que para el no existe.
     */
    private Proceso procesoVisible(Long empresaId, Long usuarioId, Long procesoId) {
        Proceso proceso = buscarProceso(empresaId, procesoId);
        permisos.exigir(empresaId, usuarioId, Accion.VER, Recurso.proceso(procesoId));
        return proceso;
    }

    private Proceso buscarProceso(Long empresaId, Long procesoId) {
        return procesoRepository.findByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
    }

    /** Los nombres de quienes escriben, mencionan y resuelven, en una sola consulta. */
    private Map<Long, String> nombres(Long empresaId, List<Comentario> comentarios) {
        Set<Long> ids = new HashSet<>();
        for (Comentario comentario : comentarios) {
            ids.add(comentario.getAutorId());
            ids.addAll(comentario.getMenciones());
            if (comentario.getResueltoPor() != null) {
                ids.add(comentario.getResueltoPor());
            }
        }
        return usuarioRepository.findAllByEmpresaIdAndIdIn(empresaId, ids).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNombre));
    }

    private static ComentarioResponse respuesta(Comentario comentario, Map<Long, String> nombres) {
        EstadoHilo estado = null;
        if (!comentario.esRespuesta()) {
            estado = comentario.resuelto() ? EstadoHilo.RESUELTO : EstadoHilo.ABIERTO;
        }
        return new ComentarioResponse(comentario.getId(), comentario.getProcesoId(), comentario.getPadreId(),
                comentario.getBpmnId(), comentario.getVersionNumero(), persona(comentario.getAutorId(), nombres),
                comentario.getMarkdown(), comentario.getHtml(),
                comentario.getMenciones().stream().map(id -> persona(id, nombres)).toList(), estado,
                persona(comentario.getResueltoPor(), nombres), comentario.getResueltoEn(), comentario.getEditadoEn(),
                comentario.eliminado(), comentario.getFechaCreacion(), comentario.getVersion());
    }

    private static PersonaResponse persona(Long id, Map<Long, String> nombres) {
        return id == null ? null : new PersonaResponse(id, nombres.get(id));
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    /** PostgreSQL guarda microsegundos: lo que se escribe es lo que se leera despues. */
    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
    }
}
