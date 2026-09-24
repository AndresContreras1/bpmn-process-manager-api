package com.facimus.procesos.modelado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.mapper.EventoMapper;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.Evento;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.EventoRepository;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;
import com.facimus.procesos.modelado.service.impl.EventoServiceImpl;

/** HU-04 y HU-27: donde empieza y termina el proceso, y los arcos que cada tipo de evento admite. */
@ExtendWith(MockitoExtension.class)
class EventoServiceTest {

    private static final Long EMPRESA = 1L;
    private static final Long AUTOR = 10L;

    @Mock
    private HistorialCambioService historialCambioService;
    @Mock
    private EventoRepository eventoRepository;
    @Mock
    private MensajeRepository mensajeRepository;
    @Mock
    private NodoFlujoRepository nodoFlujoRepository;
    @Mock
    private LaneRepository laneRepository;
    @Mock
    private ArcoRepository arcoRepository;

    @Spy
    private EventoMapper eventoMapper = Mappers.getMapper(EventoMapper.class);

    @InjectMocks
    private EventoServiceImpl eventoService;

    private Empresa empresa;
    private Proceso proceso;
    private Lane lane;
    private Evento evento;

    @BeforeEach
    void setUp() {
        empresa = Empresa.builder().id(EMPRESA).nombre("Demo Store").build();
        proceso = Proceso.builder().id(100L).empresa(empresa).nombre("Order fulfillment").build();
        Pool pool = Pool.builder().id(5L).empresa(empresa).proceso(proceso).nombre("Demo Store").build();
        lane = Lane.builder().id(7L).empresa(empresa).pool(pool)
                .rolProceso(RolProceso.builder().id(20L).empresa(empresa).nombre("Sales").build())
                .nombre("Sales").build();
        evento = Evento.builder().id(40L).empresa(empresa).lane(lane).nombre("Payment result received")
                .tipoEvento(TipoEvento.MENSAJE_INTERMEDIO).build();
    }

    @Test
    @DisplayName("HU-04: el evento se guarda en su lane con su tipo y queda anotado en el historial")
    void crear_guardaEnSuLaneConSuTipo() {
        when(laneRepository.findByIdAndEmpresaId(7L, EMPRESA)).thenReturn(Optional.of(lane));
        when(nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaId("Order received", 100L,
                EMPRESA)).thenReturn(false);
        when(eventoRepository.save(any(Evento.class))).thenAnswer(inv -> inv.getArgument(0));

        EventoResponse respuesta = eventoService.crear(EMPRESA, AUTOR, 7L, "Order received",
                TipoEvento.MENSAJE_INICIO, 20, 80);

        assertThat(respuesta.tipoEvento()).isEqualTo(TipoEvento.MENSAJE_INICIO);
        assertThat(respuesta.laneId()).isEqualTo(7L);
        verify(historialCambioService).registrar(EMPRESA, AUTOR, proceso, "Evento \"Order received\" agregado.");
    }

