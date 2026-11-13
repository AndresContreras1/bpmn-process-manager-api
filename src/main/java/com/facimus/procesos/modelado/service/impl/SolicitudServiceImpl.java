package com.facimus.procesos.modelado.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
import com.facimus.procesos.gestion.event.ProcesoEliminado;
import com.facimus.procesos.gestion.event.UsuarioAnonimizado;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.ProcesoSpecifications;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.Actor;
import com.facimus.procesos.gestion.service.BorradorCongelado;
import com.facimus.procesos.gestion.service.Instantanea;
import com.facimus.procesos.gestion.service.Permisos;
import com.facimus.procesos.gestion.service.PoliticaDePublicacionService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.Recurso;
import com.facimus.procesos.gestion.service.ReglaDeAprobacion;
import com.facimus.procesos.gestion.service.VersionService;
import com.facimus.procesos.gestion.service.Visibilidad;
import com.facimus.procesos.modelado.dto.response.ComparacionResponse;
import com.facimus.procesos.modelado.dto.response.DecisionResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.PersonaResponse;
import com.facimus.procesos.modelado.dto.response.RevisionBorradorResponse;
import com.facimus.procesos.modelado.dto.response.SolicitudResponse;
import com.facimus.procesos.modelado.model.Aprobacion;
import com.facimus.procesos.modelado.model.Decision;
import com.facimus.procesos.modelado.model.EstadoSolicitud;
import com.facimus.procesos.modelado.model.MotivoDeRevision;
import com.facimus.procesos.modelado.model.SolicitudPublicacion;
import com.facimus.procesos.modelado.repository.AprobacionRepository;
import com.facimus.procesos.modelado.repository.SolicitudPublicacionRepository;
import com.facimus.procesos.modelado.repository.SolicitudSpecifications;
import com.facimus.procesos.modelado.service.GuardadoDelModeloService;
import com.facimus.procesos.modelado.service.SolicitudService;

import tools.jackson.databind.json.JsonMapper;

/**
 * PR 64: una solicitud congela el borrador dos veces: en una revision del borrador (PR 40), el punto al que volver, y
 * en la instantanea que se publicara (D2), con el numero que tendra y la version vigente contra la que se comparo. Lo
 * que se edite despues en el borrador no cambia lo que se revisa. Sus aprobadores se fijan al pedirla, segun la
 * politica de ese dia; la ultima aprobacion que hacia falta publica por el camino de siempre, y si lo congelado ya no
 * se puede publicar, la solicitud queda aprobada sin publicar y dice por que. A quien hay que avisar se le avisa con un
 * pedido de notificaciones que sale con el commit.
 */
@Service
@Transactional(readOnly = true)
public class SolicitudServiceImpl implements SolicitudService {

    private static final String NO_ENCONTRADA = "Solicitud no encontrada.";
    private static final String RECURSO = "SOLICITUD";
    private static final String PROCESO = "procesoId";
    private static final String MARKDOWN = "markdown";
    /** El lado de la diferencia que es lo congelado; el otro es el numero de la vigente, o ninguna. */
    private static final String CONGELADO = "solicitud";
    private static final String SIN_VERSION = "ninguna";
    private static final Sort RECIENTES = Sort.by(Sort.Direction.DESC, "fechaCreacion", "id");
    /** Los aprobadores que una solicitud guarda, como mucho: el mismo tope que pone la base. */
    static final int MAXIMO_DE_APROBADORES = 200;

    private final SolicitudPublicacionRepository solicitudRepository;
    private final AprobacionRepository aprobacionRepository;
    private final ProcesoRepository procesoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProcesoService procesoService;
    private final PoliticaDePublicacionService politica;
    private final VersionService versionService;
    private final GuardadoDelModeloService guardado;
    private final Permisos permisos;
    private final Markdown markdown;
    private final Auditoria auditoria;
    private final ApplicationEventPublisher eventos;
    private final JsonMapper json;
    private final Clock reloj;

