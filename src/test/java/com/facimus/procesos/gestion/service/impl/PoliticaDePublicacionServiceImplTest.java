package com.facimus.procesos.gestion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.facimus.procesos.common.ConflictoDeVersionException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Accion;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.PoliticaDePublicacionResponse;
import com.facimus.procesos.gestion.dto.response.PoliticaDePublicacionResponse.Aprobador;
import com.facimus.procesos.gestion.event.UsuarioAnonimizado;
import com.facimus.procesos.gestion.model.ModoDePublicacion;
import com.facimus.procesos.gestion.model.PoliticaPublicacion;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.MembresiaRolRepository;
import com.facimus.procesos.gestion.repository.PoliticaPublicacionRepository;
import com.facimus.procesos.gestion.repository.RolProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.Actor;
import com.facimus.procesos.gestion.service.DatosDePolitica;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.Permisos;
import com.facimus.procesos.gestion.service.Recurso;
import com.facimus.procesos.gestion.service.ReglaDeAprobacion;

/** PR 64: la politica de publicacion de una tienda y los aprobadores que da a cada solicitud. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PoliticaDePublicacionServiceImplTest {

    private static final Long EMPRESA = 1L;
    private static final Long ADMIN = 2L;
    private static final Long BETO = 5L;
    private static final Long CARLA = 6L;
    /** Desactivada: ya no aprueba. */
    private static final Long DORA = 7L;
    /** No puede ver el proceso. */
    private static final Long ELIO = 8L;
    /** Lo cambio por ultima vez: la separacion de funciones no lo deja aprobar. */
    private static final Long FIDEL = 9L;
    /** Activa, pero de solo lectura: nunca aprueba. */
    private static final Long GINA = 10L;
    private static final Long ROL = 30L;
    private static final Long PROCESO = 3L;

    @Mock
    private PoliticaPublicacionRepository politicaRepository;
    @Mock
    private RolProcesoRepository rolProcesoRepository;
    @Mock
    private MembresiaRolRepository membresiaRolRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private Permisos permisos;
    @Mock
    private HistorialCambioService historialCambioService;
    @Mock
    private Auditoria auditoria;

    @InjectMocks
    private PoliticaDePublicacionServiceImpl servicio;
    @Captor
    private ArgumentCaptor<Collection<Actor>> actores;

    private Empresa empresa;

    @BeforeEach
    void preparar() {
        empresa = Empresa.builder().id(EMPRESA).build();
        when(empresaRepository.findById(EMPRESA)).thenReturn(Optional.of(empresa));
        List<Usuario> personas = List.of(persona(ADMIN, "Ana", true), persona(BETO, "Beto", true),
                persona(CARLA, "Carla", true), persona(DORA, "Dora", false), persona(ELIO, "Elio", true),
                persona(FIDEL, "Fidel", true), lectora(GINA, "Gina"));
        when(usuarioRepository.findAllByEmpresaIdAndIdIn(eq(EMPRESA), anyCollection())).thenAnswer(llamada -> {
            Collection<Long> ids = llamada.getArgument(1);
            return personas.stream().filter(una -> ids.contains(una.getId())).toList();
        });
        when(politicaRepository.saveAndFlush(any(PoliticaPublicacion.class))).thenAnswer(llamada -> llamada
                .getArgument(0));
        RolProceso rol = RolProceso.builder().id(ROL).nombre("Duenos de proceso").empresa(empresa).build();
        when(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(ROL, EMPRESA)).thenReturn(Optional.of(rol));
        when(rolProcesoRepository.findByIdAndEmpresaId(ROL, EMPRESA)).thenReturn(Optional.of(rol));
        when(membresiaRolRepository.personasDe(EMPRESA, ROL)).thenReturn(List.of(BETO, CARLA, DORA, GINA));
        // El evaluador, de una vez para todas: Elio no ve el proceso y Fidel lo cambio por ultima vez. Las devuelve
        // al reves, para que el orden lo ponga el servicio
        when(permisos.quienesPueden(eq(EMPRESA), eq(Recurso.proceso(PROCESO)), anyCollection(),
                eq(EnumSet.of(Accion.VER, Accion.APROBAR)))).thenAnswer(llamada -> {
                    Collection<Actor> candidatos = llamada.getArgument(2);
                    Set<Long> pueden = new TreeSet<>(Comparator.reverseOrder());
                    candidatos.stream().filter(actor -> actor.rol() != RolAcceso.SOLO_LECTURA).map(Actor::usuarioId)
                            .filter(id -> !id.equals(ELIO) && !id.equals(FIDEL)).forEach(pueden::add);
                    return pueden;
                });
    }

    private Usuario lectora(Long id, String nombre) {
        Usuario usuario = persona(id, nombre, true);
        usuario.setRolAcceso(RolAcceso.SOLO_LECTURA);
        return usuario;
    }

    private Usuario persona(Long id, String nombre, boolean activo) {
        return Usuario.builder().id(id).nombre(nombre).rolAcceso(RolAcceso.EDITOR).activo(activo).empresa(empresa)
                .build();
    }

    private PoliticaPublicacion guardada(ModoDePublicacion modo, int necesarias, Long rol, List<Long> aprobadores) {
        PoliticaPublicacion politica = PoliticaPublicacion.builder().id(11L).empresa(empresa).modo(modo)
                .aprobacionesNecesarias(necesarias).rolProcesoId(rol).aprobadores(new ArrayList<>(aprobadores))
                .version(4L).modificadoPor(ADMIN).fechaModificacion(LocalDateTime.of(2026, 11, 12, 10, 0)).build();
        when(politicaRepository.findByEmpresaId(EMPRESA)).thenReturn(Optional.of(politica));
        return politica;
    }

    @Test
    @DisplayName("una tienda que nunca eligio publica directo, con una aprobacion y sin aprobadores")
    void porDefecto() {
        PoliticaDePublicacionResponse respuesta = servicio.obtener(EMPRESA);

        assertThat(respuesta.modo()).isEqualTo(ModoDePublicacion.DIRECTA);
        assertThat(respuesta.aprobacionesNecesarias()).isOne();
        assertThat(respuesta.aprobadores()).isEmpty();
        assertThat(respuesta.version()).isNull();
        assertThat(servicio.conAprobacion(EMPRESA)).isFalse();
        assertThat(servicio.reglaPara(EMPRESA, PROCESO, ADMIN)).isEmpty();
    }

    @Test
    @DisplayName("la politica guardada se responde con los nombres de hoy, y quien ya no esta activo se marca")
    void obtener() {
        guardada(ModoDePublicacion.CON_APROBACION, 2, null, List.of(BETO, DORA, 99L));

        PoliticaDePublicacionResponse respuesta = servicio.obtener(EMPRESA);

        assertThat(respuesta.modo()).isEqualTo(ModoDePublicacion.CON_APROBACION);
        assertThat(respuesta.aprobacionesNecesarias()).isEqualTo(2);
        assertThat(respuesta.aprobadores()).containsExactly(new Aprobador(BETO, "Beto", true),
                new Aprobador(DORA, "Dora", false), new Aprobador(99L, null, false));
        assertThat(respuesta.rolProceso()).isNull();
        assertThat(respuesta.version()).isEqualTo(4L);
        assertThat(respuesta.modificadoPor()).isEqualTo(ADMIN);
        assertThat(respuesta.fechaModificacion()).isEqualTo(LocalDateTime.of(2026, 11, 12, 10, 0));

        guardada(ModoDePublicacion.CON_APROBACION, 1, ROL, List.of());
        assertThat(servicio.obtener(EMPRESA).rolProceso()).isEqualTo("Duenos de proceso");
    }

    @Test
    @DisplayName("la primera vez se crea con la tienda, sin version; las aprobaciones que faltan valen 1")
    void guardarLaPrimera() {
        PoliticaDePublicacionResponse respuesta = servicio.guardar(EMPRESA, ADMIN, new DatosDePolitica(
                ModoDePublicacion.CON_APROBACION, null, null, List.of(BETO, BETO, CARLA), null));

        ArgumentCaptor<PoliticaPublicacion> guardada = ArgumentCaptor.forClass(PoliticaPublicacion.class);
        verify(politicaRepository).saveAndFlush(guardada.capture());
        assertThat(guardada.getValue().getEmpresa()).isSameAs(empresa);
        assertThat(guardada.getValue().getAprobadores()).containsExactly(BETO, CARLA);
        assertThat(respuesta.aprobacionesNecesarias()).isOne();
        verify(historialCambioService).registrarDeTienda(EMPRESA, ADMIN, RecursoDeHistorial.EMPRESA, EMPRESA,
                "Publicación con aprobación: 1 aprobación de una lista de personas.");
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("modo", "CON_APROBACION");
        detalle.put("antes", "DIRECTA");
        detalle.put("aprobacionesNecesarias", 1);
        detalle.put("aprobadores", 2);
        verify(auditoria).registrar(EMPRESA, ADMIN, "POLITICA_PUBLICACION_CAMBIADA", "EMPRESA", EMPRESA, detalle);
    }

    @Test
    @DisplayName("la primera vez con una version leida es un conflicto, y sin la tienda un 404")
    void guardarLaPrimeraMal() {
        DatosDePolitica conVersion = new DatosDePolitica(ModoDePublicacion.DIRECTA, null, null, null, 0L);
        assertThatThrownBy(() -> servicio.guardar(EMPRESA, ADMIN, conVersion))
                .isInstanceOf(ConflictoDeVersionException.class);

        when(empresaRepository.findById(EMPRESA)).thenReturn(Optional.empty());
        DatosDePolitica sinVersion = new DatosDePolitica(ModoDePublicacion.DIRECTA, null, null, null, null);
        assertThatThrownBy(() -> servicio.guardar(EMPRESA, ADMIN, sinVersion))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(politicaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("cambiar una guardada pide su version, y el historial dice si aprueba un rol")
    void cambiarLaGuardada() {
        guardada(ModoDePublicacion.DIRECTA, 1, null, List.of());
        DatosDePolitica vieja = new DatosDePolitica(ModoDePublicacion.CON_APROBACION, 2, ROL, null, 3L);
        assertThatThrownBy(() -> servicio.guardar(EMPRESA, ADMIN, vieja))
                .isInstanceOf(ConflictoDeVersionException.class);

        PoliticaDePublicacionResponse respuesta = servicio.guardar(EMPRESA, ADMIN, new DatosDePolitica(
                ModoDePublicacion.CON_APROBACION, 2, ROL, null, 4L));

        assertThat(respuesta.rolProcesoId()).isEqualTo(ROL);
        assertThat(respuesta.rolProceso()).isEqualTo("Duenos de proceso");
        verify(historialCambioService).registrarDeTienda(EMPRESA, ADMIN, RecursoDeHistorial.EMPRESA, EMPRESA,
                "Publicación con aprobación: 2 aprobaciones de un rol de proceso.");
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("modo", "CON_APROBACION");
        detalle.put("antes", "DIRECTA");
        detalle.put("aprobacionesNecesarias", 2);
        detalle.put("aprobadores", 0);
        detalle.put("rolProcesoId", ROL);
        verify(auditoria).registrar(EMPRESA, ADMIN, "POLITICA_PUBLICACION_CAMBIADA", "EMPRESA", EMPRESA, detalle);

        guardada(ModoDePublicacion.CON_APROBACION, 2, ROL, List.of());
        servicio.guardar(EMPRESA, ADMIN, new DatosDePolitica(ModoDePublicacion.DIRECTA, 2, ROL, null, 4L));
        verify(historialCambioService).registrarDeTienda(EMPRESA, ADMIN, RecursoDeHistorial.EMPRESA, EMPRESA,
                "Publicación directa.");
    }

    @Test
    @DisplayName("los aprobadores salen de una lista o de un rol, nunca de los dos")
    void unaFuente() {
        DatosDePolitica dos = new DatosDePolitica(ModoDePublicacion.CON_APROBACION, 1, ROL, List.of(BETO), null);
        assertThatThrownBy(() -> servicio.guardar(EMPRESA, ADMIN, dos)).isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("Los aprobadores salen de una lista o de un rol de proceso, no de los dos.");
        DatosDePolitica ninguna = new DatosDePolitica(ModoDePublicacion.CON_APROBACION, 1, null, List.of(), null);
        assertThatThrownBy(() -> servicio.guardar(EMPRESA, ADMIN, ninguna))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("Con aprobación hay que decir quién aprueba: una lista de personas o un rol de proceso.");
        verify(politicaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("publicar directo no necesita aprobadores; de los que manda se guardan solo quienes podrian aprobar")
    void directaSinAprobadores() {
        assertThat(servicio.guardar(EMPRESA, ADMIN, new DatosDePolitica(ModoDePublicacion.DIRECTA, null, null,
                List.of(), null)).modo()).isEqualTo(ModoDePublicacion.DIRECTA);

        // Apagarla con la lista que devolvio el GET: Dora se desactivo, Gina es de solo lectura y 99 no es de la tienda
        servicio.guardar(EMPRESA, ADMIN, new DatosDePolitica(ModoDePublicacion.DIRECTA, null, null,
                List.of(CARLA, DORA, BETO, GINA, 99L), null));

        ArgumentCaptor<PoliticaPublicacion> guardada = ArgumentCaptor.forClass(PoliticaPublicacion.class);
        verify(politicaRepository, times(2)).saveAndFlush(guardada.capture());
        assertThat(guardada.getValue().getAprobadores()).containsExactly(CARLA, BETO);
    }

    @Test
    @DisplayName("apagarla con un rol que ya se elimino lo descarta, en vez de responder 404; uno vigente se guarda")
    void directaConRolEliminado() {
        guardada(ModoDePublicacion.CON_APROBACION, 1, ROL, List.of());
        when(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(ROL, EMPRESA)).thenReturn(Optional.empty());

        servicio.guardar(EMPRESA, ADMIN, new DatosDePolitica(ModoDePublicacion.DIRECTA, 1, ROL, null, 4L));

        ArgumentCaptor<PoliticaPublicacion> guardada = ArgumentCaptor.forClass(PoliticaPublicacion.class);
        verify(politicaRepository).saveAndFlush(guardada.capture());
        assertThat(guardada.getValue().getModo()).isEqualTo(ModoDePublicacion.DIRECTA);
        assertThat(guardada.getValue().getRolProcesoId()).isNull();

        RolProceso rol = RolProceso.builder().id(ROL).nombre("Duenos de proceso").empresa(empresa).build();
        when(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(ROL, EMPRESA)).thenReturn(Optional.of(rol));
        servicio.guardar(EMPRESA, ADMIN, new DatosDePolitica(ModoDePublicacion.DIRECTA, 1, ROL, null, 4L));
        verify(politicaRepository, times(2)).saveAndFlush(guardada.capture());
        assertThat(guardada.getValue().getRolProcesoId()).isEqualTo(ROL);
    }

    @Test
    @DisplayName("una persona desactivada, de otra tienda o que no existe responde igual, una de solo lectura dice "
            + "que no aprueba, y cada una dice cual es")
    void personasDeLaTienda() {
        DatosDePolitica lista = new DatosDePolitica(ModoDePublicacion.CON_APROBACION, 1, null,
                List.of(DORA, BETO, 99L, GINA), null);

        SolicitudInvalidaException error = catchThrowableOfType(SolicitudInvalidaException.class,
                () -> servicio.guardar(EMPRESA, ADMIN, lista));

        assertThat(error).hasMessage("Solo aprueban personas activas de la organización que no son de solo lectura.");
        assertThat(error.getErrores()).containsOnly(Map.entry("aprobadores[0]",
                "No es una persona activa de la organización."), Map.entry("aprobadores[2]",
                        "No es una persona activa de la organización."),
                Map.entry("aprobadores[3]", "Es de solo lectura: no puede aprobar."));
        verify(politicaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("no se piden mas aprobaciones que personas activas que aprueban; con justo esas, se guarda")
    void suficientes() {
        DatosDePolitica tres = new DatosDePolitica(ModoDePublicacion.CON_APROBACION, 3, null, List.of(BETO, CARLA),
                null);
        assertThatThrownBy(() -> servicio.guardar(EMPRESA, ADMIN, tres)).isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("La política pide 3 aprobaciones y quienes aprueban son 2.");
        // Del rol cuentan las activas que no son de solo lectura: Beto y Carla, no Dora ni Gina
        DatosDePolitica delRol = new DatosDePolitica(ModoDePublicacion.CON_APROBACION, 3, ROL, null, null);
        assertThatThrownBy(() -> servicio.guardar(EMPRESA, ADMIN, delRol))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("La política pide 3 aprobaciones y quienes aprueban son 2.");

        assertThat(servicio.guardar(EMPRESA, ADMIN, new DatosDePolitica(ModoDePublicacion.CON_APROBACION, 2, ROL,
                null, null)).aprobacionesNecesarias()).isEqualTo(2);
    }

    @Test
    @DisplayName("un rol que no esta o esta desactivado es un 404")
    void rolInexistente() {
        when(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(ROL, EMPRESA)).thenReturn(Optional.empty());
        DatosDePolitica delRol = new DatosDePolitica(ModoDePublicacion.CON_APROBACION, 1, ROL, null, null);

        assertThatThrownBy(() -> servicio.guardar(EMPRESA, ADMIN, delRol))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("con aprobacion, publicar directo se cierra; con la politica en DIRECTA, no")
    void conAprobacion() {
        guardada(ModoDePublicacion.CON_APROBACION, 1, null, List.of(BETO));
        assertThat(servicio.conAprobacion(EMPRESA)).isTrue();
        guardada(ModoDePublicacion.DIRECTA, 1, null, List.of(BETO));
        assertThat(servicio.conAprobacion(EMPRESA)).isFalse();
        assertThat(servicio.reglaPara(EMPRESA, PROCESO, ADMIN)).isEmpty();
    }

    @Test
    @DisplayName("los aprobadores de una solicitud: activos, que ven y aprueban el proceso, sin quien pide, en orden")
    void reglaConLista() {
        guardada(ModoDePublicacion.CON_APROBACION, 2, null, List.of(FIDEL, CARLA, ELIO, DORA, BETO, ADMIN, CARLA));

        Optional<ReglaDeAprobacion> regla = servicio.reglaPara(EMPRESA, PROCESO, ADMIN);

        assertThat(regla).contains(new ReglaDeAprobacion(2, List.of(BETO, CARLA)));
        // Al evaluador llegan las activas sin quien pide, cada una una vez y con su rol de acceso, en una sola llamada
        verify(permisos).quienesPueden(eq(EMPRESA), eq(Recurso.proceso(PROCESO)), actores.capture(),
                eq(EnumSet.of(Accion.VER, Accion.APROBAR)));
        assertThat(actores.getValue()).containsExactlyInAnyOrder(new Actor(FIDEL, EMPRESA, RolAcceso.EDITOR),
                new Actor(CARLA, EMPRESA, RolAcceso.EDITOR), new Actor(ELIO, EMPRESA, RolAcceso.EDITOR),
                new Actor(BETO, EMPRESA, RolAcceso.EDITOR));
        verify(permisos, never()).puede(any(), any(), any());
    }

    @Test
    @DisplayName("con un rol, sus personas; con el rol desactivado, nadie")
    void reglaConRol() {
        guardada(ModoDePublicacion.CON_APROBACION, 1, ROL, List.of());

        assertThat(servicio.reglaPara(EMPRESA, PROCESO, CARLA)).contains(new ReglaDeAprobacion(1, List.of(BETO)));

        when(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(ROL, EMPRESA)).thenReturn(Optional.empty());
        assertThat(servicio.reglaPara(EMPRESA, PROCESO, CARLA)).contains(new ReglaDeAprobacion(1, List.of()));
        verify(usuarioRepository, never()).findAllByEmpresaIdAndIdIn(EMPRESA, List.of());
    }

    @Test
    @DisplayName("quien se anonimiza sale de la lista de aprobadores")
    void anonimizar() {
        servicio.alAnonimizarse(new UsuarioAnonimizado(EMPRESA, BETO, "Beto", "Persona anonimizada 5"));

        verify(politicaRepository).quitarAprobador(EMPRESA, BETO);
        verify(historialCambioService, never()).registrarDeTienda(any(), any(), any(), any(), anyString());
    }
}
