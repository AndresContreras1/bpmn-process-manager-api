package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.facimus.procesos.common.ConflictoDeVersionException;
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
import com.facimus.procesos.gestion.event.UsuarioAnonimizado;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.model.VersionProceso;
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
import com.facimus.procesos.modelado.service.ComentarioService.Filtro;
import com.facimus.procesos.modelado.service.ComentarioService.Nuevo;

/** PR 63: los hilos de comentarios, con la base de mentira y el Markdown de verdad. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ComentarioServiceImplTest {

    private static final Long EMPRESA = 1L;
    private static final Long PROCESO = 3L;
    private static final Long ANA = 2L;
    private static final Long BETO = 5L;
    private static final Long CARLA = 6L;
    /** Una persona que no puede ver el proceso. */
    private static final Long DORA = 7L;
    /** Una persona desactivada. */
    private static final Long ELIO = 8L;
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 12, 10, 0);

    @Mock
    private ComentarioRepository comentarioRepository;
    @Mock
    private ProcesoRepository procesoRepository;
    @Mock
    private VersionProcesoRepository versionRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ElementosDelProceso elementos;
    @Mock
    private Permisos permisos;
    @Mock
    private Auditoria auditoria;
    @Mock
    private ApplicationEventPublisher eventos;

    private ComentarioServiceImpl servicio;
    private Empresa empresa;
    private long siguienteId = 100;

    @BeforeEach
    void preparar() {
        Clock reloj = Clock.fixed(AHORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        servicio = new ComentarioServiceImpl(comentarioRepository, procesoRepository, versionRepository,
                usuarioRepository, elementos, permisos, new Markdown(), auditoria, eventos, reloj);
        empresa = Empresa.builder().id(EMPRESA).build();
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA))
                .thenReturn(Optional.of(Proceso.builder().id(PROCESO).empresa(empresa).build()));
        when(comentarioRepository.saveAndFlush(any(Comentario.class))).thenAnswer(llamada -> {
            Comentario comentario = llamada.getArgument(0);
            if (comentario.getId() == null) {
                comentario.setId(siguienteId++);
            }
            return comentario;
        });
        List<Usuario> personas = List.of(persona(ANA, "Ana", RolAcceso.ADMINISTRADOR, true),
                persona(BETO, "Beto", RolAcceso.EDITOR, true), persona(CARLA, "Carla", RolAcceso.SOLO_LECTURA, true),
                persona(DORA, "Dora", RolAcceso.EDITOR, true), persona(ELIO, "Elio", RolAcceso.EDITOR, false));
        when(usuarioRepository.findAllByEmpresaIdAndIdIn(eq(EMPRESA), anyCollection())).thenAnswer(llamada -> {
            Collection<Long> ids = llamada.getArgument(1);
            return personas.stream().filter(una -> ids.contains(una.getId())).toList();
        });
        when(permisos.puede(any(Actor.class), eq(Accion.VER), eq(Recurso.proceso(PROCESO))))
                .thenAnswer(llamada -> !((Actor) llamada.getArgument(0)).usuarioId().equals(DORA));
    }

    private Usuario persona(Long id, String nombre, RolAcceso rol, boolean activo) {
        return Usuario.builder().id(id).nombre(nombre).rolAcceso(rol).activo(activo).empresa(empresa).build();
    }

    private Comentario hilo(Long id, Long autor) {
        Comentario comentario = Comentario.builder().id(id).empresa(empresa).procesoId(PROCESO).autorId(autor)
                .markdown("Hola").html("<p>Hola</p>").menciones(new ArrayList<>()).version(0L).build();
        when(comentarioRepository.findByIdAndEmpresaId(id, EMPRESA)).thenReturn(Optional.of(comentario));
        return comentario;
    }

    private Comentario respuestaDe(Long id, Comentario hilo, Long autor) {
        Comentario respuesta = hilo(id, autor);
        respuesta.setPadreId(hilo.getId());
        return respuesta;
    }

    @Test
    @DisplayName("un hilo nuevo guarda el Markdown y su HTML saneado, su ancla y sus menciones, y avisa a los mencionados")
    void comentar() {
        when(elementos.existe(EMPRESA, PROCESO, "Activity_1")).thenReturn(true);

        ComentarioResponse respuesta = servicio.comentar(EMPRESA, ANA, PROCESO,
                new Nuevo("**Ojo** <script>x</script>", " Activity_1 ", null, List.of(BETO, ANA, BETO)));

        ArgumentCaptor<Comentario> guardado = ArgumentCaptor.forClass(Comentario.class);
        verify(comentarioRepository).saveAndFlush(guardado.capture());
        assertThat(guardado.getValue().getBpmnId()).isEqualTo("Activity_1");
        assertThat(guardado.getValue().getPadreId()).isNull();
        assertThat(guardado.getValue().getAutorId()).isEqualTo(ANA);
        assertThat(guardado.getValue().getMenciones()).containsExactly(BETO, ANA);
        assertThat(respuesta.html()).contains("<strong>Ojo</strong>", "&lt;script&gt;").doesNotContain("<script>");
        assertThat(respuesta.autor()).isEqualTo(new PersonaResponse(ANA, "Ana"));
        assertThat(respuesta.menciones()).containsExactly(new PersonaResponse(BETO, "Beto"),
                new PersonaResponse(ANA, "Ana"));
        assertThat(respuesta.estado()).isEqualTo(EstadoHilo.ABIERTO);
        assertThat(respuesta.eliminado()).isFalse();
        // Mencionarse a uno mismo no le avisa a nadie mas que a los otros
        verify(eventos).publishEvent(new NotificacionPedida(EMPRESA, TipoDeNotificacion.MENCION, ANA, PROCESO,
                respuesta.id(), List.of(BETO)));
    }

    @Test
    @DisplayName("sin menciones no se busca a nadie mas que al autor, ni se avisa")
    void sinMenciones() {
        servicio.comentar(EMPRESA, ANA, PROCESO, new Nuevo("Hola", "  ", null, null));
        servicio.comentar(EMPRESA, ANA, PROCESO, new Nuevo("Hola", null, null, List.of()));
        servicio.comentar(EMPRESA, ANA, PROCESO, new Nuevo("Hola", null, null, List.of(ANA)));

        verifyNoInteractions(elementos, eventos);
        ArgumentCaptor<Comentario> guardado = ArgumentCaptor.forClass(Comentario.class);
        verify(comentarioRepository, times(3)).saveAndFlush(guardado.capture());
        assertThat(guardado.getAllValues()).extracting(Comentario::getBpmnId).containsOnlyNulls();
    }

    @Test
    @DisplayName("el ancla tiene que existir: la version del proceso, o el elemento en el borrador")
    void ancla() {
        Nuevo enOtraVersion = new Nuevo("x", null, 4, null);
        Nuevo enOtroElemento = new Nuevo("x", "Activity_9", null, null);
        Nuevo sinAncla = new Nuevo("x", null, null, null);

        assertThatThrownBy(() -> servicio.comentar(EMPRESA, ANA, PROCESO, enOtraVersion))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessageContaining("versión 4");
        assertThatThrownBy(() -> servicio.comentar(EMPRESA, ANA, PROCESO, enOtroElemento))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessageContaining("Activity_9");
        assertThatThrownBy(() -> servicio.comentar(EMPRESA, ANA, 99L, sinAncla))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Proceso no encontrado.");
        verify(comentarioRepository, never()).saveAndFlush(any());

        // Con version, el elemento es el de esa version: el borrador ya no hace falta que lo tenga
        when(versionRepository.findByProcesoIdAndNumeroAndEmpresaId(PROCESO, 2, EMPRESA))
                .thenReturn(Optional.of(new VersionProceso()));
        ComentarioResponse respuesta = servicio.comentar(EMPRESA, ANA, PROCESO,
                new Nuevo("x", "Activity_viejo", 2, null));

        assertThat(respuesta.versionDelProceso()).isEqualTo(2);
        assertThat(respuesta.bpmnId()).isEqualTo("Activity_viejo");
        verify(elementos, never()).existe(EMPRESA, PROCESO, "Activity_viejo");
    }

    @Test
    @DisplayName("mencionar a quien no puede ver el proceso, a quien no esta activo o a quien no existe es un 400 igual")
    void mencionesInvalidas() {
        SolicitudInvalidaException error = catchThrowableOfType(SolicitudInvalidaException.class,
                () -> servicio.comentar(EMPRESA, ANA, PROCESO, new Nuevo("x", null, null,
                        List.of(BETO, DORA, ELIO, 404L, CARLA))));

        assertThat(error.getErrores()).containsOnlyKeys("menciones[1]", "menciones[2]", "menciones[3]")
                .containsValue("No es una persona de la organización que pueda ver este proceso.");
        assertThat(error.getErrores().values()).containsOnly(
                "No es una persona de la organización que pueda ver este proceso.");
        assertThat(error).hasMessage("Solo se puede mencionar a quien puede ver este proceso.");
        verify(comentarioRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventos);
    }

    @Test
    @DisplayName("responder avisa a los mencionados, y a los demas que escribieron en el hilo que pueden verlo")
    void responder() {
        Comentario raiz = hilo(10L, BETO);
        Comentario otra = respuestaDe(11L, raiz, CARLA);
        when(comentarioRepository.participantes(EMPRESA, 10L)).thenReturn(List.of(BETO, CARLA, DORA, ANA));

        ComentarioResponse respuesta = servicio.responder(EMPRESA, ANA, otra.getId(), "Yo **no**", List.of(CARLA));

        assertThat(respuesta.padreId()).isEqualTo(10L);
        assertThat(respuesta.estado()).isNull();
        assertThat(respuesta.html()).isEqualTo("<p>Yo <strong>no</strong></p>");
        verify(eventos).publishEvent(new NotificacionPedida(EMPRESA, TipoDeNotificacion.MENCION, ANA, PROCESO,
                respuesta.id(), List.of(CARLA)));
        // Carla ya recibe la mencion; Dora no puede ver el proceso; Ana es quien responde
        verify(eventos).publishEvent(new NotificacionPedida(EMPRESA, TipoDeNotificacion.RESPUESTA, ANA, PROCESO,
                respuesta.id(), List.of(BETO)));
    }

    @Test
    @DisplayName("una respuesta en un hilo de nadie mas no avisa a nadie")
    void responderSolo() {
        hilo(10L, ANA);
        when(comentarioRepository.participantes(EMPRESA, 10L)).thenReturn(List.of(ANA));

        servicio.responder(EMPRESA, ANA, 10L, "Nota", null);

        verifyNoInteractions(eventos);
        assertThatThrownBy(() -> servicio.responder(EMPRESA, ANA, 77L, "x", null))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Comentario no encontrado.");
    }

    @Test
    @DisplayName("solo su autor edita un comentario, con la version leida, y solo se avisa a las menciones nuevas")
    void editar() {
        Comentario comentario = hilo(10L, ANA);
        comentario.setMenciones(new ArrayList<>(List.of(BETO)));

        assertThatThrownBy(() -> servicio.editar(EMPRESA, BETO, 10L, "x", null, 0L))
                .isInstanceOf(SinPermisoException.class);
        assertThatThrownBy(() -> servicio.editar(EMPRESA, ANA, 10L, "x", null, 3L))
                .isInstanceOf(ConflictoDeVersionException.class);

        ComentarioResponse respuesta = servicio.editar(EMPRESA, ANA, 10L, "Otra *vez*", List.of(BETO, CARLA), 0L);

        assertThat(respuesta.markdown()).isEqualTo("Otra *vez*");
        assertThat(respuesta.html()).isEqualTo("<p>Otra <em>vez</em></p>");
        assertThat(respuesta.editadoEn()).isEqualTo(AHORA);
        assertThat(comentario.getMenciones()).containsExactly(BETO, CARLA);
        verify(eventos).publishEvent(new NotificacionPedida(EMPRESA, TipoDeNotificacion.MENCION, ANA, PROCESO, 10L,
                List.of(CARLA)));
    }

    @Test
    @DisplayName("editar sin menciones nuevas no avisa, y un comentario borrado ya no se edita")
    void editarSinAvisos() {
        Comentario comentario = hilo(10L, ANA);
        comentario.setMenciones(new ArrayList<>(List.of(BETO)));

        servicio.editar(EMPRESA, ANA, 10L, "x", List.of(BETO), 0L);

        verifyNoInteractions(eventos);
        comentario.setEliminadoEn(AHORA);
        assertThatThrownBy(() -> servicio.editar(EMPRESA, ANA, 10L, "x", null, 0L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("resolver un hilo anota quien y cuando, queda en la bitacora y avisa a los demas que escribieron")
    void resolver() {
        hilo(10L, BETO);
        when(comentarioRepository.participantes(EMPRESA, 10L)).thenReturn(List.of(BETO, CARLA, DORA, ANA));

        ComentarioResponse respuesta = servicio.resolver(EMPRESA, ANA, 10L);

        assertThat(respuesta.estado()).isEqualTo(EstadoHilo.RESUELTO);
        assertThat(respuesta.resueltoPor()).isEqualTo(new PersonaResponse(ANA, "Ana"));
        assertThat(respuesta.resueltoEn()).isEqualTo(AHORA);
        verify(auditoria).registrar(EMPRESA, ANA, "COMENTARIO_RESUELTO", "COMENTARIO", 10L,
                Map.of("procesoId", PROCESO));
        verify(eventos).publishEvent(new NotificacionPedida(EMPRESA, TipoDeNotificacion.HILO_RESUELTO, ANA, PROCESO,
                10L, List.of(BETO, CARLA)));

        assertThatThrownBy(() -> servicio.resolver(EMPRESA, ANA, 10L)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El hilo ya está resuelto.");
    }

    @Test
    @DisplayName("reabrir quita quien y cuando y queda en la bitacora; uno abierto no se reabre")
    void reabrir() {
        Comentario raiz = hilo(10L, BETO);
        assertThatThrownBy(() -> servicio.reabrir(EMPRESA, ANA, 10L)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El hilo ya está abierto.");
        raiz.setResueltoPor(BETO);
        raiz.setResueltoEn(AHORA);

        ComentarioResponse respuesta = servicio.reabrir(EMPRESA, ANA, 10L);

        assertThat(respuesta.estado()).isEqualTo(EstadoHilo.ABIERTO);
        assertThat(respuesta.resueltoPor()).isNull();
        assertThat(raiz.getResueltoEn()).isNull();
        verify(auditoria).registrar(EMPRESA, ANA, "COMENTARIO_REABIERTO", "COMENTARIO", 10L,
                Map.of("procesoId", PROCESO));
        verifyNoInteractions(eventos);
    }

    @Test
    @DisplayName("una respuesta no se resuelve ni se reabre: el estado es del hilo")
    void respuestaSinEstado() {
        Comentario raiz = hilo(10L, BETO);
        respuestaDe(11L, raiz, CARLA);

        assertThatThrownBy(() -> servicio.resolver(EMPRESA, ANA, 11L)).isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("primer comentario");
        assertThatThrownBy(() -> servicio.reabrir(EMPRESA, ANA, 11L)).isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("primer comentario");
        verify(comentarioRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("borrar es de su autor o de un administrador: lo deja sin texto ni menciones, y queda en la bitacora")
    void eliminar() {
        Comentario comentario = hilo(10L, BETO);
        comentario.setMenciones(new ArrayList<>(List.of(CARLA)));

        assertThatThrownBy(() -> servicio.eliminar(EMPRESA, CARLA, RolAcceso.EDITOR, 10L))
                .isInstanceOf(SinPermisoException.class);
        servicio.eliminar(EMPRESA, ANA, RolAcceso.ADMINISTRADOR, 10L);

        assertThat(comentario.getMarkdown()).isEmpty();
        assertThat(comentario.getHtml()).isEmpty();
        assertThat(comentario.getMenciones()).isEmpty();
        assertThat(comentario.getEliminadoPor()).isEqualTo(ANA);
        assertThat(comentario.getEliminadoEn()).isEqualTo(AHORA);
        verify(auditoria).registrar(EMPRESA, ANA, "COMENTARIO_ELIMINADO", "COMENTARIO", 10L,
                Map.of("procesoId", PROCESO, "propio", false));
        assertThatThrownBy(() -> servicio.eliminar(EMPRESA, BETO, RolAcceso.EDITOR, 10L))
                .isInstanceOf(RecursoNoEncontradoException.class);

        hilo(12L, BETO);
        servicio.eliminar(EMPRESA, BETO, RolAcceso.EDITOR, 12L);
        verify(auditoria).registrar(EMPRESA, BETO, "COMENTARIO_ELIMINADO", "COMENTARIO", 12L,
                Map.of("procesoId", PROCESO, "propio", true));
    }

    @Test
    @DisplayName("quien no ve el proceso no escribe en el, y no sabe si un comentario existe: todo responde 404")
    void sinVer() {
        Comentario raiz = hilo(10L, BETO);
        respuestaDe(11L, raiz, CARLA);
        raiz.setResueltoEn(AHORA);
        doThrow(new RecursoNoEncontradoException("Proceso no encontrado.")).when(permisos).exigir(EMPRESA, DORA,
                Accion.VER, Recurso.proceso(PROCESO));

        Nuevo nuevo = new Nuevo("x", null, null, null);
        assertThatThrownBy(() -> servicio.comentar(EMPRESA, DORA, PROCESO, nuevo))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.responder(EMPRESA, DORA, 11L, "x", null))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.editar(EMPRESA, DORA, 10L, "x", null, 9L))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.resolver(EMPRESA, DORA, 11L))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.resolver(EMPRESA, DORA, 10L))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.eliminar(EMPRESA, DORA, RolAcceso.EDITOR, 10L))
                .isInstanceOf(RecursoNoEncontradoException.class);
        raiz.setResueltoEn(null);
        assertThatThrownBy(() -> servicio.reabrir(EMPRESA, DORA, 10L))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(comentarioRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("en un proceso borrado no se escribe nada")
    void procesoBorrado() {
        Comentario comentario = hilo(10L, ANA);
        comentario.setProcesoId(99L);

        assertThatThrownBy(() -> servicio.responder(EMPRESA, ANA, 10L, "x", null))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.editar(EMPRESA, ANA, 10L, "x", null, 0L))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.resolver(EMPRESA, ANA, 10L))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.eliminar(EMPRESA, ANA, RolAcceso.ADMINISTRADOR, 10L))
                .isInstanceOf(RecursoNoEncontradoException.class);
        comentario.setResueltoEn(AHORA);
        assertThatThrownBy(() -> servicio.reabrir(EMPRESA, ANA, 10L))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(comentarioRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("listar trae los hilos del mas nuevo al mas viejo, cada uno con sus respuestas y los nombres")
    @SuppressWarnings("unchecked")
    void listar() {
        Comentario uno = hilo(10L, ANA);
        uno.setMenciones(new ArrayList<>(List.of(CARLA)));
        uno.setResueltoPor(BETO);
        uno.setResueltoEn(AHORA);
        Comentario dos = hilo(20L, BETO);
        dos.setEliminadoEn(AHORA);
        Comentario respuesta = respuestaDe(21L, dos, DORA);
        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        when(comentarioRepository.findAll(any(Specification.class), pagina.capture()))
                .thenAnswer(llamada -> new PageImpl<>(List.of(uno, dos), llamada.getArgument(1), 2));
        when(comentarioRepository.respuestasDe(EMPRESA, List.of(10L, 20L))).thenReturn(List.of(respuesta));

        PageResponse<HiloResponse> hilos = servicio.listar(EMPRESA, PROCESO, new Filtro(" ", 2, EstadoHilo.ABIERTO),
                PageRequest.of(1, 5));

        assertThat(pagina.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pagina.getValue().getPageSize()).isEqualTo(5);
        assertThat(pagina.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "fechaCreacion", "id"));
        assertThat(hilos.content()).hasSize(2);
        HiloResponse primero = hilos.content().getFirst();
        assertThat(primero.respuestas()).isEmpty();
        assertThat(primero.comentario().estado()).isEqualTo(EstadoHilo.RESUELTO);
        assertThat(primero.comentario().resueltoPor()).isEqualTo(new PersonaResponse(BETO, "Beto"));
        assertThat(primero.comentario().menciones()).containsExactly(new PersonaResponse(CARLA, "Carla"));
        HiloResponse segundo = hilos.content().get(1);
        assertThat(segundo.comentario().eliminado()).isTrue();
        assertThat(segundo.respuestas()).extracting(ComentarioResponse::id).containsExactly(21L);
        // El nombre de quien solo escribio una respuesta sale en la misma consulta que los demas
        assertThat(segundo.respuestas().getFirst().autor()).isEqualTo(new PersonaResponse(DORA, "Dora"));
    }

    @Test
    @DisplayName("una pagina sin hilos no busca respuestas; un proceso que no esta responde 404")
    @SuppressWarnings("unchecked")
    void listarVacio() {
        when(comentarioRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        assertThat(servicio.listar(EMPRESA, PROCESO, new Filtro(null, null, null), PageRequest.of(0, 20)).content())
                .isEmpty();
        verify(comentarioRepository, never()).respuestasDe(anyLong(), anyCollection());
        Filtro todos = new Filtro(null, null, null);
        PageRequest primera = PageRequest.of(0, 20);
        assertThatThrownBy(() -> servicio.listar(EMPRESA, 99L, todos, primera))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("al anonimizar a alguien, lo que lo mencionaba con @Nombre lo menciona con su seudonimo")
    void anonimizar() {
        Comentario conNombre = hilo(10L, ANA);
        conNombre.setMarkdown("Pregunta a @Beto y a **@Beto**");
        Comentario sinNombre = hilo(11L, ANA);
        sinNombre.setMarkdown("Pregunta a quien sepa");
        when(comentarioRepository.mencionandoA(EMPRESA, BETO)).thenReturn(List.of(conNombre, sinNombre));

        servicio.alAnonimizarse(new UsuarioAnonimizado(EMPRESA, BETO, "Beto", "Persona anonimizada 5"));

        assertThat(conNombre.getMarkdown())
                .isEqualTo("Pregunta a @Persona anonimizada 5 y a **@Persona anonimizada 5**");
        // El saneador escribe la arroba como entidad
        assertThat(conNombre.getHtml()).isEqualTo("<p>Pregunta a &#64;Persona anonimizada 5 y a "
                + "<strong>&#64;Persona anonimizada 5</strong></p>");
        assertThat(sinNombre.getHtml()).isEqualTo("<p>Hola</p>");
        verify(comentarioRepository).save(conNombre);
        verify(comentarioRepository, never()).save(sinNombre);
    }

    @Test
    @DisplayName("F21: a quien mencionar sale de las primeras cincuenta personas activas por nombre que pueden ver el proceso")
    @SuppressWarnings("unchecked")
    void mencionables() {
        List<Usuario> candidatas = List.of(persona(ANA, "Ana", RolAcceso.ADMINISTRADOR, true),
                persona(BETO, "Beto", RolAcceso.EDITOR, true), persona(CARLA, "Carla", RolAcceso.SOLO_LECTURA, true),
                persona(DORA, "Dora", RolAcceso.EDITOR, true));
        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        when(usuarioRepository.findAll(any(Specification.class), pagina.capture()))
                .thenAnswer(llamada -> new PageImpl<>(candidatas, llamada.getArgument(1), candidatas.size()));
        ArgumentCaptor<Collection<Actor>> actores = ArgumentCaptor.forClass(Collection.class);
        when(permisos.quienesPueden(eq(EMPRESA), eq(Recurso.proceso(PROCESO)), actores.capture(),
                eq(Set.of(Accion.VER)))).thenReturn(Set.of(ANA, BETO, CARLA));

        List<PersonaResponse> personas = servicio.mencionables(EMPRESA, ANA, PROCESO, "a");

        // Quien pregunta no se menciona a si mismo, y quien no ve el proceso no sale
        assertThat(personas).containsExactly(new PersonaResponse(BETO, "Beto"), new PersonaResponse(CARLA, "Carla"));
        assertThat(actores.getValue()).extracting(Actor::usuarioId, Actor::rol).containsExactly(
                tuple(BETO, RolAcceso.EDITOR), tuple(CARLA, RolAcceso.SOLO_LECTURA), tuple(DORA, RolAcceso.EDITOR));
        assertThat(pagina.getValue().getPageNumber()).isZero();
        assertThat(pagina.getValue().getPageSize()).isEqualTo(ComentarioServiceImpl.CANDIDATOS_A_MENCION);
        assertThat(pagina.getValue().getSort()).isEqualTo(Sort.by("nombre", "id"));
        verify(permisos).exigir(EMPRESA, ANA, Accion.VER, Recurso.proceso(PROCESO));
    }

    @Test
    @DisplayName("F21: a quien mencionar son como mucho veinte, las mismas que caben en un comentario")
    @SuppressWarnings("unchecked")
    void mencionablesConTope() {
        List<Usuario> candidatas = new ArrayList<>();
        for (long id = 200; id < 230; id++) {
            candidatas.add(persona(id, "Persona " + id, RolAcceso.EDITOR, true));
        }
        when(usuarioRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(candidatas));
        when(permisos.quienesPueden(eq(EMPRESA), eq(Recurso.proceso(PROCESO)), anyCollection(),
                eq(Set.of(Accion.VER)))).thenAnswer(llamada -> Set.copyOf(((Collection<Actor>) llamada.getArgument(2))
                        .stream().map(Actor::usuarioId).toList()));

        List<PersonaResponse> personas = servicio.mencionables(EMPRESA, ANA, PROCESO, null);

        assertThat(personas).hasSize(ComentarioServiceImpl.MENCIONABLES)
                .extracting(PersonaResponse::id).startsWith(200L, 201L).endsWith(219L);
    }

    @Test
    @DisplayName("F21: quien no ve el proceso no sabe a quien se puede mencionar en el: responde 404 sin buscar a nadie")
    @SuppressWarnings("unchecked")
    void mencionablesSinVer() {
        doThrow(new RecursoNoEncontradoException("Proceso no encontrado.")).when(permisos).exigir(EMPRESA, DORA,
                Accion.VER, Recurso.proceso(PROCESO));

        assertThatThrownBy(() -> servicio.mencionables(EMPRESA, DORA, PROCESO, "a"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.mencionables(EMPRESA, ANA, 99L, "a"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(usuarioRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }
}