    @SuppressWarnings("java:S107") // las piezas de la solicitud: sus tablas, la publicacion, los permisos y los avisos
    public SolicitudServiceImpl(SolicitudPublicacionRepository solicitudRepository,
            AprobacionRepository aprobacionRepository, ProcesoRepository procesoRepository,
            UsuarioRepository usuarioRepository, ProcesoService procesoService, PoliticaDePublicacionService politica,
            VersionService versionService, GuardadoDelModeloService guardado, Permisos permisos, Markdown markdown,
            Auditoria auditoria, ApplicationEventPublisher eventos, JsonMapper json, Clock reloj) {
        this.solicitudRepository = solicitudRepository;
        this.aprobacionRepository = aprobacionRepository;
        this.procesoRepository = procesoRepository;
        this.usuarioRepository = usuarioRepository;
        this.procesoService = procesoService;
        this.politica = politica;
        this.versionService = versionService;
        this.guardado = guardado;
        this.permisos = permisos;
        this.markdown = markdown;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.json = json;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public SolicitudResponse pedir(Long empresaId, Long usuarioId, Long procesoId, String texto) {
        // Se piden de a una por proceso, y el borrador no cambia mientras se congela
        Proceso proceso = procesoRepository.bloquear(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
        permisos.exigir(empresaId, usuarioId, Accion.PUBLICAR, Recurso.proceso(procesoId));
        ReglaDeAprobacion regla = politica.reglaPara(empresaId, procesoId, usuarioId)
                .orElseThrow(() -> new ReglaNegocioException("Esta organización publica directo: el proceso se "
                        + "publica sin pedir una revisión."));
        if (solicitudRepository.existsByProcesoIdAndEmpresaIdAndEstado(procesoId, empresaId,
                EstadoSolicitud.ABIERTA)) {
            throw new ReglaNegocioException("El proceso ya tiene una solicitud de revisión abierta.");
        }
        if (regla.aprobadores().size() < regla.necesarias()) {
            throw new ReglaNegocioException("La política pide " + regla.necesarias() + " aprobaciones y solo "
                    + regla.aprobadores().size() + " personas pueden aprobar este proceso sin contar a quien pide la "
                    + "revisión.");
        }
        // Un rol no tiene tope de personas; una solicitud si, y lo dice antes de que lo diga la base
        if (regla.aprobadores().size() > MAXIMO_DE_APROBADORES) {
            throw new ReglaNegocioException("Una solicitud admite hasta " + MAXIMO_DE_APROBADORES + " aprobadores y "
                    + regla.aprobadores().size() + " personas pueden aprobar este proceso: la política tiene que "
                    + "tomarlos de una lista o de un rol de proceso más chico.");
        }
        BorradorCongelado congelado = procesoService.congelar(empresaId, procesoId);
        RevisionBorradorResponse revision = guardado.puntoDeGuardado(empresaId, usuarioId, procesoId,
                MotivoDeRevision.SOLICITUD);
        String cuerpo = limpio(texto);
        Instantanea instantanea = congelado.instantanea();
        SolicitudPublicacion solicitud = solicitudRepository.saveAndFlush(SolicitudPublicacion.builder()
                .empresa(proceso.getEmpresa()).procesoId(procesoId).revisionId(revision.id())
                .solicitanteId(usuarioId).markdown(cuerpo).html(markdown.aHtml(cuerpo)).numero(congelado.numero())
                .versionBase(congelado.versionBase()).huella(instantanea.huella()).definicion(instantanea.definicion())
                .modeloBpmn(instantanea.bpmn()).aprobacionesNecesarias(regla.necesarias())
                .aprobadores(new ArrayList<>(regla.aprobadores())).build());
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put(PROCESO, procesoId);
        detalle.put("version", congelado.numero());
        detalle.put("aprobacionesNecesarias", regla.necesarias());
        detalle.put("aprobadores", regla.aprobadores().size());
        auditoria.registrar(empresaId, usuarioId, "PUBLICACION_SOLICITADA", RECURSO, solicitud.getId(), detalle);
        avisar(TipoDeNotificacion.APROBACION_PEDIDA, usuarioId, solicitud, solicitud.getAprobadores());
        return respuesta(solicitud, true);
    }

    @Override
    public PageResponse<SolicitudResponse> listarDelProceso(Long empresaId, Long procesoId, EstadoSolicitud estado,
            Pageable pagina) {
        if (!procesoRepository.existsByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)) {
            throw new RecursoNoEncontradoException("Proceso no encontrado.");
        }
        Pageable ordenada = PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(), RECIENTES);
        Page<SolicitudPublicacion> solicitudes = estado == null
                ? solicitudRepository.findAllByProcesoIdAndEmpresaId(procesoId, empresaId, ordenada)
                : solicitudRepository.findAllByProcesoIdAndEmpresaIdAndEstado(procesoId, empresaId, estado, ordenada);
        return pagina(empresaId, solicitudes);
    }