    @Test
    @DisplayName("Un evento no puede llamarse como otro nodo del proceso")
    void crear_conNombreRepetido_lanzaReglaNegocio() {
        when(laneRepository.findByIdAndEmpresaId(7L, EMPRESA)).thenReturn(Optional.of(lane));
        when(nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaId("Receive order", 100L,
                EMPRESA)).thenReturn(true);

        assertThatThrownBy(() -> eventoService.crear(EMPRESA, AUTOR, 7L, "Receive order", TipoEvento.INICIO, 0, 0))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Receive order");
        verify(eventoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Crear un evento en una lane de otra tienda no encuentra la lane")
    void crear_enLaneAjena_lanzaRecursoNoEncontrado() {
        when(laneRepository.findByIdAndEmpresaId(7L, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventoService.crear(EMPRESA, AUTOR, 7L, "Order received", TipoEvento.INICIO, 0, 0))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Lane no encontrada.");
    }

    @Test
    @DisplayName("R-31: un evento con arcos entrantes no pasa a evento de inicio")
    void editar_aInicioConArcosEntrantes_lanzaReglaNegocio() {
        when(eventoRepository.findByIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.of(evento));
        when(nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaIdAndIdNot(
                "Payment result received", 100L, EMPRESA, 40L)).thenReturn(false);
        when(arcoRepository.findAllByDestinoIdAndEmpresaId(40L, EMPRESA))
                .thenReturn(List.of(Arco.builder().id(50L).empresa(empresa).build()));

        assertThatThrownBy(() -> eventoService.editar(EMPRESA, AUTOR, 40L, "Payment result received",
                TipoEvento.MENSAJE_INICIO, null, 0, 0, null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Un evento de inicio no puede tener arcos entrantes.");
        assertThat(evento.getTipoEvento()).isEqualTo(TipoEvento.MENSAJE_INTERMEDIO);
        verify(eventoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("R-32: un evento con arcos salientes no pasa a evento de fin")
    void editar_aFinConArcosSalientes_lanzaReglaNegocio() {
        when(eventoRepository.findByIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.of(evento));
        when(nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaIdAndIdNot(
                "Payment result received", 100L, EMPRESA, 40L)).thenReturn(false);
        when(arcoRepository.findAllByOrigenIdAndEmpresaId(40L, EMPRESA))
                .thenReturn(List.of(Arco.builder().id(51L).empresa(empresa).build()));

        assertThatThrownBy(() -> eventoService.editar(EMPRESA, AUTOR, 40L, "Payment result received",
                TipoEvento.FIN, null, 0, 0, null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Un evento de fin no puede tener arcos salientes.");
        assertThat(evento.getTipoEvento()).isEqualTo(TipoEvento.MENSAJE_INTERMEDIO);
    }

    @Test
    @DisplayName("Un evento intermedio sin arcos pasa a inicio, y el que ya era intermedio se mueve sin mirar arcos")
    void editar_aIntermedio_noMiraLosArcos() {
        when(eventoRepository.findByIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.of(evento));
        when(nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaIdAndIdNot("Payment result",
                100L, EMPRESA, 40L)).thenReturn(false);
        when(eventoRepository.saveAndFlush(any(Evento.class))).thenAnswer(inv -> inv.getArgument(0));

        EventoResponse respuesta = eventoService.editar(EMPRESA, AUTOR, 40L, "Payment result",
                TipoEvento.MENSAJE_INTERMEDIO, null, 440, 80, null);

        assertThat(respuesta.nombre()).isEqualTo("Payment result");
        assertThat(respuesta.posicionX()).isEqualTo(440);
        verify(arcoRepository, never()).findAllByDestinoIdAndEmpresaId(any(), any());
        verify(arcoRepository, never()).findAllByOrigenIdAndEmpresaId(any(), any());
    }

    @Test
    @DisplayName("Al eliminar un evento se van los arcos que entran y los que salen de el")
    void eliminar_arrastraLosArcosQueEntranYSalen() {
        Arco entrante = Arco.builder().id(52L).empresa(empresa).build();
        when(eventoRepository.findByIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.of(evento));
        when(arcoRepository.findAllByOrigenIdAndEmpresaId(40L, EMPRESA)).thenReturn(List.of());
        when(arcoRepository.findAllByDestinoIdAndEmpresaId(40L, EMPRESA)).thenReturn(List.of(entrante));

        eventoService.eliminar(EMPRESA, AUTOR, 40L);

        verify(arcoRepository).deleteAll(List.of(entrante));
        verify(eventoRepository).delete(evento);
        verify(historialCambioService).registrar(EMPRESA, AUTOR, proceso,
                "Evento \"Payment result received\" eliminado.");
    }

    @Test
    @DisplayName("Los eventos de una lane se listan, y una lane de otra tienda no existe")
    void listarPorLane_deOtraTienda_lanzaRecursoNoEncontrado() {
        when(laneRepository.existsByIdAndEmpresaId(7L, EMPRESA)).thenReturn(true);
        when(eventoRepository.findAllByLaneIdAndEmpresaId(7L, EMPRESA)).thenReturn(List.of(evento));

        assertThat(eventoService.listarPorLane(EMPRESA, 7L)).extracting(EventoResponse::nombre)
                .containsExactly("Payment result received");

        when(laneRepository.existsByIdAndEmpresaId(8L, EMPRESA)).thenReturn(false);
        assertThatThrownBy(() -> eventoService.listarPorLane(EMPRESA, 8L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Lane no encontrada.");
    }

    @Test
    @DisplayName("Un evento de otra tienda no se obtiene por su id")
    void obtener_deOtraTienda_lanzaRecursoNoEncontrado() {
        when(eventoRepository.findByIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventoService.obtener(EMPRESA, 40L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Evento no encontrado.");
    }
}
