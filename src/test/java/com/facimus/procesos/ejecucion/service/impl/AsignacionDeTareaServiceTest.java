package com.facimus.procesos.ejecucion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SinPermisoException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.ejecucion.mapper.CasoMapper;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.Delegacion;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.EstadoDelegacion;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.model.TipoNodoCaso;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.repository.CasoRepository;
import com.facimus.procesos.ejecucion.repository.DelegacionRepository;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.service.MembresiaRolService;
import com.facimus.procesos.gestion.service.UsuarioService;

/**
 * PR 70: tomar, soltar, reasignar y delegar. Quien puede cada cosa, lo que cada una deja en la tarea, en su delegacion,
 * en la linea de tiempo del caso y en la bitacora, y lo que no deja cuando no se puede.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AsignacionDeTareaServiceTest {

    private static final Long TIENDA_ID = 1L;
    private static final Long TAREA_ID = 77L;
    private static final Long CASO_ID = 42L;
    private static final Long ROL = 2L;
    /** De su rol, editora. */
    private static final Long ANA = 3L;
    /** Editor, de otro rol. */
    private static final Long LUIS = 4L;
    private static final Long ADMIN = 6L;
    /** Solo lee. */
    private static final Long LECTORA = 7L;
    /** Desactivado. */
    private static final Long IDO = 8L;
    private static final int TICK = 9;
    private static final String NOMBRE = "\"Pick and pack items\"";

    @Mock
    private ActividadCasoRepository actividadCasoRepository;

    @Mock
    private CasoRepository casoRepository;

    @Mock
    private DelegacionRepository delegacionRepository;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private MembresiaRolService membresiaRolService;

    @Mock
    private RelojDeLaTienda reloj;

    @Mock
    private Bitacora bitacora;

    @Mock
    private Auditoria auditoria;

    @Mock
    private CasoMapper casoMapper;

    private AsignacionDeTareaServiceImpl servicio;
    private Caso caso;
    private ActividadCaso tarea;

    @BeforeEach
    void unaTareaQueEspera() {
        servicio = new AsignacionDeTareaServiceImpl(actividadCasoRepository, delegacionRepository,
                new Tareas(actividadCasoRepository, casoRepository, delegacionRepository),
                new PersonasDeLaTarea(usuarioService, membresiaRolService), reloj, bitacora, auditoria, casoMapper);
        Empresa tienda = Empresa.builder().id(TIENDA_ID).nombre("Demo Store").build();
        caso = Caso.builder().id(CASO_ID).empresa(tienda).referencia("ORD-1").estado(EstadoCaso.ABIERTO)
                .variables("{}").build();
        tarea = ActividadCaso.builder().id(TAREA_ID).empresa(tienda).caso(caso).nodoId(3L)
                .nodoNombre("Pick and pack items").tipoNodo(TipoNodoCaso.ACTIVIDAD).subtipo("USUARIO")
                .rolProcesoId(ROL).estado(EstadoActividadCaso.EN_ESPERA).build();
        given(actividadCasoRepository.findByIdAndEmpresaId(TAREA_ID, TIENDA_ID)).willReturn(Optional.of(tarea));
        given(actividadCasoRepository.casoDe(TAREA_ID, TIENDA_ID)).willReturn(Optional.of(CASO_ID));
        given(casoRepository.bloquear(CASO_ID, TIENDA_ID)).willReturn(Optional.of(caso));
        given(actividadCasoRepository.save(any(ActividadCaso.class))).willAnswer(llamada -> llamada.getArgument(0));
        given(reloj.ahora(TIENDA_ID)).willReturn(TICK);
        persona(ANA, RolAcceso.EDITOR, true);
        persona(LUIS, RolAcceso.EDITOR, true);
        persona(ADMIN, RolAcceso.ADMINISTRADOR, true);
        persona(LECTORA, RolAcceso.SOLO_LECTURA, true);
        persona(IDO, RolAcceso.EDITOR, false);
        given(usuarioService.obtener(TIENDA_ID, 99L)).willThrow(new RecursoNoEncontradoException("Usuario no "
                + "encontrado."));
        given(membresiaRolService.idsDeLosRolesDe(TIENDA_ID, ANA)).willReturn(List.of(ROL));
        given(membresiaRolService.idsDeLosRolesDe(TIENDA_ID, LUIS)).willReturn(List.of(5L));
    }

    @Nested
    @DisplayName("Reclamar")
    class Reclamar {

        @Test
        @DisplayName("Quien es de su rol la toma con la escritura atomica, y queda en el caso y en la bitacora")
        void deSuRol_laToma() {
            given(actividadCasoRepository.reclamar(any(), any(), any(), any())).willReturn(1);

            servicio.reclamar(TIENDA_ID, ANA, TAREA_ID);

            ArgumentCaptor<LocalDateTime> cuando = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(actividadCasoRepository).reclamar(eq(TAREA_ID), eq(TIENDA_ID), eq(ANA), cuando.capture());
            assertThat(cuando.getValue()).isNotNull();
            verify(bitacora).anotar(caso, TICK, TipoEventoCaso.TAREA_RECLAMADA,
                    NOMBRE + " la tomo el usuario " + ANA + ".", ANA);
            verify(auditoria).registrar(TIENDA_ID, ANA, "TAREA_RECLAMADA", "TAREA", TAREA_ID,
                    Map.of("casoId", CASO_ID));
            verify(casoMapper).toTarea(tarea);
        }

        @Test
        @DisplayName("Un administrador la toma aunque no sea de su rol, sin preguntar por sus roles")
        void administrador_laToma() {
            given(actividadCasoRepository.reclamar(eq(TAREA_ID), eq(TIENDA_ID), eq(ADMIN), any())).willReturn(1);

            servicio.reclamar(TIENDA_ID, ADMIN, TAREA_ID);

            verify(membresiaRolService, never()).idsDeLosRolesDe(TIENDA_ID, ADMIN);
            verify(auditoria).registrar(TIENDA_ID, ADMIN, "TAREA_RECLAMADA", "TAREA", TAREA_ID,
                    Map.of("casoId", CASO_ID));
        }

        @Test
        @DisplayName("Quien no es de su rol no la toma (403) y no se escribe nada")
        void deOtroRol_sinPermiso() {
            assertThatThrownBy(() -> servicio.reclamar(TIENDA_ID, LUIS, TAREA_ID))
                    .isInstanceOf(SinPermisoException.class)
                    .hasMessage(TareaServiceImpl.NO_ES_DE_SU_ROL);
            verify(actividadCasoRepository, never()).reclamar(any(), any(), any(), any());
            verifyNoInteractions(bitacora, auditoria);
        }

        @Test
        @DisplayName("Una tarea sin rol solo la toma un administrador")
        void sinRol_soloAdministrador() {
            tarea.setRolProcesoId(null);

            assertThatThrownBy(() -> servicio.reclamar(TIENDA_ID, ANA, TAREA_ID))
                    .isInstanceOf(SinPermisoException.class);
        }

        @Test
        @DisplayName("La que ya tiene alguien no se toma (409), ni siquiera quien la tiene")
        void yaTomada_esConflicto() {
            tarea.setAsignadoA(ANA);

            assertThatThrownBy(() -> servicio.reclamar(TIENDA_ID, ANA, TAREA_ID))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(AsignacionDeTareaServiceImpl.YA_LA_TIENE_ALGUIEN);
            verify(actividadCasoRepository, never()).reclamar(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Si otro la tomo entre la lectura y la escritura, la escritura no toca fila y es un 409")
        void carrera_laPierde() {
            given(actividadCasoRepository.reclamar(any(), any(), any(), any())).willReturn(0);

            assertThatThrownBy(() -> servicio.reclamar(TIENDA_ID, ANA, TAREA_ID))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(AsignacionDeTareaServiceImpl.YA_LA_TIENE_ALGUIEN);
            verifyNoInteractions(bitacora, auditoria);
        }

        @Test
        @DisplayName("La que ya no espera no se toma (409)")
        void completada_noSeToma() {
            tarea.setEstado(EstadoActividadCaso.COMPLETADA);

            assertThatThrownBy(() -> servicio.reclamar(TIENDA_ID, ANA, TAREA_ID))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(AsignacionDeTareaServiceImpl.YA_NO_ESPERA);
        }

        @Test
        @DisplayName("Un paso que no es tarea, o de otra tienda, no existe (404)")
        void noEsTarea_noExiste() {
            tarea.setSubtipo("SERVICIO");

            assertThatThrownBy(() -> servicio.reclamar(TIENDA_ID, ANA, TAREA_ID))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessage(Tareas.NO_ENCONTRADA);
            assertThatThrownBy(() -> servicio.reclamar(2L, ANA, TAREA_ID))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }
    }

    @Nested
    @DisplayName("Soltar")
    class Soltar {

        @Test
        @DisplayName("Quien la tiene la devuelve a la bandeja de su rol, con su motivo en el caso")
        void quienLaTiene_vuelveAlRol() {
            tarea.setAsignadoA(ANA);

            servicio.soltar(TIENDA_ID, ANA, TAREA_ID, "  No contesta el cliente  ");

            assertThat(tarea.getAsignadoA()).isNull();
            assertThat(tarea.getDelegadaPor()).isNull();
            verify(actividadCasoRepository).save(tarea);
            verify(bitacora).anotar(caso, TICK, TipoEventoCaso.TAREA_LIBERADA, NOMBRE + " deja de tenerla el usuario "
                    + ANA + " y vuelve a la bandeja de su rol: No contesta el cliente", ANA);
            verify(auditoria).registrar(TIENDA_ID, ANA, "TAREA_LIBERADA", "TAREA", TAREA_ID,
                    Map.of("casoId", CASO_ID, "de", ANA));
            verifyNoInteractions(delegacionRepository);
            verify(usuarioService, never()).obtener(any(), any());
        }

        @Test
        @DisplayName("Sin motivo, la linea termina en punto")
        void sinMotivo() {
            tarea.setAsignadoA(ANA);

            servicio.soltar(TIENDA_ID, ANA, TAREA_ID, " ");

            verify(bitacora).anotar(caso, TICK, TipoEventoCaso.TAREA_LIBERADA, NOMBRE + " deja de tenerla el usuario "
                    + ANA + " y vuelve a la bandeja de su rol.", ANA);
        }

        @Test
        @DisplayName("Un delegado que la suelta se la devuelve a quien se la delego, y la delegacion queda devuelta")
        void delegado_laDevuelve() {
            tarea.setAsignadoA(LUIS);
            tarea.setDelegadaPor(ANA);
            Delegacion abierta = delegacion(ANA, LUIS);

            servicio.soltar(TIENDA_ID, LUIS, TAREA_ID, null);

            assertThat(tarea.getAsignadoA()).isEqualTo(ANA);
            assertThat(tarea.getDelegadaPor()).isNull();
            assertThat(abierta.getEstado()).isEqualTo(EstadoDelegacion.DEVUELTA);
            assertThat(abierta.getCerradaEn()).isNotNull();
            verify(delegacionRepository).save(abierta);
            verify(bitacora).anotar(caso, TICK, TipoEventoCaso.TAREA_LIBERADA, NOMBRE + " deja de tenerla el usuario "
                    + LUIS + " y vuelve al usuario " + ANA + ", que la habia delegado.", LUIS);
            verify(auditoria).registrar(TIENDA_ID, LUIS, "TAREA_LIBERADA", "TAREA", TAREA_ID,
                    Map.of("casoId", CASO_ID, "de", LUIS, "a", ANA));
        }

        @Test
        @DisplayName("Un administrador suelta la de otro: vuelve al rol y una delegacion abierta se anula")
        void administrador_laDeOtro() {
            tarea.setAsignadoA(LUIS);
            tarea.setDelegadaPor(ANA);
            Delegacion abierta = delegacion(ANA, LUIS);

            servicio.soltar(TIENDA_ID, ADMIN, TAREA_ID, "Reparto");

            assertThat(tarea.getAsignadoA()).isNull();
            assertThat(tarea.getDelegadaPor()).isNull();
            assertThat(abierta.getEstado()).isEqualTo(EstadoDelegacion.ANULADA);
        }

        @Test
        @DisplayName("Quien no la tiene y no administra no la suelta (403)")
        void otro_sinPermiso() {
            tarea.setAsignadoA(ANA);

            assertThatThrownBy(() -> servicio.soltar(TIENDA_ID, LUIS, TAREA_ID, null))
                    .isInstanceOf(SinPermisoException.class)
                    .hasMessage(AsignacionDeTareaServiceImpl.SOLTAR_NO_ES_SUYO);
            assertThat(tarea.getAsignadoA()).isEqualTo(ANA);
            verify(actividadCasoRepository, never()).save(any());
        }

        @Test
        @DisplayName("La que no tiene nadie no se suelta (409)")
        void sinNadie_esConflicto() {
            assertThatThrownBy(() -> servicio.soltar(TIENDA_ID, ADMIN, TAREA_ID, null))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(AsignacionDeTareaServiceImpl.NO_LA_TIENE_NADIE);
        }

        @Test
        @DisplayName("D3: bloquea el caso antes de leer la tarea")
        void bloqueaAntes() {
            tarea.setAsignadoA(ANA);

            servicio.soltar(TIENDA_ID, ANA, TAREA_ID, null);

            InOrder enOrden = inOrder(casoRepository, actividadCasoRepository);
            enOrden.verify(casoRepository).bloquear(CASO_ID, TIENDA_ID);
            enOrden.verify(actividadCasoRepository).findByIdAndEmpresaId(TAREA_ID, TIENDA_ID);
        }

        @Test
        @DisplayName("La que ya no espera no se suelta (409)")
        void completada() {
            tarea.setAsignadoA(ANA);
            tarea.setEstado(EstadoActividadCaso.OMITIDA);

            assertThatThrownBy(() -> servicio.soltar(TIENDA_ID, ANA, TAREA_ID, null))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(AsignacionDeTareaServiceImpl.YA_NO_ESPERA);
        }
    }

    @Nested
    @DisplayName("Reasignar")
    class Reasignar {

        @Test
        @DisplayName("Quien la tiene se la pasa a otra persona que la puede completar, con su motivo")
        void quienLaTiene_laPasa() {
            tarea.setAsignadoA(ANA);

            servicio.reasignar(TIENDA_ID, ANA, TAREA_ID, LUIS, "Me voy de vacaciones");

            assertThat(tarea.getAsignadoA()).isEqualTo(LUIS);
            verify(actividadCasoRepository).save(tarea);
            verify(bitacora).anotar(caso, TICK, TipoEventoCaso.TAREA_REASIGNADA, NOMBRE + " pasa del usuario " + ANA
                    + " al usuario " + LUIS + ": Me voy de vacaciones", ANA);
            verify(auditoria).registrar(TIENDA_ID, ANA, "TAREA_REASIGNADA", "TAREA", TAREA_ID,
                    Map.of("casoId", CASO_ID, "de", ANA, "a", LUIS));
        }

        @Test
        @DisplayName("Un administrador da la que no tiene nadie: pasa de la bandeja del rol a la persona")
        void administrador_daLaDelRol() {
            servicio.reasignar(TIENDA_ID, ADMIN, TAREA_ID, LUIS, "Reparto");

            assertThat(tarea.getAsignadoA()).isEqualTo(LUIS);
            verify(bitacora).anotar(caso, TICK, TipoEventoCaso.TAREA_REASIGNADA, NOMBRE + " pasa de la bandeja de su "
                    + "rol al usuario " + LUIS + ": Reparto", ADMIN);
            verify(auditoria).registrar(TIENDA_ID, ADMIN, "TAREA_REASIGNADA", "TAREA", TAREA_ID,
                    Map.of("casoId", CASO_ID, "a", LUIS));
        }

        @Test
        @DisplayName("Reasignar una tarea delegada anula su delegacion: ya no vuelve a quien la delego")
        void delegada_seAnula() {
            tarea.setAsignadoA(LUIS);
            tarea.setDelegadaPor(ANA);
            Delegacion abierta = delegacion(ANA, LUIS);

            servicio.reasignar(TIENDA_ID, ADMIN, TAREA_ID, ADMIN, "Me la quedo");

            assertThat(tarea.getAsignadoA()).isEqualTo(ADMIN);
            assertThat(tarea.getDelegadaPor()).isNull();
            assertThat(abierta.getEstado()).isEqualTo(EstadoDelegacion.ANULADA);
        }

        @Test
        @DisplayName("Quien no la tiene y no administra no la reasigna (403), tampoco la que no tiene nadie")
        void otro_sinPermiso() {
            assertThatThrownBy(() -> servicio.reasignar(TIENDA_ID, ANA, TAREA_ID, LUIS, "x"))
                    .isInstanceOf(SinPermisoException.class)
                    .hasMessage(AsignacionDeTareaServiceImpl.REASIGNAR_NO_ES_SUYO);
            tarea.setAsignadoA(LUIS);
            assertThatThrownBy(() -> servicio.reasignar(TIENDA_ID, ANA, TAREA_ID, ANA, "x"))
                    .isInstanceOf(SinPermisoException.class);
            assertThat(tarea.getAsignadoA()).isEqualTo(LUIS);
        }

        @Test
        @DisplayName("A quien solo lee o esta desactivado no se le pasa (409); a quien no es de la tienda, 404")
        void destinoQueNoPuede() {
            tarea.setAsignadoA(ANA);

            assertThatThrownBy(() -> servicio.reasignar(TIENDA_ID, ANA, TAREA_ID, LECTORA, "x"))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(PersonasDeLaTarea.NO_PUEDE_TENERLA);
            assertThatThrownBy(() -> servicio.reasignar(TIENDA_ID, ANA, TAREA_ID, IDO, "x"))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(PersonasDeLaTarea.NO_PUEDE_TENERLA);
            assertThatThrownBy(() -> servicio.reasignar(TIENDA_ID, ANA, TAREA_ID, 99L, "x"))
                    .isInstanceOf(RecursoNoEncontradoException.class);
            assertThat(tarea.getAsignadoA()).isEqualTo(ANA);
            verify(actividadCasoRepository, never()).save(any());
        }

        @Test
        @DisplayName("A quien ya la tiene no se le reasigna (409)")
        void aQuienYaLaTiene() {
            tarea.setAsignadoA(ANA);

            assertThatThrownBy(() -> servicio.reasignar(TIENDA_ID, ANA, TAREA_ID, ANA, "x"))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(AsignacionDeTareaServiceImpl.YA_ES_DE_ESA_PERSONA);
        }

        @Test
        @DisplayName("La que ya no espera no se reasigna (409)")
        void completada() {
            tarea.setAsignadoA(ANA);
            tarea.setEstado(EstadoActividadCaso.COMPLETADA);

            assertThatThrownBy(() -> servicio.reasignar(TIENDA_ID, ADMIN, TAREA_ID, LUIS, "x"))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(AsignacionDeTareaServiceImpl.YA_NO_ESPERA);
        }
    }

    @Nested
    @DisplayName("Delegar")
    class Delegar {

        @Test
        @DisplayName("Quien la tiene la delega: el delegado la tiene, le vuelve si la suelta, y la delegacion se "
                + "guarda")
        void quienLaTiene_laDelega() {
            tarea.setAsignadoA(ANA);

            servicio.delegar(TIENDA_ID, ANA, TAREA_ID, LUIS, "  Cubre mi turno  ");

            assertThat(tarea.getAsignadoA()).isEqualTo(LUIS);
            assertThat(tarea.getDelegadaPor()).isEqualTo(ANA);
            ArgumentCaptor<Delegacion> guardada = ArgumentCaptor.forClass(Delegacion.class);
            verify(delegacionRepository).save(guardada.capture());
            assertThat(guardada.getValue().getTarea()).isSameAs(tarea);
            assertThat(guardada.getValue().getCaso()).isSameAs(caso);
            assertThat(guardada.getValue().getEmpresa()).isSameAs(tarea.getEmpresa());
            assertThat(guardada.getValue().getDelegadaPor()).isEqualTo(ANA);
            assertThat(guardada.getValue().getDelegadaA()).isEqualTo(LUIS);
            assertThat(guardada.getValue().getMotivo()).isEqualTo("Cubre mi turno");
            assertThat(guardada.getValue().getEstado()).isEqualTo(EstadoDelegacion.ACTIVA);
            verify(bitacora).anotar(caso, TICK, TipoEventoCaso.TAREA_DELEGADA, NOMBRE + " la delega el usuario " + ANA
                    + " en el usuario " + LUIS + ": Cubre mi turno", ANA);
            verify(auditoria).registrar(TIENDA_ID, ANA, "TAREA_DELEGADA", "TAREA", TAREA_ID,
                    Map.of("casoId", CASO_ID, "de", ANA, "a", LUIS));
        }

        @Test
        @DisplayName("Solo quien la tiene la delega: ni otro editor ni un administrador (403)")
        void otro_sinPermiso() {
            tarea.setAsignadoA(ANA);

            assertThatThrownBy(() -> servicio.delegar(TIENDA_ID, ADMIN, TAREA_ID, LUIS, "x"))
                    .isInstanceOf(SinPermisoException.class)
                    .hasMessage(AsignacionDeTareaServiceImpl.DELEGAR_NO_ES_SUYO);
            assertThatThrownBy(() -> servicio.delegar(TIENDA_ID, LUIS, TAREA_ID, ADMIN, "x"))
                    .isInstanceOf(SinPermisoException.class);
            verifyNoInteractions(delegacionRepository);
        }

        @Test
        @DisplayName("Una delegada no se vuelve a delegar (409)")
        void yaDelegada() {
            tarea.setAsignadoA(LUIS);
            tarea.setDelegadaPor(ANA);

            assertThatThrownBy(() -> servicio.delegar(TIENDA_ID, LUIS, TAREA_ID, ADMIN, "x"))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(AsignacionDeTareaServiceImpl.YA_DELEGADA);
        }

        @Test
        @DisplayName("No se delega en uno mismo, ni en quien no la puede completar (409)")
        void destinoQueNoSirve() {
            tarea.setAsignadoA(ANA);

            assertThatThrownBy(() -> servicio.delegar(TIENDA_ID, ANA, TAREA_ID, ANA, "x"))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(AsignacionDeTareaServiceImpl.A_SI_MISMO);
            assertThatThrownBy(() -> servicio.delegar(TIENDA_ID, ANA, TAREA_ID, LECTORA, "x"))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(PersonasDeLaTarea.NO_PUEDE_TENERLA);
            verifyNoInteractions(delegacionRepository);
            assertThat(tarea.getDelegadaPor()).isNull();
        }

        @Test
        @DisplayName("La que ya no espera no se delega (409)")
        void completada() {
            tarea.setAsignadoA(ANA);
            tarea.setEstado(EstadoActividadCaso.COMPLETADA);

            assertThatThrownBy(() -> servicio.delegar(TIENDA_ID, ANA, TAREA_ID, LUIS, "x"))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(AsignacionDeTareaServiceImpl.YA_NO_ESPERA);
        }
    }

    private void persona(Long id, RolAcceso rol, boolean activa) {
        given(usuarioService.obtener(TIENDA_ID, id)).willReturn(new UsuarioResponse(id, "Persona " + id,
                id + "@demo.test", rol, activa, TIENDA_ID, 0L, null, null, null, null, false, null));
    }

    private Delegacion delegacion(Long de, Long a) {
        Delegacion abierta = Delegacion.builder().id(5L).tarea(tarea).caso(caso).delegadaPor(de).delegadaA(a)
                .motivo("Vacaciones").estado(EstadoDelegacion.ACTIVA).build();
        given(delegacionRepository.findFirstByTareaIdAndEmpresaIdAndEstado(TAREA_ID, TIENDA_ID,
                EstadoDelegacion.ACTIVA)).willReturn(Optional.of(abierta));
        return abierta;
    }
}
