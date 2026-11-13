package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.facimus.procesos.common.CamposInvalidosException;
import com.facimus.procesos.common.ConflictoDeVersionException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.EscalamientoResponse;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.dto.response.PlazoResponse;
import com.facimus.procesos.modelado.mapper.ActividadMapper;
import com.facimus.procesos.modelado.mapper.EventoMapper;
import com.facimus.procesos.modelado.model.AccionDeEscalamiento;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Evento;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.UnidadDePlazo;
import com.facimus.procesos.modelado.repository.ActividadRepository;
import com.facimus.procesos.modelado.repository.EventoRepository;

/** PR 71: guardar el plazo de una tarea y de un caso, y lo que escala un borde; y lo que no se deja guardar. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlazoDelNodoServiceImplTest {

    private static final Long TIENDA = 1L;
    private static final Long AUTOR = 10L;
    private static final Long ANA = 5L;
    private static final Long LECTORA = 6L;

    @Mock
    private ActividadRepository actividadRepository;
    @Mock
    private EventoRepository eventoRepository;
    @Mock
    private UsuarioService usuarioService;
    @Mock
    private HistorialCambioService historialCambioService;

    private PlazoDelNodoServiceImpl servicio;
    private Proceso proceso;
    private Actividad tarea;
    private Evento inicio;
    private Evento borde;

    @BeforeEach
    void unProceso() {
        servicio = new PlazoDelNodoServiceImpl(actividadRepository, eventoRepository, usuarioService,
                historialCambioService, Mappers.getMapper(ActividadMapper.class), Mappers.getMapper(EventoMapper.class));
        Empresa empresa = Empresa.builder().id(TIENDA).nombre("Demo Store").build();
        proceso = Proceso.builder().id(100L).empresa(empresa).nombre("Refunds").build();
        Pool pool = Pool.builder().id(5L).empresa(empresa).proceso(proceso).nombre("Demo Store").build();
        Lane lane = Lane.builder().id(7L).empresa(empresa).pool(pool).nombre("Support").build();
        tarea = Actividad.builder().id(30L).empresa(empresa).lane(lane).nombre("Approve the refund")
                .tipoActividad(TipoActividad.USUARIO).version(2L).build();
        inicio = Evento.builder().id(40L).empresa(empresa).lane(lane).nombre("Refund requested")
                .tipoEvento(TipoEvento.INICIO).version(3L).build();
        borde = Evento.builder().id(50L).empresa(empresa).lane(lane).nombre("Six hours")
                .tipoEvento(TipoEvento.TEMPORIZADOR_BORDE).adjuntoA(tarea).interrumpe(false).version(4L).build();
        when(actividadRepository.findByIdAndEmpresaId(30L, TIENDA)).thenReturn(Optional.of(tarea));
        when(eventoRepository.findByIdAndEmpresaId(40L, TIENDA)).thenReturn(Optional.of(inicio));
        when(eventoRepository.findByIdAndEmpresaId(50L, TIENDA)).thenReturn(Optional.of(borde));
        when(actividadRepository.saveAndFlush(any(Actividad.class))).thenAnswer(llamada -> llamada.getArgument(0));
        when(eventoRepository.saveAndFlush(any(Evento.class))).thenAnswer(llamada -> llamada.getArgument(0));
        when(usuarioService.obtener(TIENDA, ANA)).thenReturn(persona(ANA, RolAcceso.EDITOR, true));
        when(usuarioService.obtener(TIENDA, LECTORA)).thenReturn(persona(LECTORA, RolAcceso.SOLO_LECTURA, true));
        when(usuarioService.obtener(TIENDA, 99L)).thenThrow(new RecursoNoEncontradoException("Usuario no encontrado."));
    }

    @Nested
    @DisplayName("El plazo de una tarea")
    class DeLaTarea {

        @Test
        @DisplayName("Se guarda en horas o en dias habiles, vuelve en la respuesta y deja su linea en el historial")
        void seGuarda() {
            ActividadResponse respuesta = servicio.deLaActividad(TIENDA, AUTOR, 30L, 8, UnidadDePlazo.HORAS, 2L);

            assertThat(tarea.getPlazoCantidad()).isEqualTo(8);
            assertThat(tarea.getPlazoUnidad()).isEqualTo(UnidadDePlazo.HORAS);
            assertThat(respuesta.plazo()).isEqualTo(new PlazoResponse(8, UnidadDePlazo.HORAS));
            verify(historialCambioService).registrar(TIENDA, AUTOR, proceso,
                    "Plazo de la actividad \"Approve the refund\": 8 horas.");

            servicio.deLaActividad(TIENDA, AUTOR, 30L, 2, UnidadDePlazo.DIAS_HABILES, 2L);
            verify(historialCambioService).registrar(TIENDA, AUTOR, proceso,
                    "Plazo de la actividad \"Approve the refund\": 2 dias habiles.");
        }

        @Test
        @DisplayName("Sin cantidad ni unidad se quita, tambien de una actividad que no es de usuario")
        void seQuita() {
            tarea.setPlazoCantidad(8);
            tarea.setPlazoUnidad(UnidadDePlazo.HORAS);
            tarea.setTipoActividad(TipoActividad.SERVICIO);

            ActividadResponse respuesta = servicio.deLaActividad(TIENDA, AUTOR, 30L, null, null, 2L);

            assertThat(tarea.getPlazoCantidad()).isNull();
            assertThat(tarea.getPlazoUnidad()).isNull();
            assertThat(respuesta.plazo()).isNull();
            verify(historialCambioService).registrar(TIENDA, AUTOR, proceso,
                    "Plazo de la actividad \"Approve the refund\": sin plazo.");
        }

        @Test
        @DisplayName("Solo una tarea de usuario lleva plazo (409), y nada se guarda")
        void soloDeUsuario() {
            tarea.setTipoActividad(TipoActividad.SERVICIO);

            assertThatThrownBy(() -> servicio.deLaActividad(TIENDA, AUTOR, 30L, 8, UnidadDePlazo.HORAS, 2L))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(PlazoDelNodoServiceImpl.SOLO_DE_USUARIO);
            assertThat(tarea.getPlazoUnidad()).isNull();
            verify(actividadRepository, never()).saveAndFlush(any());
        }

        @ParameterizedTest(name = "{0} {1}: 400 en {2}")
        @CsvSource({"0, HORAS, cantidad", "8785, HORAS, cantidad", "-1, DIAS_HABILES, cantidad",
                "367, DIAS_HABILES, cantidad", "8, , unidad", ", HORAS, cantidad"})
        @DisplayName("Una cantidad fuera de su tope, o una cantidad sin unidad o al reves, es un 400 en su propiedad")
        void malArmado(Integer cantidad, UnidadDePlazo unidad, String propiedad) {
            assertThatThrownBy(() -> servicio.deLaActividad(TIENDA, AUTOR, 30L, cantidad, unidad, 2L))
                    .isInstanceOfSatisfying(CamposInvalidosException.class, error -> {
                        assertThat(error).hasMessage(PlazoDelNodoServiceImpl.PLAZO_MAL_ARMADO);
                        assertThat(error.errores()).containsOnlyKeys(propiedad);
                    });
            verify(actividadRepository, never()).saveAndFlush(any());
            verifyNoInteractions(historialCambioService);
        }

        @Test
        @DisplayName("Los topes se dicen en el error, y los extremos de cada unidad se aceptan")
        void topes() {
            assertThatThrownBy(() -> servicio.deLaActividad(TIENDA, AUTOR, 30L, 367, UnidadDePlazo.DIAS_HABILES, 2L))
                    .isInstanceOfSatisfying(CamposInvalidosException.class, error -> assertThat(error.errores())
                            .containsExactly(Map.entry("cantidad", "En DIAS_HABILES va de 1 a 366.")));
            assertThatThrownBy(() -> servicio.deLaActividad(TIENDA, AUTOR, 30L, 8, null, 2L))
                    .isInstanceOfSatisfying(CamposInvalidosException.class, error -> assertThat(error.errores())
                            .containsExactly(Map.entry("unidad", PlazoDelNodoServiceImpl.SIN_UNIDAD)));
            assertThatThrownBy(() -> servicio.deLaActividad(TIENDA, AUTOR, 30L, null, UnidadDePlazo.HORAS, 2L))
                    .isInstanceOfSatisfying(CamposInvalidosException.class, error -> assertThat(error.errores())
                            .containsExactly(Map.entry("cantidad", PlazoDelNodoServiceImpl.SIN_CANTIDAD)));

            servicio.deLaActividad(TIENDA, AUTOR, 30L, 1, UnidadDePlazo.HORAS, 2L);
            assertThat(tarea.getPlazoCantidad()).isOne();
            servicio.deLaActividad(TIENDA, AUTOR, 30L, 8784, UnidadDePlazo.HORAS, 2L);
            assertThat(tarea.getPlazoCantidad()).isEqualTo(8784);
            servicio.deLaActividad(TIENDA, AUTOR, 30L, 366, UnidadDePlazo.DIAS_HABILES, 2L);
            assertThat(tarea.getPlazoCantidad()).isEqualTo(366);
        }

        @Test
        @DisplayName("Con una version vieja es un conflicto, y una actividad de otra tienda no existe")
        void versionYTienda() {
            assertThatThrownBy(() -> servicio.deLaActividad(TIENDA, AUTOR, 30L, 8, UnidadDePlazo.HORAS, 1L))
                    .isInstanceOf(ConflictoDeVersionException.class);
            assertThatThrownBy(() -> servicio.deLaActividad(2L, AUTOR, 30L, 8, UnidadDePlazo.HORAS, 2L))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessage("Actividad no encontrada.");
        }
    }

    @Nested
    @DisplayName("El plazo del caso")
    class DelCaso {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = TipoEvento.class, names = {"INICIO", "MENSAJE_INICIO", "TEMPORIZADOR_INICIO"})
        @DisplayName("Lo lleva cualquier evento de inicio, y deja su linea en el historial")
        void enUnInicio(TipoEvento tipo) {
            inicio.setTipoEvento(tipo);

            EventoResponse respuesta = servicio.delEvento(TIENDA, AUTOR, 40L, 5, UnidadDePlazo.DIAS_HABILES, 3L);

            assertThat(respuesta.plazo()).isEqualTo(new PlazoResponse(5, UnidadDePlazo.DIAS_HABILES));
            verify(historialCambioService).registrar(TIENDA, AUTOR, proceso,
                    "Plazo del caso en el evento \"Refund requested\": 5 dias habiles.");
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = TipoEvento.class, names = {"FIN", "MENSAJE_INTERMEDIO", "TEMPORIZADOR_BORDE"})
        @DisplayName("Otro evento no lo lleva (409); quitarlo si se puede")
        void soloDeInicio(TipoEvento tipo) {
            inicio.setTipoEvento(tipo);

            assertThatThrownBy(() -> servicio.delEvento(TIENDA, AUTOR, 40L, 5, UnidadDePlazo.DIAS_HABILES, 3L))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(PlazoDelNodoServiceImpl.SOLO_DE_INICIO);
            assertThat(servicio.delEvento(TIENDA, AUTOR, 40L, null, null, 3L).plazo()).isNull();
            verify(historialCambioService).registrar(TIENDA, AUTOR, proceso,
                    "Plazo del caso en el evento \"Refund requested\": sin plazo.");
        }

        @Test
        @DisplayName("Un plazo mal armado es un 400, y un evento de otra tienda no existe")
        void malArmadoYTienda() {
            assertThatThrownBy(() -> servicio.delEvento(TIENDA, AUTOR, 40L, 0, UnidadDePlazo.HORAS, 3L))
                    .isInstanceOf(CamposInvalidosException.class);
            assertThatThrownBy(() -> servicio.delEvento(2L, AUTOR, 40L, 5, UnidadDePlazo.HORAS, 3L))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessage("Evento no encontrado.");
            assertThatThrownBy(() -> servicio.delEvento(TIENDA, AUTOR, 40L, 5, UnidadDePlazo.HORAS, 2L))
                    .isInstanceOf(ConflictoDeVersionException.class);
            verify(eventoRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("El escalamiento de un borde")
    class Escalamiento {

        @Test
        @DisplayName("AVISAR o REASIGNAR, al supervisor dicho o al dueno, con su linea en el historial")
        void seGuarda() {
            EventoResponse respuesta = servicio.escalamiento(TIENDA, AUTOR, 50L, AccionDeEscalamiento.REASIGNAR, ANA,
                    4L);

            assertThat(respuesta.escalamiento())
                    .isEqualTo(new EscalamientoResponse(AccionDeEscalamiento.REASIGNAR, ANA));
            verify(historialCambioService).registrar(TIENDA, AUTOR, proceso,
                    "Escalamiento del evento \"Six hours\": reasigna al usuario 5.");

            respuesta = servicio.escalamiento(TIENDA, AUTOR, 50L, AccionDeEscalamiento.AVISAR, null, 4L);

            assertThat(respuesta.escalamiento()).isEqualTo(new EscalamientoResponse(AccionDeEscalamiento.AVISAR, null));
            verify(historialCambioService).registrar(TIENDA, AUTOR, proceso,
                    "Escalamiento del evento \"Six hours\": avisa al dueno del proceso.");
        }

        @Test
        @DisplayName("Sin accion solo abre su camino, y el supervisor que se hubiera dicho se olvida")
        void seQuita() {
            borde.setEscalamiento(AccionDeEscalamiento.AVISAR);
            borde.setEscalarA(ANA);

            EventoResponse respuesta = servicio.escalamiento(TIENDA, AUTOR, 50L, null, ANA, 4L);

            assertThat(respuesta.escalamiento()).isNull();
            assertThat(borde.getEscalarA()).isNull();
            verifyNoInteractions(usuarioService);
            verify(historialCambioService).registrar(TIENDA, AUTOR, proceso,
                    "Escalamiento del evento \"Six hours\": solo abre su camino.");
        }

        @Test
        @DisplayName("Un borde que interrumpe, o un evento que no es de borde, no escala (409)")
        void soloUnBordeQueNoInterrumpe() {
            borde.setInterrumpe(true);
            assertThatThrownBy(() -> servicio.escalamiento(TIENDA, AUTOR, 50L, AccionDeEscalamiento.AVISAR, null, 4L))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(PlazoDelNodoServiceImpl.SOLO_DE_BORDE);
            borde.setInterrumpe(null);
            assertThatThrownBy(() -> servicio.escalamiento(TIENDA, AUTOR, 50L, AccionDeEscalamiento.AVISAR, null, 4L))
                    .isInstanceOf(ReglaNegocioException.class);
            borde.setInterrumpe(false);
            borde.setTipoEvento(TipoEvento.TEMPORIZADOR_INTERMEDIO);
            assertThatThrownBy(() -> servicio.escalamiento(TIENDA, AUTOR, 50L, AccionDeEscalamiento.AVISAR, null, 4L))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(PlazoDelNodoServiceImpl.SOLO_DE_BORDE);
            assertThat(borde.getEscalamiento()).isNull();
            verify(eventoRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Solo se reasigna una tarea de usuario; avisar si se puede sobre cualquier actividad")
        void reasignaUnaTarea() {
            tarea.setTipoActividad(TipoActividad.RECEPCION);

            assertThatThrownBy(() -> servicio.escalamiento(TIENDA, AUTOR, 50L, AccionDeEscalamiento.REASIGNAR, null,
                    4L)).isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(PlazoDelNodoServiceImpl.REASIGNA_UNA_TAREA);
            borde.setAdjuntoA(null);
            assertThatThrownBy(() -> servicio.escalamiento(TIENDA, AUTOR, 50L, AccionDeEscalamiento.REASIGNAR, null,
                    4L)).isInstanceOf(ReglaNegocioException.class);
            assertThat(servicio.escalamiento(TIENDA, AUTOR, 50L, AccionDeEscalamiento.AVISAR, null, 4L)
                    .escalamiento().accion()).isEqualTo(AccionDeEscalamiento.AVISAR);
        }

        @Test
        @DisplayName("Un supervisor de otra tienda, o que no completa tareas, es un 400 en usuarioId")
        void supervisorQueNoSirve() {
            assertThatThrownBy(() -> servicio.escalamiento(TIENDA, AUTOR, 50L, AccionDeEscalamiento.AVISAR, 99L, 4L))
                    .isInstanceOfSatisfying(CamposInvalidosException.class, error -> {
                        assertThat(error).hasMessage(PlazoDelNodoServiceImpl.ESCALAMIENTO_MAL_ARMADO);
                        assertThat(error.errores())
                                .containsExactly(Map.entry("usuarioId", PersonaQueCompleta.NO_ES_DE_LA_TIENDA));
                    });
            assertThatThrownBy(() -> servicio.escalamiento(TIENDA, AUTOR, 50L, AccionDeEscalamiento.AVISAR, LECTORA,
                    4L)).isInstanceOfSatisfying(CamposInvalidosException.class, error -> assertThat(error.errores())
                            .containsExactly(Map.entry("usuarioId", PersonaQueCompleta.NO_COMPLETA)));
            verify(eventoRepository, never()).saveAndFlush(any());
        }
    }

    private static UsuarioResponse persona(Long id, RolAcceso rol, boolean activa) {
        return new UsuarioResponse(id, "Persona " + id, id + "@demo.test", rol, activa, TIENDA, 0L, null, null, null,
                null, false, null);
    }
}
