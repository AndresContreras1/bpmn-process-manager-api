package com.facimus.procesos.modelado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
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

import com.facimus.procesos.common.ConflictoDeVersionException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.PoolResponse;
import com.facimus.procesos.modelado.mapper.PoolMapper;
import com.facimus.procesos.modelado.model.Correlacion;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.repository.CorrelacionRepository;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;
import com.facimus.procesos.modelado.service.impl.PoolServiceImpl;

/** HU-21: los participantes del proceso. */
@ExtendWith(MockitoExtension.class)
class PoolServiceTest {

    private static final Long EMPRESA = 1L;
    private static final Long AUTOR = 10L;

    @Mock
    private HistorialCambioService historialCambioService;
    @Mock
    private PoolRepository poolRepository;
    @Mock
    private ProcesoRepository procesoRepository;
    @Mock
    private LaneRepository laneRepository;
    @Mock
    private NodoFlujoRepository nodoFlujoRepository;
    @Mock
    private MensajeRepository mensajeRepository;
    @Mock
    private CorrelacionRepository correlacionRepository;

    @Spy
    private PoolMapper poolMapper = Mappers.getMapper(PoolMapper.class);

    @InjectMocks
    private PoolServiceImpl poolService;

    private Empresa empresa;
    private Proceso proceso;
    private Pool pool;

    @BeforeEach
    void setUp() {
        empresa = Empresa.builder().id(EMPRESA).nombre("Demo Store").build();
        proceso = Proceso.builder().id(100L).empresa(empresa).nombre("Order fulfillment").build();
        pool = Pool.builder().id(5L).empresa(empresa).proceso(proceso).nombre("Carrier").orden(1)
                .tipoParticipante(TipoParticipante.PROVEEDOR).build();
    }

