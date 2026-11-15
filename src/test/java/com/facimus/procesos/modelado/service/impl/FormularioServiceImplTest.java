package com.facimus.procesos.modelado.service.impl;

import static com.facimus.procesos.common.formularios.CampoArmado.campo;
import static com.facimus.procesos.common.formularios.CampoArmado.formulario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
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
import com.facimus.procesos.common.formularios.Formulario;
import com.facimus.procesos.common.formularios.TipoDeCampo;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.mapper.ActividadMapper;
import com.facimus.procesos.modelado.mapper.EventoMapper;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Evento;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.repository.ActividadRepository;
import com.facimus.procesos.modelado.repository.EventoRepository;

/** PR 69: guardar el formulario de una tarea o de un inicio, y lo que no se deja guardar. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FormularioServiceImplTest {

    private static final Long TIENDA = 1L;
    private static final Long AUTOR = 10L;

    @Mock
    private ActividadRepository actividadRepository;
    @Mock
    private EventoRepository eventoRepository;
    @Mock
    private HistorialCambioService historialCambioService;

    private FormularioServiceImpl servicio;
    private Proceso proceso;
    private Actividad tarea;
    private Evento inicio;

    private final Formulario pago = formulario(campo("monto", TipoDeCampo.MONEDA).requerido().moneda("COP"),
            campo("metodo", TipoDeCampo.LISTA).opciones("TARJETA", "EFECTIVO").visibleSi("monto > 100"));

    @BeforeEach
    void unaTareaYUnInicio() {
        servicio = new FormularioServiceImpl(actividadRepository, eventoRepository, historialCambioService,
                Mappers.getMapper(ActividadMapper.class), Mappers.getMapper(EventoMapper.class));
        Empresa empresa = Empresa.builder().id(TIENDA).nombre("Demo Store").build();
        proceso = Proceso.builder().id(100L).empresa(empresa).nombre("Refunds").build();
        Pool pool = Pool.builder().id(5L).empresa(empresa).proceso(proceso).nombre("Demo Store").build();
        Lane lane = Lane.builder().id(7L).empresa(empresa).pool(pool).nombre("Support").build();
        tarea = Actividad.builder().id(30L).empresa(empresa).lane(lane).nombre("Approve the refund")
                .tipoActividad(TipoActividad.USUARIO).version(2L).build();
        inicio = Evento.builder().id(31L).empresa(empresa).lane(lane).nombre("Refund requested")
                .tipoEvento(TipoEvento.INICIO).version(4L).build();
        when(actividadRepository.findByIdAndEmpresaId(30L, TIENDA)).thenReturn(Optional.of(tarea));
        when(eventoRepository.findByIdAndEmpresaId(31L, TIENDA)).thenReturn(Optional.of(inicio));
        when(actividadRepository.saveAndFlush(any(Actividad.class))).thenAnswer(llamada -> llamada.getArgument(0));
        when(eventoRepository.saveAndFlush(any(Evento.class))).thenAnswer(llamada -> llamada.getArgument(0));
    }

    @Test
    @DisplayName("Una tarea de usuario guarda su formulario, que vuelve con las condiciones compiladas")
    void tareaDeUsuario() {
        ActividadResponse respuesta = servicio.deLaActividad(TIENDA, AUTOR, 30L, pago, 2L);

        assertThat(tarea.getFormulario()).isEqualTo(pago);
        assertThat(respuesta.formulario().campos()).hasSize(2);
        assertThat(respuesta.formulario().campos().get(1).visibleSiArbol().ruta()).isEqualTo("monto");
        assertThat(respuesta.formulario().campos().getFirst().visibleSiArbol()).isNull();
        verify(historialCambioService).registrar(TIENDA, AUTOR, proceso,
                "Formulario de la actividad \"Approve the refund\" guardado.");
    }

    @Test
    @DisplayName("Un inicio simple guarda el suyo")
    void inicioSimple() {
        EventoResponse respuesta = servicio.delEvento(TIENDA, AUTOR, 31L, pago, 4L);

        assertThat(inicio.getFormulario()).isEqualTo(pago);
        assertThat(respuesta.formulario().formulario()).isEqualTo(pago);
        verify(historialCambioService).registrar(TIENDA, AUTOR, proceso,
                "Formulario del evento \"Refund requested\" guardado.");
    }

    @Test
    @DisplayName("Un formulario sin campos quita el que habia")
    void quitar() {
        tarea.setFormulario(pago);

        ActividadResponse respuesta = servicio.deLaActividad(TIENDA, AUTOR, 30L, new Formulario(null), 2L);

        assertThat(tarea.getFormulario()).isNull();
        assertThat(respuesta.formulario()).isNull();
        verify(historialCambioService).registrar(TIENDA, AUTOR, proceso,
                "Formulario de la actividad \"Approve the refund\" quitado.");
    }

    @Test
    @DisplayName("Uno nulo tambien")
    void quitarConNulo() {
        inicio.setFormulario(pago);

        servicio.delEvento(TIENDA, AUTOR, 31L, null, 4L);

        assertThat(inicio.getFormulario()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = TipoActividad.class, names = "USUARIO", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Una tarea que no es de usuario no lleva formulario: nadie lo llenaria")
    void tareaQueNoEsDeUsuario(TipoActividad tipo) {
        tarea.setTipoActividad(tipo);

        assertThatThrownBy(() -> servicio.deLaActividad(TIENDA, AUTOR, 30L, pago, 2L))
                .isInstanceOf(ReglaNegocioException.class).hasMessage(FormularioServiceImpl.SOLO_DE_USUARIO);
        assertThat(tarea.getFormulario()).isNull();
        verify(actividadRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = TipoEvento.class, names = "INICIO", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Un evento que no es un inicio simple no lleva formulario: se abre solo o no abre nada")
    void eventoQueNoEsUnInicioSimple(TipoEvento tipo) {
        inicio.setTipoEvento(tipo);

        assertThatThrownBy(() -> servicio.delEvento(TIENDA, AUTOR, 31L, pago, 4L))
                .isInstanceOf(ReglaNegocioException.class).hasMessage(FormularioServiceImpl.SOLO_DE_INICIO);
        verify(eventoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Quitarlo se puede siempre, aunque el nodo ya no sea de los que lo llevan")
    void quitarDeCualquierNodo() {
        tarea.setTipoActividad(TipoActividad.SERVICIO);

        servicio.deLaActividad(TIENDA, AUTOR, 30L, new Formulario(null), 2L);

        assertThat(tarea.getFormulario()).isNull();
        verify(actividadRepository).saveAndFlush(tarea);
    }

    @Test
    @DisplayName("Un formulario mal armado no se guarda: 400 con cada propiedad")
    void malArmado() {
        Formulario malo = formulario(campo("monto", TipoDeCampo.NUMERO).minimo("10").maximo("1"),
                campo("monto", TipoDeCampo.TEXTO).patron("(a)\\1"));

        assertThatThrownBy(() -> servicio.deLaActividad(TIENDA, AUTOR, 30L, malo, 2L))
                .isInstanceOfSatisfying(CamposInvalidosException.class, error -> {
                    assertThat(error).hasMessage("El formulario no está bien armado.");
                    assertThat(error.errores()).containsOnlyKeys("campos[0].minimo", "campos[1].nombre",
                            "campos[1].patron");
                });
        assertThat(tarea.getFormulario()).isNull();
        verify(historialCambioService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Con la version vieja, 409: alguien guardo otro cambio antes")
    void versionVieja() {
        assertThatThrownBy(() -> servicio.deLaActividad(TIENDA, AUTOR, 30L, pago, 1L))
                .isInstanceOf(ConflictoDeVersionException.class);
        assertThatThrownBy(() -> servicio.delEvento(TIENDA, AUTOR, 31L, pago, 3L))
                .isInstanceOf(ConflictoDeVersionException.class);
        assertThat(tarea.getFormulario()).isNull();
    }

    @Test
    @DisplayName("Un nodo de otra tienda no existe")
    void deOtraTienda() {
        assertThatThrownBy(() -> servicio.deLaActividad(2L, AUTOR, 30L, pago, 2L))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Actividad no encontrada.");
        assertThatThrownBy(() -> servicio.delEvento(2L, AUTOR, 31L, pago, 4L))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Evento no encontrado.");
    }
}
