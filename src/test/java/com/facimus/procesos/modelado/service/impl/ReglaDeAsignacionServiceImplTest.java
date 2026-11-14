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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import com.facimus.procesos.modelado.dto.response.AsignacionResponse;
import com.facimus.procesos.modelado.mapper.ActividadMapper;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoAsignacion;
import com.facimus.procesos.modelado.repository.ActividadRepository;

/** PR 70: guardar a quien le llega una tarea, y lo que no se deja guardar. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReglaDeAsignacionServiceImplTest {

    private static final Long TIENDA = 1L;
    private static final Long AUTOR = 10L;
    private static final Long ANA = 5L;
    private static final Long LECTORA = 6L;
    private static final Long IDA = 7L;
    private static final Long ADMIN = 8L;

    @Mock
    private ActividadRepository actividadRepository;
    @Mock
    private UsuarioService usuarioService;
    @Mock
    private HistorialCambioService historialCambioService;

    private ReglaDeAsignacionServiceImpl servicio;
    private Proceso proceso;
    private Actividad tarea;

    @BeforeEach
    void unaTarea() {
        servicio = new ReglaDeAsignacionServiceImpl(actividadRepository, usuarioService, historialCambioService,
                Mappers.getMapper(ActividadMapper.class));
        Empresa empresa = Empresa.builder().id(TIENDA).nombre("Demo Store").build();
        proceso = Proceso.builder().id(100L).empresa(empresa).nombre("Refunds").build();
        Pool pool = Pool.builder().id(5L).empresa(empresa).proceso(proceso).nombre("Demo Store").build();
        Lane lane = Lane.builder().id(7L).empresa(empresa).pool(pool).nombre("Support").build();
        tarea = Actividad.builder().id(30L).empresa(empresa).lane(lane).nombre("Approve the refund")
                .tipoActividad(TipoActividad.USUARIO).version(2L).build();
        when(actividadRepository.findByIdAndEmpresaId(30L, TIENDA)).thenReturn(Optional.of(tarea));
        when(actividadRepository.saveAndFlush(any(Actividad.class))).thenAnswer(llamada -> llamada.getArgument(0));
        persona(ANA, RolAcceso.EDITOR, true);
        persona(ADMIN, RolAcceso.ADMINISTRADOR, true);
        persona(LECTORA, RolAcceso.SOLO_LECTURA, true);
        persona(IDA, RolAcceso.EDITOR, false);
        when(usuarioService.obtener(TIENDA, 99L)).thenThrow(new RecursoNoEncontradoException("Usuario no encontrado."));
    }

    @Test
    @DisplayName("PERSONA guarda la persona fija y vuelve en la respuesta, con su linea en el historial")
    void persona() {
        ActividadResponse respuesta = servicio.deLaActividad(TIENDA, AUTOR, 30L, TipoAsignacion.PERSONA, ANA,
                "ignorado", 2L);

        assertThat(tarea.getTipoAsignacion()).isEqualTo(TipoAsignacion.PERSONA);
        assertThat(tarea.getAsignacionUsuarioId()).isEqualTo(ANA);
        assertThat(tarea.getAsignacionCampo()).isNull();
        assertThat(respuesta.asignacion()).isEqualTo(new AsignacionResponse(TipoAsignacion.PERSONA, ANA, null));
        verify(historialCambioService).registrar(TIENDA, AUTOR, proceso,
                "Asignación de la actividad \"Approve the refund\": una persona fija.");
    }

    @Test
    @DisplayName("Un administrador tambien puede ser la persona fija")
    void persona_administrador() {
        servicio.deLaActividad(TIENDA, AUTOR, 30L, TipoAsignacion.PERSONA, ADMIN, null, 2L);

        assertThat(tarea.getAsignacionUsuarioId()).isEqualTo(ADMIN);
    }

    @Test
    @DisplayName("INICIADOR no guarda persona ni variable")
    void iniciador() {
        ActividadResponse respuesta = servicio.deLaActividad(TIENDA, AUTOR, 30L, TipoAsignacion.INICIADOR, ANA,
                "aprobador", 2L);

        assertThat(respuesta.asignacion()).isEqualTo(new AsignacionResponse(TipoAsignacion.INICIADOR, null, null));
        verifyNoInteractions(usuarioService);
        verify(historialCambioService).registrar(TIENDA, AUTOR, proceso,
                "Asignación de la actividad \"Approve the refund\": quien abre el caso.");
    }

    @Test
    @DisplayName("CAMPO guarda la ruta de la variable, sin espacios alrededor")
    void campo() {
        ActividadResponse respuesta = servicio.deLaActividad(TIENDA, AUTOR, 30L, TipoAsignacion.CAMPO, ANA,
                "  tarea.reviewOrder.approver ", 2L);

        assertThat(respuesta.asignacion())
                .isEqualTo(new AsignacionResponse(TipoAsignacion.CAMPO, null, "tarea.reviewOrder.approver"));
        verify(historialCambioService).registrar(TIENDA, AUTOR, proceso,
                "Asignación de la actividad \"Approve the refund\": la persona de una variable del caso.");
    }

    @Test
    @DisplayName("ROL la devuelve a la bandeja de su lane, sin nada guardado, y la respuesta no trae asignacion")
    void rol_quitaLaAsignacion() {
        tarea.setTipoAsignacion(TipoAsignacion.PERSONA);
        tarea.setAsignacionUsuarioId(ANA);

        ActividadResponse respuesta = servicio.deLaActividad(TIENDA, AUTOR, 30L, TipoAsignacion.ROL, ANA, "x", 2L);

        assertThat(tarea.getTipoAsignacion()).isNull();
        assertThat(tarea.getAsignacionUsuarioId()).isNull();
        assertThat(tarea.getAsignacionCampo()).isNull();
        assertThat(respuesta.asignacion()).isNull();
        verify(historialCambioService).registrar(TIENDA, AUTOR, proceso,
                "Asignación de la actividad \"Approve the refund\": la bandeja del rol de su lane.");
    }

    @Test
    @DisplayName("ROL se puede poner en cualquier actividad; los demas, solo en una de usuario (409)")
    void soloDeUsuario() {
        tarea.setTipoActividad(TipoActividad.SERVICIO);

        servicio.deLaActividad(TIENDA, AUTOR, 30L, TipoAsignacion.ROL, null, null, 2L);
        assertThatThrownBy(() -> servicio.deLaActividad(TIENDA, AUTOR, 30L, TipoAsignacion.INICIADOR, null, null, 2L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(ReglaDeAsignacionServiceImpl.SOLO_DE_USUARIO);
        assertThat(tarea.getTipoAsignacion()).isNull();
    }

    @Test
    @DisplayName("PERSONA sin persona, de otra tienda, que solo lee o desactivada: 400 en usuarioId, y nada cambia")
    void persona_queNoSirve() {
        assertThat(errorDe(null)).containsExactly(Map.entry("usuarioId", ReglaDeAsignacionServiceImpl.SIN_PERSONA));
        assertThat(errorDe(99L))
                .containsExactly(Map.entry("usuarioId", ReglaDeAsignacionServiceImpl.NO_ES_DE_LA_TIENDA));
        assertThat(errorDe(LECTORA)).containsExactly(Map.entry("usuarioId", ReglaDeAsignacionServiceImpl.NO_COMPLETA));
        assertThat(errorDe(IDA)).containsExactly(Map.entry("usuarioId", ReglaDeAsignacionServiceImpl.NO_COMPLETA));
        verify(actividadRepository, never()).saveAndFlush(any());
        verifyNoInteractions(historialCambioService);
    }

    @ParameterizedTest(name = "\"{0}\" no es una variable")
    @ValueSource(strings = {"", "  ", "1aprobador", "tarea..aprobador", "tarea.", ".aprobador", "aprobador-1",
            "tarea.revisar pedido"})
    @DisplayName("CAMPO con una ruta que no es una variable: 400 en campo")
    void campo_queNoEsVariable(String ruta) {
        assertThatThrownBy(() -> servicio.deLaActividad(TIENDA, AUTOR, 30L, TipoAsignacion.CAMPO, null, ruta, 2L))
                .isInstanceOfSatisfying(CamposInvalidosException.class, error -> {
                    assertThat(error).hasMessage(ReglaDeAsignacionServiceImpl.NO_ARMADA);
                    assertThat(error.errores())
                            .containsExactly(Map.entry("campo", ReglaDeAsignacionServiceImpl.SIN_CAMPO));
                });
        verify(actividadRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("CAMPO sin ruta tambien es un 400")
    void campo_sinRuta() {
        assertThatThrownBy(() -> servicio.deLaActividad(TIENDA, AUTOR, 30L, TipoAsignacion.CAMPO, null, null, 2L))
                .isInstanceOf(CamposInvalidosException.class);
    }

    @Test
    @DisplayName("Con una version vieja es un conflicto, y una actividad de otra tienda no existe")
    void versionYTienda() {
        assertThatThrownBy(() -> servicio.deLaActividad(TIENDA, AUTOR, 30L, TipoAsignacion.INICIADOR, null, null, 1L))
                .isInstanceOf(ConflictoDeVersionException.class);
        assertThatThrownBy(() -> servicio.deLaActividad(2L, AUTOR, 30L, TipoAsignacion.INICIADOR, null, null, 2L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Actividad no encontrada.");
    }

    private Map<String, String> errorDe(Long usuario) {
        try {
            servicio.deLaActividad(TIENDA, AUTOR, 30L, TipoAsignacion.PERSONA, usuario, null, 2L);
        } catch (CamposInvalidosException error) {
            assertThat(error).hasMessage(ReglaDeAsignacionServiceImpl.NO_ARMADA);
            return error.errores();
        }
        throw new AssertionError("Se guardo una persona que no sirve: " + usuario);
    }

    private void persona(Long id, RolAcceso rol, boolean activa) {
        when(usuarioService.obtener(TIENDA, id)).thenReturn(new UsuarioResponse(id, "Persona " + id, id + "@demo.test",
                rol, activa, TIENDA, 0L, null, null, null, null, false, null));
    }
}