    @Override
    public PageResponse<SolicitudResponse> listar(Long empresaId, Long usuarioId, RolAcceso rol, Filtro filtro,
            Pageable pagina) {
        Pageable ordenada = PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(), RECIENTES);
        List<Long> soloEstas = null;
        if (filtro.pendientesDeMi()) {
            soloEstas = solicitudRepository.pendientesDe(empresaId, usuarioId);
            if (soloEstas.isEmpty()) {
                return PageResponse.from(Page.empty(ordenada));
            }
        }
        Visibilidad visible = permisos.visibilidad(new Actor(usuarioId, empresaId, rol));
        Specification<Proceso> visibles = visible.todo() ? null : ProcesoSpecifications.visibles(
                visible.carpetasOcultas(), visible.procesosOcultos(), visible.procesosVisibles());
        return pagina(empresaId, solicitudRepository.findAll(SolicitudSpecifications.deLosProcesos(empresaId,
                visibles, filtro.estado(), soloEstas), ordenada));
    }

    @Override
    public SolicitudResponse obtener(Long empresaId, Long usuarioId, Long solicitudId) {
        SolicitudPublicacion solicitud = solicitudRepository.findByIdAndEmpresaId(solicitudId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADA));
        exigirProcesoVisible(empresaId, usuarioId, solicitud);
        return respuesta(solicitud, true);
    }

    @Override
    @Transactional
    public SolicitudResponse decidir(Long empresaId, Long usuarioId, Long solicitudId, Decision decision,
            String texto) {
        SolicitudPublicacion solicitud = bloquearAbierta(empresaId, usuarioId, solicitudId);
        if (usuarioId.equals(solicitud.getSolicitanteId())) {
            throw new SinPermisoException("Quien pide una revisión no la aprueba.");
        }
        if (!solicitud.getAprobadores().contains(usuarioId)) {
            throw new SinPermisoException("No estás entre quienes aprueban esta solicitud.");
        }
        if (aprobacionRepository.existsByEmpresaIdAndSolicitudIdAndAprobadorId(empresaId, solicitudId, usuarioId)) {
            throw new ReglaNegocioException("Ya decidiste sobre esta solicitud.");
        }
        permisos.exigir(empresaId, usuarioId, Accion.APROBAR, Recurso.proceso(solicitud.getProcesoId()));
        String cuerpo = limpio(texto);
        if (decision != Decision.APROBADA && cuerpo.isEmpty()) {
            throw new SolicitudInvalidaException("Pedir cambios o rechazar una solicitud siempre dice por qué.",
                    Map.of(MARKDOWN, "Escribe qué hay que cambiar o por qué se rechaza."));
        }
        LocalDateTime ahora = ahora();
        aprobacionRepository.saveAndFlush(Aprobacion.builder().empresa(solicitud.getEmpresa())
                .solicitudId(solicitudId).aprobadorId(usuarioId).decision(decision).markdown(cuerpo)
                .html(markdown.aHtml(cuerpo)).fecha(ahora).build());
        auditoria.registrar(empresaId, usuarioId, "SOLICITUD_" + decision.name(), RECURSO, solicitudId,
                Map.of(PROCESO, solicitud.getProcesoId()));
        if (decision == Decision.APROBADA) {
            aprobar(solicitud, usuarioId, ahora);
        } else if (decision == Decision.CAMBIOS_PEDIDOS) {
            cerrar(solicitud, EstadoSolicitud.CAMBIOS_PEDIDOS, usuarioId, ahora, TipoDeNotificacion.CAMBIOS_PEDIDOS);
        } else {
            cerrar(solicitud, EstadoSolicitud.RECHAZADA, usuarioId, ahora, TipoDeNotificacion.SOLICITUD_RECHAZADA);
        }
        return respuesta(solicitudRepository.saveAndFlush(solicitud), true);
    }

    /**
     * Con la ultima que hacia falta se publica lo congelado; si ya no se puede, porque la vigente cambio o el
     * diagnostico ya no esta limpio, la solicitud queda aprobada sin publicar y guarda por que.
     */
    private void aprobar(SolicitudPublicacion solicitud, Long usuarioId, LocalDateTime ahora) {
        Long empresaId = solicitud.getEmpresa().getId();
        long aprobaciones = aprobacionRepository.countByEmpresaIdAndSolicitudIdAndDecision(empresaId,
                solicitud.getId(), Decision.APROBADA);
        if (aprobaciones < solicitud.getAprobacionesNecesarias()) {
            avisar(TipoDeNotificacion.SOLICITUD_APROBADA, usuarioId, solicitud, List.of(solicitud.getSolicitanteId()));
            return;
        }
        Optional<String> impedimento = procesoService.publicarCongelado(empresaId, solicitud.getProcesoId(),
                usuarioId, congelado(solicitud), solicitud.getId());
        if (impedimento.isEmpty()) {
            solicitud.setVersionPublicada(solicitud.getNumero());
            cerrar(solicitud, EstadoSolicitud.PUBLICADA, usuarioId, ahora, TipoDeNotificacion.SOLICITUD_PUBLICADA);
            return;
        }
        solicitud.setMotivo(impedimento.get());
        cerrar(solicitud, EstadoSolicitud.APROBADA_SIN_PUBLICAR, usuarioId, ahora,
                TipoDeNotificacion.SOLICITUD_SIN_PUBLICAR);
        auditoria.registrar(empresaId, usuarioId, "SOLICITUD_SIN_PUBLICAR", RECURSO, solicitud.getId(),
                Map.of(PROCESO, solicitud.getProcesoId(), "motivo", impedimento.get()));
    }

    @Override
    @Transactional
    public SolicitudResponse retirar(Long empresaId, Long usuarioId, Long solicitudId) {
        SolicitudPublicacion solicitud = bloquearAbierta(empresaId, usuarioId, solicitudId);
        boolean propia = usuarioId.equals(solicitud.getSolicitanteId());
        // Una que quedo sin quien la siga la cierra quien administra el proceso
        if (!propia) {
            permisos.exigir(empresaId, usuarioId, Accion.ADMINISTRAR, Recurso.proceso(solicitud.getProcesoId()));
        }
        cerrar(solicitud, EstadoSolicitud.RETIRADA, usuarioId, ahora(), null);
        auditoria.registrar(empresaId, usuarioId, "SOLICITUD_RETIRADA", RECURSO, solicitudId,
                Map.of(PROCESO, solicitud.getProcesoId(), "propia", propia));
        return respuesta(solicitudRepository.saveAndFlush(solicitud), true);
    }

    /**
     * PR 35: quien se anonimiza ya no sigue sus solicitudes: las abiertas se retiran, y dicen por que. Lo que decidio
     * como aprobador se queda: es lo que se dijo ese dia, y su nombre ya sale con el seudonimo.
     */
    @EventListener
    @Transactional
    public void alAnonimizarse(UsuarioAnonimizado anonimizado) {
        retirarPorElSistema(anonimizado.empresaId(),
                solicitudRepository.findAllByEmpresaIdAndSolicitanteIdAndEstado(anonimizado.empresaId(),
                        anonimizado.usuarioId(), EstadoSolicitud.ABIERTA),
                "Quien pidió la revisión ya no está en la organización.", anonimizado.usuarioId());
    }

    /**
     * Un proceso dado de baja ya no se publica: su solicitud abierta se retira en la misma transaccion, y dice por que.
     * Nadie la cierra en persona; la bitacora la anota como del sistema.
     */
    @EventListener
    @Transactional
    public void alDarseDeBaja(ProcesoEliminado eliminado) {
        retirarPorElSistema(eliminado.empresaId(),
                solicitudRepository.findAllByEmpresaIdAndProcesoIdAndEstado(eliminado.empresaId(),
                        eliminado.procesoId(), EstadoSolicitud.ABIERTA),
                "El proceso se dio de baja.", null);
    }

    private void retirarPorElSistema(Long empresaId, List<SolicitudPublicacion> abiertas, String motivo,
            Long cerradaPor) {
        LocalDateTime ahora = ahora();
        for (SolicitudPublicacion solicitud : abiertas) {
            solicitud.setMotivo(motivo);
            cerrar(solicitud, EstadoSolicitud.RETIRADA, cerradaPor, ahora, null);
            solicitudRepository.save(solicitud);
            auditoria.registrar(empresaId, null, "SOLICITUD_RETIRADA", RECURSO, solicitud.getId(),
                    Map.of(PROCESO, solicitud.getProcesoId(), "propia", false));
        }
    }

    /** Cerrar es dejar de recibir decisiones; quien la pidio se entera si hay un aviso para eso. */
    private void cerrar(SolicitudPublicacion solicitud, EstadoSolicitud estado, Long usuarioId, LocalDateTime ahora,
            TipoDeNotificacion aviso) {
        solicitud.setEstado(estado);
        solicitud.setCerradaPor(usuarioId);
        solicitud.setCerradaEn(ahora);
        if (aviso != null) {
            avisar(aviso, usuarioId, solicitud, List.of(solicitud.getSolicitanteId()));
        }
    }

    /** La solicitud, tomada para decidir sobre ella, si su proceso sigue en pie, quien pide lo ve y sigue abierta. */
    private SolicitudPublicacion bloquearAbierta(Long empresaId, Long usuarioId, Long solicitudId) {
        SolicitudPublicacion solicitud = solicitudRepository.bloquear(solicitudId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADA));
        exigirProcesoVisible(empresaId, usuarioId, solicitud);
        if (!solicitud.abierta()) {
            throw new ReglaNegocioException("La solicitud ya está cerrada: " + solicitud.getEstado() + ".");
        }
        return solicitud;
    }

    /** La solicitud de un proceso dado de baja, o que quien pide no ve, no existe para el. */
    private void exigirProcesoVisible(Long empresaId, Long usuarioId, SolicitudPublicacion solicitud) {
        if (!procesoRepository.existsByIdAndEmpresaIdAndActivoTrue(solicitud.getProcesoId(), empresaId)) {
            throw new RecursoNoEncontradoException(NO_ENCONTRADA);
        }
        permisos.exigir(empresaId, usuarioId, Accion.VER, Recurso.proceso(solicitud.getProcesoId()));
    }

    /** Un pedido de notificaciones que sale con este commit, si queda alguien a quien avisar. */
    private void avisar(TipoDeNotificacion tipo, Long autorId, SolicitudPublicacion solicitud,
            List<Long> destinatarios) {
        List<Long> otros = destinatarios.stream().filter(id -> !id.equals(autorId)).toList();
        if (!otros.isEmpty()) {
            eventos.publishEvent(new NotificacionPedida(solicitud.getEmpresa().getId(), tipo, autorId,
                    solicitud.getProcesoId(), solicitud.getId(), otros));
        }
    }

    private static BorradorCongelado congelado(SolicitudPublicacion solicitud) {
        return new BorradorCongelado(solicitud.getNumero(), solicitud.getVersionBase(),
                new Instantanea(solicitud.getDefinicion(), solicitud.getHuella(), solicitud.getModeloBpmn()));
    }

    private PageResponse<SolicitudResponse> pagina(Long empresaId, Page<SolicitudPublicacion> solicitudes) {
        Partes partes = partes(empresaId, solicitudes.getContent());
        return PageResponse.from(solicitudes.map(solicitud -> respuesta(solicitud, partes, null)));
    }

    private SolicitudResponse respuesta(SolicitudPublicacion solicitud, boolean conDiferencia) {
        return respuesta(solicitud, partes(solicitud.getEmpresa().getId(), List.of(solicitud)),
                conDiferencia ? diferencia(solicitud) : null);
    }

    /** Lo que una pagina de solicitudes necesita de otras tablas, leido de una vez y no por fila. */
    private record Partes(Map<Long, List<Aprobacion>> decisiones, Map<Long, String> nombres,
            Map<Long, String> procesos) {
    }

    private Partes partes(Long empresaId, List<SolicitudPublicacion> solicitudes) {
        if (solicitudes.isEmpty()) {
            return new Partes(Map.of(), Map.of(), Map.of());
        }
        Map<Long, List<Aprobacion>> decisiones = aprobacionRepository
                .findAllByEmpresaIdAndSolicitudIdInOrderByFechaAscIdAsc(empresaId,
                        solicitudes.stream().map(SolicitudPublicacion::getId).toList())
                .stream().collect(Collectors.groupingBy(Aprobacion::getSolicitudId));
        Set<Long> personas = new HashSet<>();
        Set<Long> procesos = new HashSet<>();
        for (SolicitudPublicacion solicitud : solicitudes) {
            personas.add(solicitud.getSolicitanteId());
            personas.addAll(solicitud.getAprobadores());
            if (solicitud.getCerradaPor() != null) {
                personas.add(solicitud.getCerradaPor());
            }
            procesos.add(solicitud.getProcesoId());
        }
        return new Partes(decisiones, usuarioRepository.findAllByEmpresaIdAndIdIn(empresaId, personas).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNombre)),
                procesoRepository.findAllByIdInAndEmpresaId(procesos, empresaId).stream()
                        .collect(Collectors.toMap(Proceso::getId, Proceso::getNombre)));
    }

    private static SolicitudResponse respuesta(SolicitudPublicacion solicitud, Partes partes,
            ComparacionResponse diferencia) {
        List<Aprobacion> decisiones = partes.decisiones().getOrDefault(solicitud.getId(), List.of());
        int aprobadas = (int) decisiones.stream().filter(una -> una.getDecision() == Decision.APROBADA).count();
        return new SolicitudResponse(solicitud.getId(), solicitud.getProcesoId(),
                partes.procesos().get(solicitud.getProcesoId()), solicitud.getEstado(),
                persona(solicitud.getSolicitanteId(), partes), solicitud.getMarkdown(), solicitud.getHtml(),
                solicitud.getNumero(), solicitud.getVersionBase(), solicitud.getRevisionId(),
                solicitud.getAprobacionesNecesarias(), aprobadas,
                solicitud.getAprobadores().stream().map(id -> persona(id, partes)).toList(),
                decisiones.stream().map(una -> new DecisionResponse(una.getId(), persona(una.getAprobadorId(), partes),
                        una.getDecision(), una.getMarkdown(), una.getHtml(), una.getFecha())).toList(),
                solicitud.getMotivo(), persona(solicitud.getCerradaPor(), partes), solicitud.getCerradaEn(),
                solicitud.getVersionPublicada(), solicitud.getFechaCreacion(), diferencia);
    }

    private static PersonaResponse persona(Long id, Partes partes) {
        return id == null ? null : new PersonaResponse(id, partes.nombres().get(id));
    }

    /**
     * PR 57: la misma diferencia que compara dos versiones, entre la vigente de cuando se pidio y lo congelado. Sin
     * vigente, contra un diagrama vacio: cada elemento que trae es nuevo.
     */
    private ComparacionResponse diferencia(SolicitudPublicacion solicitud) {
        DiagramaResponse congelado = json.readValue(solicitud.getDefinicion(), DiagramaResponse.class);
        Integer base = solicitud.getVersionBase();
        if (base == null) {
            return ComparacionDeDiagramas.comparar(SIN_VERSION, vacio(congelado), CONGELADO, congelado, json);
        }
        DiagramaResponse vigente = json.readValue(versionService.definicion(solicitud.getEmpresa().getId(),
                solicitud.getProcesoId(), base), DiagramaResponse.class);
        return ComparacionDeDiagramas.comparar(String.valueOf(base), vigente, CONGELADO, congelado, json);
    }

    private static DiagramaResponse vacio(DiagramaResponse diagrama) {
        return new DiagramaResponse(diagrama.proceso(), false, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static String limpio(String texto) {
        return texto == null ? "" : texto.strip();
    }

    /** PostgreSQL guarda microsegundos: lo que se escribe es lo que se leera despues. */
    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
    }
}
