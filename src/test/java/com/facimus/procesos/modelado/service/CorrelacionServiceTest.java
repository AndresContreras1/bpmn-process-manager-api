package com.facimus.procesos.modelado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

import com.facimus.procesos.common.ConflictoDeVersionException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.CorrelacionResponse;
import com.facimus.procesos.modelado.mapper.CorrelacionMapper;
import com.facimus.procesos.modelado.model.Correlacion;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.repository.CorrelacionRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;
import com.facimus.procesos.modelado.service.impl.CorrelacionServiceImpl;

/** HU-28: la clave que dice a que caso concreto del proceso corresponde un mensaje. */
@ExtendWith(MockitoExtension.class)
class CorrelacionServiceTest {

    private static final Long EMPRESA = 1L;
    private static final Long AUTOR = 10L;

    @Mock
    private HistorialCambioService historialCambioService;
    @Mock
    private CorrelacionRepository correlacionRepository;
    @Mock
    private MensajeRepository mensajeRepository;

    @Spy
    private CorrelacionMapper correlacionMapper = Mappers.getMapper(CorrelacionMapper.class);

    @InjectMocks
    private CorrelacionServiceImpl correlacionService;

    private Empresa empresa;
    private Proceso proceso;
    private Mensaje mensaje;

    @BeforeEach
    void setUp() {
        empresa = Empresa.builder().id(EMPRESA).nombre("Demo Store").build();
        proceso = Proceso.builder().id(100L).empresa(empresa).nombre("Order fulfillment").build();
        Pool tienda = Pool.builder().id(5L).empresa(empresa).proceso(proceso).nombre("Demo Store").build();
        Pool transportadora = Pool.builder().id(6L).empresa(empresa).proceso(proceso).nombre("Carrier").build();
        mensaje = Mensaje.builder().id(40L).empresa(empresa).proceso(proceso).poolOrigen(tienda)
                .poolDestino(transportadora).nombre("Shipment requested").build();
    }

    @Test
    @DisplayName("HU-28: la primera clave del mensaje se crea sin mandar version")
    void definir_primeraVez_creaSinVersion() {
        when(mensajeRepository.findByIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.of(mensaje));
        when(correlacionRepository.findByMensajeIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.empty());
        when(correlacionRepository.saveAndFlush(any(Correlacion.class))).thenAnswer(inv -> inv.getArgument(0));

        CorrelacionResponse respuesta = correlacionService.definir(EMPRESA, AUTOR, 40L, "orderId", null);

        assertThat(respuesta.criterio()).isEqualTo("orderId");
        assertThat(respuesta.mensajeId()).isEqualTo(40L);
        verify(historialCambioService).registrar(eq(EMPRESA), eq(AUTOR), eq(proceso), contains("orderId"));
    }

    @Test
    @DisplayName("Reemplazar la clave reusa la fila que ya tenia el mensaje")
    void definir_conClaveExistente_reusaLaFila() {
        Correlacion actual = Correlacion.builder().id(50L).empresa(empresa).mensaje(mensaje).criterio("orderId")
                .build();
        when(mensajeRepository.findByIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.of(mensaje));
        when(correlacionRepository.findByMensajeIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.of(actual));
        when(correlacionRepository.saveAndFlush(any(Correlacion.class))).thenAnswer(inv -> inv.getArgument(0));

        CorrelacionResponse respuesta = correlacionService.definir(EMPRESA, AUTOR, 40L, "trackingId", null);

        assertThat(respuesta.id()).isEqualTo(50L);
        assertThat(actual.getCriterio()).isEqualTo("trackingId");
    }

    @Test
    @DisplayName("Reemplazar la clave con una version vieja responde conflicto")
    void definir_conVersionVieja_lanzaConflicto() {
        Correlacion actual = Correlacion.builder().id(50L).empresa(empresa).mensaje(mensaje).criterio("orderId")
                .build();
        when(mensajeRepository.findByIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.of(mensaje));
        when(correlacionRepository.findByMensajeIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.of(actual));

        assertThatThrownBy(() -> correlacionService.definir(EMPRESA, AUTOR, 40L, "trackingId", 3L))
                .isInstanceOf(ConflictoDeVersionException.class);
        assertThat(actual.getCriterio()).isEqualTo("orderId");
        verify(correlacionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Un mensaje sin clave de correlacion responde no encontrado")
    void obtener_sinClave_lanzaNoEncontrado() {
        when(correlacionRepository.findByMensajeIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> correlacionService.obtener(EMPRESA, 40L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Definir la clave de un mensaje de otra tienda responde no encontrado")
    void definir_mensajeAjeno_lanzaNoEncontrado() {
        when(mensajeRepository.findByIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> correlacionService.definir(EMPRESA, AUTOR, 40L, "orderId", null))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(correlacionRepository, never()).saveAndFlush(any());
    }
}
