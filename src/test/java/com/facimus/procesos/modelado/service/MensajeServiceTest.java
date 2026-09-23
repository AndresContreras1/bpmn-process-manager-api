package com.facimus.procesos.modelado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.MensajeResponse;
import com.facimus.procesos.modelado.mapper.MensajeMapper;
import com.facimus.procesos.modelado.model.Correlacion;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.repository.CorrelacionRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;
import com.facimus.procesos.modelado.service.impl.MensajeServiceImpl;

/** HU-25 a HU-27: la comunicacion entre participantes de un mismo proceso. */
@ExtendWith(MockitoExtension.class)
class MensajeServiceTest {

    private static final Long EMPRESA = 1L;
    private static final Long AUTOR = 10L;

    @Mock
    private HistorialCambioService historialCambioService;
    @Mock
    private MensajeRepository mensajeRepository;
    @Mock
    private PoolRepository poolRepository;
    @Mock
    private ProcesoRepository procesoRepository;
    @Mock
    private CorrelacionRepository correlacionRepository;

    @Spy
    private MensajeMapper mensajeMapper = Mappers.getMapper(MensajeMapper.class);

    @InjectMocks
    private MensajeServiceImpl mensajeService;

    private Empresa empresa;
    private Proceso proceso;
    private Pool tienda;
    private Pool transportadora;

    @BeforeEach
    void setUp() {
        empresa = Empresa.builder().id(EMPRESA).nombre("Demo Store").build();
        proceso = Proceso.builder().id(100L).empresa(empresa).nombre("Order fulfillment").build();
        tienda = Pool.builder().id(5L).empresa(empresa).proceso(proceso).nombre("Demo Store").build();
        transportadora = Pool.builder().id(6L).empresa(empresa).proceso(proceso).nombre("Carrier").build();
    }

    @Test
    @DisplayName("HU-25: el mensaje une dos participantes del proceso y queda anotado en el historial")
    void crear_uneDosParticipantesDelProceso() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, EMPRESA)).thenReturn(Optional.of(proceso));
        when(poolRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(tienda));
        when(poolRepository.findByIdAndEmpresaId(6L, EMPRESA)).thenReturn(Optional.of(transportadora));
        when(mensajeRepository.save(any(Mensaje.class))).thenAnswer(inv -> inv.getArgument(0));

        MensajeResponse respuesta = mensajeService.crear(EMPRESA, AUTOR, 100L, "Shipment requested", "Pedido listo",
                5L, 6L);

        assertThat(respuesta.poolOrigenId()).isEqualTo(5L);
        assertThat(respuesta.poolDestinoId()).isEqualTo(6L);
        verify(historialCambioService).registrar(eq(EMPRESA), eq(AUTOR), eq(proceso), contains("Shipment requested"));
    }

    @Test
    @DisplayName("Un mensaje tiene que conectar dos pools diferentes")
    void crear_mismoPool_lanzaReglaNegocio() {
        assertThatThrownBy(() -> mensajeService.crear(EMPRESA, AUTOR, 100L, "Aviso", "texto", 5L, 5L))
                .isInstanceOf(ReglaNegocioException.class);
        verifyNoInteractions(procesoRepository, poolRepository, mensajeRepository);
    }

    @Test
    @DisplayName("Los dos pools de un mensaje tienen que ser participantes de su proceso")
    void crear_poolDeOtroProceso_lanzaReglaNegocio() {
        Proceso otroProceso = Proceso.builder().id(101L).empresa(empresa).nombre("Returns").build();
        Pool ajeno = Pool.builder().id(7L).empresa(empresa).proceso(otroProceso).nombre("Supplier").build();
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, EMPRESA)).thenReturn(Optional.of(proceso));
        when(poolRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(tienda));
        when(poolRepository.findByIdAndEmpresaId(7L, EMPRESA)).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> mensajeService.crear(EMPRESA, AUTOR, 100L, "Aviso", "texto", 5L, 7L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("participantes de su proceso");
        verify(mensajeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un proceso eliminado o de otra tienda no acepta mensajes")
    void crear_procesoFueraDeLaPuerta_lanzaNoEncontrado() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mensajeService.crear(EMPRESA, AUTOR, 100L, "Aviso", "texto", 5L, 6L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("HU-27: al eliminar un mensaje se va con el su clave de correlacion")
    void eliminar_arrastraLaCorrelacion() {
        Mensaje mensaje = Mensaje.builder().id(40L).empresa(empresa).proceso(proceso).poolOrigen(tienda)
                .poolDestino(transportadora).nombre("Shipment requested").build();
        Correlacion correlacion = Correlacion.builder().id(50L).empresa(empresa).mensaje(mensaje)
                .criterio("orderId").build();
        when(mensajeRepository.findByIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.of(mensaje));
        when(correlacionRepository.findByMensajeIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.of(correlacion));

        mensajeService.eliminar(EMPRESA, AUTOR, 40L);

        verify(correlacionRepository).delete(correlacion);
        verify(mensajeRepository).delete(mensaje);
        verify(historialCambioService).registrar(eq(EMPRESA), eq(AUTOR), eq(proceso), contains("eliminado"));
    }
}
