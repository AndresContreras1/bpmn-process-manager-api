package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SinPermisoException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.markdown.Markdown;
import com.facimus.procesos.common.model.Accion;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.notificaciones.NotificacionPedida;
import com.facimus.procesos.common.notificaciones.TipoDeNotificacion;
import com.facimus.procesos.gestion.event.ProcesoEliminado;
import com.facimus.procesos.gestion.event.UsuarioAnonimizado;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
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
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.PersonaResponse;
import com.facimus.procesos.modelado.dto.response.RevisionBorradorResponse;
import com.facimus.procesos.modelado.dto.response.SolicitudResponse;
import com.facimus.procesos.modelado.model.Aprobacion;
import com.facimus.procesos.modelado.model.Decision;
import com.facimus.procesos.modelado.model.EstadoSolicitud;
import com.facimus.procesos.modelado.model.MotivoDeRevision;
import com.facimus.procesos.modelado.model.SolicitudPublicacion;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.repository.AprobacionRepository;
import com.facimus.procesos.modelado.repository.SolicitudPublicacionRepository;
import com.facimus.procesos.modelado.service.DiagramaArmado;
import com.facimus.procesos.modelado.service.GuardadoDelModeloService;
import com.facimus.procesos.modelado.service.SolicitudService.Filtro;

import tools.jackson.databind.json.JsonMapper;