    @Test
    @DisplayName("HU-21: el pool nuevo se guarda al final del proceso y queda anotado en el historial")
    void crear_vaAlFinalDelProcesoYQuedaEnElHistorial() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, EMPRESA)).thenReturn(Optional.of(proceso));
        when(poolRepository.siguienteOrden(100L, EMPRESA)).thenReturn(2);
        when(poolRepository.save(any(Pool.class))).thenAnswer(inv -> inv.getArgument(0));

        PoolResponse respuesta = poolService.crear(EMPRESA, AUTOR, 100L, "Payment gateway",
                TipoParticipante.SISTEMA_EXTERNO, true, Integracion.PAGOS);

        assertThat(respuesta.orden()).isEqualTo(2);
        assertThat(respuesta.cajaNegra()).isTrue();
        assertThat(respuesta.integracion()).isEqualTo(Integracion.PAGOS);
        assertThat(respuesta.procesoId()).isEqualTo(100L);
        verify(historialCambioService).registrar(eq(EMPRESA), eq(AUTOR), eq(proceso), contains("Payment gateway"));
    }

    @Test
    @DisplayName("Un proceso eliminado o de otra tienda no acepta pools")
    void crear_procesoFueraDeLaPuerta_lanzaNoEncontrado() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> poolService.crear(EMPRESA, AUTOR, 100L, "Carrier",
                TipoParticipante.PROVEEDOR, false, null))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(poolRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un pool con actividades no se elimina")
    void eliminar_conActividades_lanzaReglaNegocio() {
        when(poolRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(pool));
        when(nodoFlujoRepository.existsByLane_Pool_IdAndEmpresaId(5L, EMPRESA)).thenReturn(true);

        assertThatThrownBy(() -> poolService.eliminar(EMPRESA, AUTOR, 5L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Carrier");
        verify(poolRepository, never()).delete(any());
        verify(mensajeRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Al eliminar un pool se van con el sus mensajes, sus correlaciones y sus lanes")
    void eliminar_arrastraMensajesCorrelacionesYLanes() {
        Pool otroPool = Pool.builder().id(6L).empresa(empresa).proceso(proceso).nombre("Demo Store").build();
        Mensaje enviado = Mensaje.builder().id(30L).empresa(empresa).proceso(proceso).poolOrigen(pool)
                .poolDestino(otroPool).nombre("Shipment requested").build();
        Mensaje recibido = Mensaje.builder().id(31L).empresa(empresa).proceso(proceso).poolOrigen(otroPool)
                .poolDestino(pool).nombre("Shipment confirmed").build();
        Correlacion correlacion = Correlacion.builder().id(40L).empresa(empresa).mensaje(enviado)
                .criterio("orderId").build();
        when(poolRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(pool));
        when(nodoFlujoRepository.existsByLane_Pool_IdAndEmpresaId(5L, EMPRESA)).thenReturn(false);
        when(mensajeRepository.findAllByPoolOrigenIdAndEmpresaId(5L, EMPRESA)).thenReturn(List.of(enviado));
        when(mensajeRepository.findAllByPoolDestinoIdAndEmpresaId(5L, EMPRESA)).thenReturn(List.of(recibido));
        when(correlacionRepository.findByMensajeIdAndEmpresaId(30L, EMPRESA)).thenReturn(Optional.of(correlacion));
        when(correlacionRepository.findByMensajeIdAndEmpresaId(31L, EMPRESA)).thenReturn(Optional.empty());
        when(laneRepository.findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(5L, EMPRESA)).thenReturn(List.of());

        poolService.eliminar(EMPRESA, AUTOR, 5L);

        verify(correlacionRepository).delete(correlacion);
        verify(mensajeRepository).delete(enviado);
        verify(mensajeRepository).delete(recibido);
        verify(laneRepository).deleteAll(List.of());
        verify(poolRepository).delete(pool);
        verify(historialCambioService).registrar(eq(EMPRESA), eq(AUTOR), eq(proceso), contains("eliminado"));
    }

    @Test
    @DisplayName("R-33: un pool que ya tiene lanes no pasa a caja negra")
    void editar_pasandoACajaNegraConLanes_lanzaReglaNegocio() {
        when(poolRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(pool));
        when(laneRepository.existsByPoolIdAndEmpresaId(5L, EMPRESA)).thenReturn(true);

        assertThatThrownBy(() -> poolService.editar(EMPRESA, AUTOR, 5L, "Carrier", TipoParticipante.PROVEEDOR, true,
                null, null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Un pool de caja negra no puede tener lanes.");
        assertThat(pool.isCajaNegra()).isFalse();
        verify(poolRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("HU-21.4: un pool sin lanes si pasa a caja negra, que es como se dibuja otra empresa")
    void editar_pasandoACajaNegraSinLanes_loMarca() {
        when(poolRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(pool));
        when(laneRepository.existsByPoolIdAndEmpresaId(5L, EMPRESA)).thenReturn(false);
        when(poolRepository.saveAndFlush(any(Pool.class))).thenAnswer(inv -> inv.getArgument(0));

        PoolResponse respuesta = poolService.editar(EMPRESA, AUTOR, 5L, "Carrier", TipoParticipante.PROVEEDOR, true,
                null, null);

        assertThat(respuesta.cajaNegra()).isTrue();
    }

    @Test
    @DisplayName("Editar con una version vieja responde conflicto y no guarda")
    void editar_conVersionVieja_lanzaConflicto() {
        when(poolRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(pool));

        assertThatThrownBy(() -> poolService.editar(EMPRESA, AUTOR, 5L, "Carrier",
                TipoParticipante.PROVEEDOR, false, null, 7L))
                .isInstanceOf(ConflictoDeVersionException.class);
        verify(poolRepository, never()).saveAndFlush(any());
        verify(historialCambioService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Listar los pools de un proceso eliminado responde no encontrado")
    void listarPorProceso_procesoFueraDeLaPuerta_lanzaNoEncontrado() {
        when(procesoRepository.existsByIdAndEmpresaIdAndActivoTrue(100L, EMPRESA)).thenReturn(false);

        assertThatThrownBy(() -> poolService.listarPorProceso(EMPRESA, 100L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