/** PR 64: las solicitudes de revision, con la base y la publicacion de mentira, y el Markdown y la diferencia de verdad. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SolicitudServiceImplTest {

    private static final Long EMPRESA = 1L;
    private static final Long PROCESO = 3L;
    private static final Long SOLICITUD = 40L;
    private static final Long ANA = 2L;
    private static final Long BETO = 5L;
    private static final Long CARLA = 6L;
    private static final Long DORA = 7L;
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 12, 10, 0);
    private static final String HUELLA = "9f2c4a6b8d0e1f23456789abcdef0123456789abcdef0123456789abcdef0123";
    private static final String BPMN = "<bpmn:definitions/>";

    @Mock
    private SolicitudPublicacionRepository solicitudRepository;
    @Mock
    private AprobacionRepository aprobacionRepository;
    @Mock
    private ProcesoRepository procesoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ProcesoService procesoService;
    @Mock
    private PoliticaDePublicacionService politica;
    @Mock
    private VersionService versionService;
    @Mock
    private GuardadoDelModeloService guardado;
    @Mock
    private Permisos permisos;
    @Mock
    private Auditoria auditoria;
    @Mock
    private ApplicationEventPublisher eventos;

    private final JsonMapper json = JsonMapper.builder().build();
    private SolicitudServiceImpl servicio;
    private Empresa empresa;
    private Proceso proceso;
    private String definicionDemo;
    private final List<Aprobacion> guardadas = new ArrayList<>();

    @BeforeEach
    void preparar() {
        Clock reloj = Clock.fixed(AHORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        servicio = new SolicitudServiceImpl(solicitudRepository, aprobacionRepository, procesoRepository,
                usuarioRepository, procesoService, politica, versionService, guardado, permisos, new Markdown(),
                auditoria, eventos, json, reloj);
        empresa = Empresa.builder().id(EMPRESA).build();
        proceso = Proceso.builder().id(PROCESO).empresa(empresa).nombre("Despacho").activo(true).build();
        definicionDemo = json.writeValueAsString(DiagramaArmado.demo().diagrama());
        when(procesoRepository.bloquear(PROCESO, EMPRESA)).thenReturn(Optional.of(proceso));
        when(procesoRepository.existsByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(true);
        when(procesoRepository.findAllByIdInAndEmpresaId(anyCollection(), eq(EMPRESA))).thenReturn(List.of(proceso));
        List<Usuario> personas = List.of(persona(ANA, "Ana"), persona(BETO, "Beto"), persona(CARLA, "Carla"),
                persona(DORA, "Dora"));
        when(usuarioRepository.findAllByEmpresaIdAndIdIn(eq(EMPRESA), anyCollection())).thenAnswer(llamada -> {
            Collection<Long> ids = llamada.getArgument(1);
            return personas.stream().filter(una -> ids.contains(una.getId())).toList();
        });
        when(solicitudRepository.saveAndFlush(any(SolicitudPublicacion.class))).thenAnswer(llamada -> {
            SolicitudPublicacion solicitud = llamada.getArgument(0);
            if (solicitud.getId() == null) {
                solicitud.setId(SOLICITUD);
            }
            return solicitud;
        });
        when(aprobacionRepository.saveAndFlush(any(Aprobacion.class))).thenAnswer(llamada -> {
            Aprobacion aprobacion = llamada.getArgument(0);
            aprobacion.setId(90L + guardadas.size());
            guardadas.add(aprobacion);
            return aprobacion;
        });
        when(aprobacionRepository.findAllByEmpresaIdAndSolicitudIdInOrderByFechaAscIdAsc(eq(EMPRESA),
                anyCollection())).thenReturn(guardadas);
    }

    private Usuario persona(Long id, String nombre) {
        return Usuario.builder().id(id).nombre(nombre).rolAcceso(RolAcceso.EDITOR).activo(true).empresa(empresa)
                .build();
    }

    /** Una solicitud abierta de Ana, que aprueban Beto y Carla, sobre la version 2, para publicar la 3. */
    private SolicitudPublicacion abierta(int necesarias) {
        SolicitudPublicacion solicitud = SolicitudPublicacion.builder().id(SOLICITUD).empresa(empresa)
                .procesoId(PROCESO).revisionId(31L).solicitanteId(ANA).markdown("Cambios").html("<p>Cambios</p>")
                .numero(3).versionBase(2).huella(HUELLA).definicion(definicionDemo).modeloBpmn(BPMN)
                .aprobacionesNecesarias(necesarias).aprobadores(new ArrayList<>(List.of(BETO, CARLA))).version(0L)
                .build();
        when(solicitudRepository.bloquear(SOLICITUD, EMPRESA)).thenReturn(Optional.of(solicitud));
        when(solicitudRepository.findByIdAndEmpresaId(SOLICITUD, EMPRESA)).thenReturn(Optional.of(solicitud));
        when(versionService.definicion(EMPRESA, PROCESO, 2)).thenReturn(definicionDemo);
        return solicitud;
    }

    private void congelable(Integer base) {
        when(politica.reglaPara(EMPRESA, PROCESO, ANA)).thenReturn(Optional.of(new ReglaDeAprobacion(2,
                List.of(BETO, CARLA))));
        when(procesoService.congelar(EMPRESA, PROCESO)).thenReturn(new BorradorCongelado(base == null ? 1 : base + 1,
                base, new Instantanea(definicionDemo, HUELLA, BPMN)));
        when(guardado.puntoDeGuardado(EMPRESA, ANA, PROCESO, MotivoDeRevision.SOLICITUD))
                .thenReturn(new RevisionBorradorResponse(31L, MotivoDeRevision.SOLICITUD, HUELLA, ANA, AHORA));
        if (base != null) {
            when(versionService.definicion(EMPRESA, PROCESO, base)).thenReturn(definicionDemo);
        }
    }

    private List<NotificacionPedida> avisos() {
        ArgumentCaptor<Object> publicados = ArgumentCaptor.forClass(Object.class);
        verify(eventos, atLeast(0)).publishEvent(publicados.capture());
        return publicados.getAllValues().stream().filter(NotificacionPedida.class::isInstance)
                .map(NotificacionPedida.class::cast).toList();
    }

    @Test
    @DisplayName("pedir congela el borrador en una revision y en la instantanea, fija los aprobadores y les avisa")
    void pedir() {
        congelable(2);

        SolicitudResponse respuesta = servicio.pedir(EMPRESA, ANA, PROCESO, "  Agrega el **fraude**  ");

        ArgumentCaptor<SolicitudPublicacion> guardada = ArgumentCaptor.forClass(SolicitudPublicacion.class);
        verify(solicitudRepository).saveAndFlush(guardada.capture());
        SolicitudPublicacion solicitud = guardada.getValue();
        assertThat(solicitud.getEmpresa()).isSameAs(empresa);
        assertThat(solicitud.getProcesoId()).isEqualTo(PROCESO);
        assertThat(solicitud.getRevisionId()).isEqualTo(31L);
        assertThat(solicitud.getSolicitanteId()).isEqualTo(ANA);
        assertThat(solicitud.getMarkdown()).isEqualTo("Agrega el **fraude**");
        assertThat(solicitud.getHtml()).isEqualTo("<p>Agrega el <strong>fraude</strong></p>");
        assertThat(solicitud.getNumero()).isEqualTo(3);
        assertThat(solicitud.getVersionBase()).isEqualTo(2);
        assertThat(solicitud.getHuella()).isEqualTo(HUELLA);
        assertThat(solicitud.getDefinicion()).isEqualTo(definicionDemo);
        assertThat(solicitud.getModeloBpmn()).isEqualTo(BPMN);
        assertThat(solicitud.getAprobacionesNecesarias()).isEqualTo(2);
        assertThat(solicitud.getAprobadores()).containsExactly(BETO, CARLA);
        assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.ABIERTA);
        verify(permisos).exigir(EMPRESA, ANA, Accion.PUBLICAR, Recurso.proceso(PROCESO));

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("procesoId", PROCESO);
        detalle.put("version", 3);
        detalle.put("aprobacionesNecesarias", 2);
        detalle.put("aprobadores", 2);
        verify(auditoria).registrar(EMPRESA, ANA, "PUBLICACION_SOLICITADA", "SOLICITUD", SOLICITUD, detalle);
        assertThat(avisos()).containsExactly(new NotificacionPedida(EMPRESA, TipoDeNotificacion.APROBACION_PEDIDA,
                ANA, PROCESO, SOLICITUD, List.of(BETO, CARLA)));

        assertThat(respuesta.id()).isEqualTo(SOLICITUD);
        assertThat(respuesta.proceso()).isEqualTo("Despacho");
        assertThat(respuesta.solicitante()).isEqualTo(new PersonaResponse(ANA, "Ana"));
        assertThat(respuesta.aprobadores()).containsExactly(new PersonaResponse(BETO, "Beto"),
                new PersonaResponse(CARLA, "Carla"));
        assertThat(respuesta.aprobaciones()).isZero();
        assertThat(respuesta.revisionId()).isEqualTo(31L);
        // Contra la misma version: nada cambia
        assertThat(respuesta.diferencia().de()).isEqualTo("2");
        assertThat(respuesta.diferencia().a()).isEqualTo("solicitud");
        assertThat(respuesta.diferencia().sinDiferencias()).isTrue();
    }

    @Test
    @DisplayName("la diferencia es contra la vigente del dia en que se pidio, y sin vigente contra un diagrama vacio")
    void diferencia() {
        congelable(null);

        SolicitudResponse primera = servicio.pedir(EMPRESA, ANA, PROCESO, null);

        assertThat(primera.markdown()).isEmpty();
        assertThat(primera.html()).isEmpty();
        assertThat(primera.versionBase()).isNull();
        assertThat(primera.diferencia().de()).isEqualTo("ninguna");
        assertThat(primera.diferencia().quitados()).isEmpty();
        assertThat(primera.diferencia().cambiados()).isEmpty();
        assertThat(primera.diferencia().agregados()).isNotEmpty()
                .anySatisfy(elemento -> assertThat(elemento.nombre()).isEqualTo("Receive order"));
        verify(versionService, never()).definicion(anyLong(), anyLong(), anyInt());

        SolicitudPublicacion solicitud = abierta(1);
        DiagramaArmado vigente = DiagramaArmado.demo();
        vigente.actividad(DiagramaArmado.VENTAS, "Check fraud", TipoActividad.USUARIO);
        when(versionService.definicion(EMPRESA, PROCESO, 2)).thenReturn(json.writeValueAsString(vigente.diagrama()));

        SolicitudResponse leida = servicio.obtener(EMPRESA, BETO, solicitud.getId());

        assertThat(leida.diferencia().de()).isEqualTo("2");
        assertThat(leida.diferencia().quitados()).singleElement()
                .satisfies(elemento -> assertThat(elemento.nombre()).isEqualTo("Check fraud"));
        verify(permisos).exigir(EMPRESA, BETO, Accion.VER, Recurso.proceso(PROCESO));
    }

    @Test
    @DisplayName("pedir en un proceso que no esta, o sin PUBLICAR, no congela nada")
    void pedirSinProcesoNiPermiso() {
        congelable(2);
        when(procesoRepository.bloquear(99L, EMPRESA)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.pedir(EMPRESA, ANA, 99L, null)).isInstanceOf(
                RecursoNoEncontradoException.class);

        doThrow(new SinPermisoException("No")).when(permisos).exigir(EMPRESA, ANA, Accion.PUBLICAR,
                Recurso.proceso(PROCESO));
        assertThatThrownBy(() -> servicio.pedir(EMPRESA, ANA, PROCESO, null)).isInstanceOf(SinPermisoException.class);

        verify(procesoService, never()).congelar(anyLong(), anyLong());
        verify(solicitudRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("con la tienda publicando directo, pedir una revision es un 409")
    void pedirConPublicacionDirecta() {
        when(politica.reglaPara(EMPRESA, PROCESO, ANA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.pedir(EMPRESA, ANA, PROCESO, null)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Esta organización publica directo: el proceso se publica sin pedir una revisión.");
        verify(procesoService, never()).congelar(anyLong(), anyLong());
    }

    @Test
    @DisplayName("solo una abierta por proceso: la segunda es un 409")
    void segundaAbierta() {
        congelable(2);
        when(solicitudRepository.existsByProcesoIdAndEmpresaIdAndEstado(PROCESO, EMPRESA, EstadoSolicitud.ABIERTA))
                .thenReturn(true);

        assertThatThrownBy(() -> servicio.pedir(EMPRESA, ANA, PROCESO, null)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El proceso ya tiene una solicitud de revisión abierta.");
        verify(procesoService, never()).congelar(anyLong(), anyLong());
    }

    @Test
    @DisplayName("sin tantas personas que puedan aprobar como aprobaciones pide la politica, 409; con justo esas, pasa")
    void aprobadoresSuficientes() {
        congelable(2);
        when(politica.reglaPara(EMPRESA, PROCESO, ANA)).thenReturn(Optional.of(new ReglaDeAprobacion(3,
                List.of(BETO, CARLA))));

        assertThatThrownBy(() -> servicio.pedir(EMPRESA, ANA, PROCESO, null)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La política pide 3 aprobaciones y solo 2 personas pueden aprobar este proceso sin contar "
                        + "a quien pide la revisión.");
        verify(procesoService, never()).congelar(anyLong(), anyLong());

        when(politica.reglaPara(EMPRESA, PROCESO, ANA)).thenReturn(Optional.of(new ReglaDeAprobacion(2,
                List.of(BETO, CARLA))));
        assertThat(servicio.pedir(EMPRESA, ANA, PROCESO, null).aprobacionesNecesarias()).isEqualTo(2);
    }

    @Test
    @DisplayName("una solicitud guarda hasta 200 aprobadores: con mas, 409 con la causa y sin congelar; con 200, pasa")
    void maximoDeAprobadores() {
        congelable(2);
        List<Long> demasiados = LongStream.rangeClosed(100, 300).boxed().toList();
        when(politica.reglaPara(EMPRESA, PROCESO, ANA)).thenReturn(Optional.of(new ReglaDeAprobacion(1, demasiados)));

        assertThatThrownBy(() -> servicio.pedir(EMPRESA, ANA, PROCESO, null)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Una solicitud admite hasta 200 aprobadores y 201 personas pueden aprobar este proceso: la "
                        + "política tiene que tomarlos de una lista o de un rol de proceso más chico.");
        verify(procesoService, never()).congelar(anyLong(), anyLong());

        List<Long> justos = demasiados.subList(0, SolicitudServiceImpl.MAXIMO_DE_APROBADORES);
        when(politica.reglaPara(EMPRESA, PROCESO, ANA)).thenReturn(Optional.of(new ReglaDeAprobacion(1, justos)));
        assertThat(servicio.pedir(EMPRESA, ANA, PROCESO, null).aprobadores()).hasSize(200);
    }

    @Test
    @DisplayName("lo que impide congelar (diagnostico, sin cambios) impide pedir, y no deja revision")
    void noSeCongela() {
        congelable(2);
        when(procesoService.congelar(EMPRESA, PROCESO)).thenThrow(new ReglaNegocioException("No hay cambios."));

        assertThatThrownBy(() -> servicio.pedir(EMPRESA, ANA, PROCESO, null)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage("No hay cambios.");
        verify(guardado, never()).puntoDeGuardado(anyLong(), anyLong(), anyLong(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("una aprobacion que no completa las que hacen falta no publica, y le avisa a quien pidio")
    void aprobacionParcial() {
        SolicitudPublicacion solicitud = abierta(2);
        when(aprobacionRepository.countByEmpresaIdAndSolicitudIdAndDecision(EMPRESA, SOLICITUD, Decision.APROBADA))
                .thenReturn(1L);

        SolicitudResponse respuesta = servicio.decidir(EMPRESA, BETO, SOLICITUD, Decision.APROBADA, null);

        assertThat(respuesta.estado()).isEqualTo(EstadoSolicitud.ABIERTA);
        assertThat(respuesta.aprobaciones()).isOne();
        assertThat(respuesta.decisiones()).singleElement().satisfies(decision -> {
            assertThat(decision.aprobador()).isEqualTo(new PersonaResponse(BETO, "Beto"));
            assertThat(decision.decision()).isEqualTo(Decision.APROBADA);
            assertThat(decision.markdown()).isEmpty();
            assertThat(decision.fecha()).isEqualTo(AHORA);
        });
        assertThat(solicitud.getCerradaEn()).isNull();
        verify(procesoService, never()).publicarCongelado(anyLong(), anyLong(), anyLong(), any(), anyLong());
        verify(permisos).exigir(EMPRESA, BETO, Accion.APROBAR, Recurso.proceso(PROCESO));
        verify(auditoria).registrar(EMPRESA, BETO, "SOLICITUD_APROBADA", "SOLICITUD", SOLICITUD,
                Map.of("procesoId", PROCESO));
        assertThat(avisos()).containsExactly(new NotificacionPedida(EMPRESA, TipoDeNotificacion.SOLICITUD_APROBADA,
                BETO, PROCESO, SOLICITUD, List.of(ANA)));
    }

    @Test
    @DisplayName("la aprobacion que completa las que hacen falta publica lo congelado y cierra la solicitud")
    void aprobacionQuePublica() {
        SolicitudPublicacion solicitud = abierta(2);
        when(aprobacionRepository.countByEmpresaIdAndSolicitudIdAndDecision(EMPRESA, SOLICITUD, Decision.APROBADA))
                .thenReturn(2L);
        when(procesoService.publicarCongelado(eq(EMPRESA), eq(PROCESO), eq(CARLA), any(), eq(SOLICITUD)))
                .thenReturn(Optional.empty());

        SolicitudResponse respuesta = servicio.decidir(EMPRESA, CARLA, SOLICITUD, Decision.APROBADA, "Bien");

        ArgumentCaptor<BorradorCongelado> congelado = ArgumentCaptor.forClass(BorradorCongelado.class);
        verify(procesoService).publicarCongelado(eq(EMPRESA), eq(PROCESO), eq(CARLA), congelado.capture(),
                eq(SOLICITUD));
        assertThat(congelado.getValue()).isEqualTo(new BorradorCongelado(3, 2, new Instantanea(definicionDemo,
                HUELLA, BPMN)));
        assertThat(respuesta.estado()).isEqualTo(EstadoSolicitud.PUBLICADA);
        assertThat(respuesta.versionPublicada()).isEqualTo(3);
        assertThat(respuesta.cerradaPor()).isEqualTo(new PersonaResponse(CARLA, "Carla"));
        assertThat(respuesta.cerradaEn()).isEqualTo(AHORA);
        assertThat(respuesta.motivo()).isNull();
        assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.PUBLICADA);
        assertThat(avisos()).containsExactly(new NotificacionPedida(EMPRESA, TipoDeNotificacion.SOLICITUD_PUBLICADA,
                CARLA, PROCESO, SOLICITUD, List.of(ANA)));
        verify(auditoria, never()).registrar(anyLong(), anyLong(), eq("SOLICITUD_SIN_PUBLICAR"), any(), any(), any());
    }

    @Test
    @DisplayName("si lo congelado ya no se puede publicar, la solicitud queda aprobada sin publicar y dice por que")
    void aprobadaSinPublicar() {
        SolicitudPublicacion solicitud = abierta(1);
        when(aprobacionRepository.countByEmpresaIdAndSolicitudIdAndDecision(EMPRESA, SOLICITUD, Decision.APROBADA))
                .thenReturn(1L);
        when(procesoService.publicarCongelado(eq(EMPRESA), eq(PROCESO), eq(BETO), any(), eq(SOLICITUD)))
                .thenReturn(Optional.of("La versión vigente cambió."));

        SolicitudResponse respuesta = servicio.decidir(EMPRESA, BETO, SOLICITUD, Decision.APROBADA, null);

        assertThat(respuesta.estado()).isEqualTo(EstadoSolicitud.APROBADA_SIN_PUBLICAR);
        assertThat(respuesta.motivo()).isEqualTo("La versión vigente cambió.");
        assertThat(respuesta.versionPublicada()).isNull();
        assertThat(solicitud.getCerradaPor()).isEqualTo(BETO);
        verify(auditoria).registrar(EMPRESA, BETO, "SOLICITUD_SIN_PUBLICAR", "SOLICITUD", SOLICITUD,
                Map.of("procesoId", PROCESO, "motivo", "La versión vigente cambió."));
        assertThat(avisos()).containsExactly(new NotificacionPedida(EMPRESA,
                TipoDeNotificacion.SOLICITUD_SIN_PUBLICAR, BETO, PROCESO, SOLICITUD, List.of(ANA)));
    }

    @Test
    @DisplayName("pedir cambios cierra la solicitud con el comentario saneado y le avisa a quien pidio")
    void pedirCambios() {
        SolicitudPublicacion solicitud = abierta(2);

        SolicitudResponse respuesta = servicio.decidir(EMPRESA, BETO, SOLICITUD, Decision.CAMBIOS_PEDIDOS,
                " Falta el *plazo* <script>x</script> ");

        assertThat(respuesta.estado()).isEqualTo(EstadoSolicitud.CAMBIOS_PEDIDOS);
        assertThat(solicitud.getCerradaPor()).isEqualTo(BETO);
        assertThat(solicitud.getCerradaEn()).isEqualTo(AHORA);
        assertThat(guardadas).singleElement().satisfies(aprobacion -> {
            assertThat(aprobacion.getEmpresa()).isSameAs(empresa);
            assertThat(aprobacion.getSolicitudId()).isEqualTo(SOLICITUD);
            assertThat(aprobacion.getAprobadorId()).isEqualTo(BETO);
            assertThat(aprobacion.getMarkdown()).isEqualTo("Falta el *plazo* <script>x</script>");
            assertThat(aprobacion.getHtml()).contains("<em>plazo</em>").doesNotContain("<script>");
        });
        verify(procesoService, never()).publicarCongelado(anyLong(), anyLong(), anyLong(), any(), anyLong());
        verify(auditoria).registrar(EMPRESA, BETO, "SOLICITUD_CAMBIOS_PEDIDOS", "SOLICITUD", SOLICITUD,
                Map.of("procesoId", PROCESO));
        assertThat(avisos()).containsExactly(new NotificacionPedida(EMPRESA, TipoDeNotificacion.CAMBIOS_PEDIDOS,
                BETO, PROCESO, SOLICITUD, List.of(ANA)));
    }

    @Test
    @DisplayName("rechazar cierra la solicitud y le avisa a quien pidio")
    void rechazar() {
        abierta(2);

        SolicitudResponse respuesta = servicio.decidir(EMPRESA, CARLA, SOLICITUD, Decision.RECHAZADA, "No aplica");

        assertThat(respuesta.estado()).isEqualTo(EstadoSolicitud.RECHAZADA);
        assertThat(respuesta.cerradaPor()).isEqualTo(new PersonaResponse(CARLA, "Carla"));
        verify(auditoria).registrar(EMPRESA, CARLA, "SOLICITUD_RECHAZADA", "SOLICITUD", SOLICITUD,
                Map.of("procesoId", PROCESO));
        assertThat(avisos()).containsExactly(new NotificacionPedida(EMPRESA, TipoDeNotificacion.SOLICITUD_RECHAZADA,
                CARLA, PROCESO, SOLICITUD, List.of(ANA)));
    }

    @Test
    @DisplayName("pedir cambios o rechazar sin decir por que es un 400, y no queda ninguna decision")
    void sinMotivo() {
        abierta(2);

        assertThatThrownBy(() -> servicio.decidir(EMPRESA, BETO, SOLICITUD, Decision.CAMBIOS_PEDIDOS, "   "))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> servicio.decidir(EMPRESA, BETO, SOLICITUD, Decision.RECHAZADA, null))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThat(guardadas).isEmpty();
    }

    @Test
    @DisplayName("quien pidio no decide, ni quien no esta entre los aprobadores, ni quien ya decidio")
    void quienDecide() {
        abierta(2);
        assertThatThrownBy(() -> servicio.decidir(EMPRESA, ANA, SOLICITUD, Decision.APROBADA, null))
                .isInstanceOf(SinPermisoException.class).hasMessage("Quien pide una revisión no la aprueba.");
        assertThatThrownBy(() -> servicio.decidir(EMPRESA, DORA, SOLICITUD, Decision.APROBADA, null))
                .isInstanceOf(SinPermisoException.class)
                .hasMessage("No estás entre quienes aprueban esta solicitud.");
        when(aprobacionRepository.existsByEmpresaIdAndSolicitudIdAndAprobadorId(EMPRESA, SOLICITUD, BETO))
                .thenReturn(true);
        assertThatThrownBy(() -> servicio.decidir(EMPRESA, BETO, SOLICITUD, Decision.APROBADA, null))
                .isInstanceOf(ReglaNegocioException.class).hasMessage("Ya decidiste sobre esta solicitud.");
        assertThat(guardadas).isEmpty();
    }

    @Test
    @DisplayName("decidir pide APROBAR sobre el proceso, ademas de estar entre los aprobadores")
    void decidirPideAprobar() {
        abierta(2);
        doThrow(new SinPermisoException("No")).when(permisos).exigir(EMPRESA, BETO, Accion.APROBAR,
                Recurso.proceso(PROCESO));

        assertThatThrownBy(() -> servicio.decidir(EMPRESA, BETO, SOLICITUD, Decision.APROBADA, null))
                .isInstanceOf(SinPermisoException.class);
        assertThat(guardadas).isEmpty();
    }

    @Test
    @DisplayName("una solicitud cerrada no recibe decisiones ni se retira: 409")
    void cerrada() {
        SolicitudPublicacion solicitud = abierta(2);
        solicitud.setEstado(EstadoSolicitud.RECHAZADA);

        assertThatThrownBy(() -> servicio.decidir(EMPRESA, BETO, SOLICITUD, Decision.APROBADA, null))
                .isInstanceOf(ReglaNegocioException.class).hasMessage("La solicitud ya está cerrada: RECHAZADA.");
        assertThatThrownBy(() -> servicio.retirar(EMPRESA, ANA, SOLICITUD)).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("una solicitud que no esta, de un proceso dado de baja o que no se ve responde 404")
    void noEncontrada() {
        abierta(2);
        assertThatThrownBy(() -> servicio.decidir(EMPRESA, BETO, 99L, Decision.APROBADA, null))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.obtener(EMPRESA, BETO, 99L)).isInstanceOf(RecursoNoEncontradoException.class);

        doThrow(new RecursoNoEncontradoException("Proceso no encontrado.")).when(permisos).exigir(EMPRESA, DORA,
                Accion.VER, Recurso.proceso(PROCESO));
        assertThatThrownBy(() -> servicio.obtener(EMPRESA, DORA, SOLICITUD))
                .isInstanceOf(RecursoNoEncontradoException.class);

        when(procesoRepository.existsByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(false);
        assertThatThrownBy(() -> servicio.decidir(EMPRESA, BETO, SOLICITUD, Decision.APROBADA, null))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Solicitud no encontrada.");
        assertThatThrownBy(() -> servicio.obtener(EMPRESA, BETO, SOLICITUD))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(permisos, never()).exigir(EMPRESA, BETO, Accion.VER, Recurso.proceso(PROCESO));
    }

    @Test
    @DisplayName("quien pidio la retira sin mas; otra persona necesita administrar el proceso")
    void retirar() {
        SolicitudPublicacion solicitud = abierta(2);

        SolicitudResponse propia = servicio.retirar(EMPRESA, ANA, SOLICITUD);

        assertThat(propia.estado()).isEqualTo(EstadoSolicitud.RETIRADA);
        assertThat(propia.cerradaPor()).isEqualTo(new PersonaResponse(ANA, "Ana"));
        assertThat(propia.cerradaEn()).isEqualTo(AHORA);
        verify(permisos, never()).exigir(EMPRESA, ANA, Accion.ADMINISTRAR, Recurso.proceso(PROCESO));
        verify(auditoria).registrar(EMPRESA, ANA, "SOLICITUD_RETIRADA", "SOLICITUD", SOLICITUD,
                Map.of("procesoId", PROCESO, "propia", true));
        assertThat(avisos()).isEmpty();

        solicitud.setEstado(EstadoSolicitud.ABIERTA);
        doThrow(new SinPermisoException("No")).when(permisos).exigir(EMPRESA, BETO, Accion.ADMINISTRAR,
                Recurso.proceso(PROCESO));
        assertThatThrownBy(() -> servicio.retirar(EMPRESA, BETO, SOLICITUD)).isInstanceOf(SinPermisoException.class);
        assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.ABIERTA);

        servicio.retirar(EMPRESA, DORA, SOLICITUD);
        assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.RETIRADA);
        assertThat(solicitud.getCerradaPor()).isEqualTo(DORA);
        verify(auditoria).registrar(EMPRESA, DORA, "SOLICITUD_RETIRADA", "SOLICITUD", SOLICITUD,
                Map.of("procesoId", PROCESO, "propia", false));
    }

    @Test
    @DisplayName("al anonimizar a alguien, sus solicitudes abiertas se retiran y dicen por que")
    void anonimizar() {
        SolicitudPublicacion solicitud = abierta(2);
        when(solicitudRepository.findAllByEmpresaIdAndSolicitanteIdAndEstado(EMPRESA, ANA, EstadoSolicitud.ABIERTA))
                .thenReturn(List.of(solicitud));

        servicio.alAnonimizarse(new UsuarioAnonimizado(EMPRESA, ANA, "Ana", "Persona anonimizada 2"));

        assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.RETIRADA);
        assertThat(solicitud.getMotivo()).isEqualTo("Quien pidió la revisión ya no está en la organización.");
        assertThat(solicitud.getCerradaPor()).isEqualTo(ANA);
        assertThat(solicitud.getCerradaEn()).isEqualTo(AHORA);
        verify(solicitudRepository).save(solicitud);
        verify(auditoria).registrar(EMPRESA, null, "SOLICITUD_RETIRADA", "SOLICITUD", SOLICITUD,
                Map.of("procesoId", PROCESO, "propia", false));
    }

    @Test
    @DisplayName("al dar de baja un proceso, su solicitud abierta se retira, dice por que y la anota el sistema")
    void procesoDadoDeBaja() {
        SolicitudPublicacion solicitud = abierta(2);
        when(solicitudRepository.findAllByEmpresaIdAndProcesoIdAndEstado(EMPRESA, PROCESO, EstadoSolicitud.ABIERTA))
                .thenReturn(List.of(solicitud));

        servicio.alDarseDeBaja(new ProcesoEliminado(EMPRESA, PROCESO));

        assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.RETIRADA);
        assertThat(solicitud.getMotivo()).isEqualTo("El proceso se dio de baja.");
        assertThat(solicitud.getCerradaPor()).isNull();
        assertThat(solicitud.getCerradaEn()).isEqualTo(AHORA);
        verify(solicitudRepository).save(solicitud);
        verify(auditoria).registrar(EMPRESA, null, "SOLICITUD_RETIRADA", "SOLICITUD", SOLICITUD,
                Map.of("procesoId", PROCESO, "propia", false));
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("las de un proceso salen de la mas nueva a la mas vieja, filtradas por estado y sin diferencia")
    void listarDelProceso() {
        SolicitudPublicacion solicitud = abierta(2);
        Pageable esperada = PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "fechaCreacion", "id"));
        when(solicitudRepository.findAllByProcesoIdAndEmpresaId(PROCESO, EMPRESA, esperada))
                .thenReturn(new PageImpl<>(List.of(solicitud), esperada, 6));
        when(solicitudRepository.findAllByProcesoIdAndEmpresaIdAndEstado(PROCESO, EMPRESA, EstadoSolicitud.ABIERTA,
                esperada)).thenReturn(new PageImpl<>(List.of(), esperada, 5));

        PageResponse<SolicitudResponse> todas = servicio.listarDelProceso(EMPRESA, PROCESO, null,
                PageRequest.of(1, 5));
        PageResponse<SolicitudResponse> abiertas = servicio.listarDelProceso(EMPRESA, PROCESO,
                EstadoSolicitud.ABIERTA, PageRequest.of(1, 5));

        assertThat(todas.content()).singleElement().satisfies(una -> {
            assertThat(una.id()).isEqualTo(SOLICITUD);
            assertThat(una.diferencia()).isNull();
            assertThat(una.proceso()).isEqualTo("Despacho");
        });
        assertThat(abiertas.content()).isEmpty();
        verify(versionService, never()).definicion(anyLong(), anyLong(), anyInt());

        when(procesoRepository.existsByIdAndEmpresaIdAndActivoTrue(99L, EMPRESA)).thenReturn(false);
        Pageable primera = PageRequest.of(0, 5);
        assertThatThrownBy(() -> servicio.listarDelProceso(EMPRESA, 99L, null, primera))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("la bandeja por aprobar son las abiertas que esperan mi decision; sin ninguna, ni se consulta")
    @SuppressWarnings("unchecked")
    void bandeja() {
        SolicitudPublicacion solicitud = abierta(2);
        when(permisos.visibilidad(new Actor(BETO, EMPRESA, RolAcceso.EDITOR))).thenReturn(Visibilidad.TODO);
        // Carla no ve lo de una carpeta: la consulta lo filtra con las reglas de los procesos visibles
        when(permisos.visibilidad(new Actor(CARLA, EMPRESA, RolAcceso.EDITOR))).thenReturn(new Visibilidad(false,
                Set.of(9L), Set.of(), Set.of()));
        when(solicitudRepository.pendientesDe(EMPRESA, BETO)).thenReturn(List.of(SOLICITUD));
        when(solicitudRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(solicitud)));

        PageResponse<SolicitudResponse> mias = servicio.listar(EMPRESA, BETO, RolAcceso.EDITOR,
                new Filtro(true, null), PageRequest.of(0, 20));

        assertThat(mias.content()).extracting(SolicitudResponse::id).containsExactly(SOLICITUD);
        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(solicitudRepository).findAll(any(Specification.class), pagina.capture());
        assertThat(pagina.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "fechaCreacion", "id"));

        when(solicitudRepository.pendientesDe(EMPRESA, CARLA)).thenReturn(List.of());
        PageResponse<SolicitudResponse> vacia = servicio.listar(EMPRESA, CARLA, RolAcceso.EDITOR,
                new Filtro(true, null), PageRequest.of(0, 20));
        assertThat(vacia.content()).isEmpty();
        assertThat(vacia.totalElements()).isZero();
        verify(permisos, never()).visibilidad(new Actor(CARLA, EMPRESA, RolAcceso.EDITOR));

        servicio.listar(EMPRESA, CARLA, RolAcceso.EDITOR, new Filtro(false, EstadoSolicitud.ABIERTA),
                PageRequest.of(0, 20));
        verify(solicitudRepository).pendientesDe(EMPRESA, CARLA);
        verify(permisos).visibilidad(new Actor(CARLA, EMPRESA, RolAcceso.EDITOR));
    }

    @Test
    @DisplayName("la respuesta cuenta solo las aprobaciones, y nombra a cada persona")
    void respuestaCuentaAprobaciones() {
        SolicitudPublicacion solicitud = abierta(2);
        guardadas.add(Aprobacion.builder().id(1L).solicitudId(SOLICITUD).aprobadorId(BETO)
                .decision(Decision.APROBADA).markdown("").html("").fecha(AHORA).build());
        guardadas.add(Aprobacion.builder().id(2L).solicitudId(SOLICITUD).aprobadorId(CARLA)
                .decision(Decision.RECHAZADA).markdown("No").html("<p>No</p>").fecha(AHORA).build());
        solicitud.setCerradaPor(CARLA);

        SolicitudResponse respuesta = servicio.obtener(EMPRESA, BETO, SOLICITUD);

        assertThat(respuesta.aprobaciones()).isOne();
        assertThat(respuesta.decisiones()).extracting(una -> una.aprobador().nombre()).containsExactly("Beto",
                "Carla");
        assertThat(respuesta.cerradaPor()).isEqualTo(new PersonaResponse(CARLA, "Carla"));
        assertThat(respuesta.numero()).isEqualTo(3);
        assertThat(respuesta.markdown()).isEqualTo("Cambios");
        assertThat(respuesta.html()).isEqualTo("<p>Cambios</p>");
    }

    @Test
    @DisplayName("el diagrama de prueba se lee de vuelta: lo congelado es un diagrama de verdad")
    void definicionLegible() {
        assertThat(json.readValue(definicionDemo, DiagramaResponse.class).actividades()).isNotEmpty();
    }
}
