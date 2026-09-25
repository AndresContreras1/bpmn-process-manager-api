package com.facimus.procesos.modelado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
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

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.GatewayResponse;
import com.facimus.procesos.modelado.mapper.GatewayMapper;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.GatewayRepository;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;
import com.facimus.procesos.modelado.service.impl.GatewayServiceImpl;

/** HU-14 a HU-16: los puntos de decision, y la condicion que exigen los arcos que salen de ellos. */
@ExtendWith(MockitoExtension.class)
class GatewayServiceTest {

    private static final Long EMPRESA = 1L;
    private static final Long AUTOR = 10L;

    @Mock
    private HistorialCambioService historialCambioService;
    @Mock
    private GatewayRepository gatewayRepository;
    @Mock
    private MensajeRepository mensajeRepository;
    @Mock
    private NodoFlujoRepository nodoFlujoRepository;
    @Mock
    private LaneRepository laneRepository;
    @Mock
    private ArcoRepository arcoRepository;

    @Spy
    private GatewayMapper gatewayMapper = Mappers.getMapper(GatewayMapper.class);

    @InjectMocks
    private GatewayServiceImpl gatewayService;

    private Empresa empresa;
    private Lane lane;
    private Gateway gateway;

    @BeforeEach
    void setUp() {
        empresa = Empresa.builder().id(EMPRESA).nombre("Demo Store").build();
        Proceso proceso = Proceso.builder().id(100L).empresa(empresa).nombre("Order fulfillment").build();
        Pool pool = Pool.builder().id(5L).empresa(empresa).proceso(proceso).nombre("Demo Store").build();
        lane = Lane.builder().id(7L).empresa(empresa).pool(pool)
                .rolProceso(RolProceso.builder().id(20L).empresa(empresa).nombre("Warehouse").build())
                .nombre("Picking").build();
        gateway = Gateway.builder().id(31L).empresa(empresa).lane(lane).nombre("Stock available?")
                .tipoGateway(TipoGateway.PARALELO).build();
    }

    @Test
    @DisplayName("HU-14: el gateway se guarda en su lane con su tipo")
    void crear_guardaEnSuLaneConSuTipo() {
        when(laneRepository.findByIdAndEmpresaId(7L, EMPRESA)).thenReturn(Optional.of(lane));
        when(nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaId("Paid?", 100L, EMPRESA))
                .thenReturn(false);
        when(gatewayRepository.save(any(Gateway.class))).thenAnswer(inv -> inv.getArgument(0));

        GatewayResponse respuesta = gatewayService.crear(EMPRESA, AUTOR, 7L, "Paid?", TipoGateway.EXCLUSIVO, 1, 2);

        assertThat(respuesta.tipoGateway()).isEqualTo(TipoGateway.EXCLUSIVO);
        assertThat(respuesta.laneId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("Un gateway no puede llamarse como otro nodo del proceso")
    void crear_conNombreRepetido_lanzaReglaNegocio() {
        when(laneRepository.findByIdAndEmpresaId(7L, EMPRESA)).thenReturn(Optional.of(lane));
        when(nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaId("Pick items", 100L,
                EMPRESA)).thenReturn(true);

        assertThatThrownBy(() -> gatewayService.crear(EMPRESA, AUTOR, 7L, "Pick items", TipoGateway.PARALELO, 0, 0))
                .isInstanceOf(ReglaNegocioException.class);
        verify(gatewayRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un gateway no pasa a exclusivo si alguna de sus salidas no tiene condicion")
    void editar_aExclusivoConSalidasSinCondicion_lanzaReglaNegocio() {
        Arco conCondicion = Arco.builder().id(50L).empresa(empresa).condicion("stock > 0").build();
        Arco sinCondicion = Arco.builder().id(51L).empresa(empresa).condicion("  ").build();
        when(gatewayRepository.findByIdAndEmpresaId(31L, EMPRESA)).thenReturn(Optional.of(gateway));
        when(nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaIdAndIdNot("Stock available?",
                100L, EMPRESA, 31L)).thenReturn(false);
        when(arcoRepository.findAllByOrigenIdAndEmpresaId(31L, EMPRESA))
                .thenReturn(List.of(conCondicion, sinCondicion));

        assertThatThrownBy(() -> gatewayService.editar(EMPRESA, AUTOR, 31L, "Stock available?",
                TipoGateway.EXCLUSIVO, null, 0, 0, null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("condicion");
        assertThat(gateway.getTipoGateway()).isEqualTo(TipoGateway.PARALELO);
        verify(gatewayRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Pasar a paralelo no exige condiciones: el paralelo sigue todas sus salidas")
    void editar_aParalelo_noExigeCondiciones() {
        gateway.setTipoGateway(TipoGateway.EXCLUSIVO);
        when(gatewayRepository.findByIdAndEmpresaId(31L, EMPRESA)).thenReturn(Optional.of(gateway));
        when(nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaIdAndIdNot("Stock available?",
                100L, EMPRESA, 31L)).thenReturn(false);
        when(gatewayRepository.saveAndFlush(any(Gateway.class))).thenAnswer(inv -> inv.getArgument(0));

        GatewayResponse respuesta = gatewayService.editar(EMPRESA, AUTOR, 31L, "Stock available?",
                TipoGateway.PARALELO, null, 0, 0, null);

        assertThat(respuesta.tipoGateway()).isEqualTo(TipoGateway.PARALELO);
        // Sus salidas no llevaban condicion, asi que no hay nada que retirar ni nada que reprochar.
        verify(arcoRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("R-34: al pasar a paralelo se retiran las condiciones de sus salidas y queda en el historial")
    void editar_pasandoAParalelo_retiraLasCondiciones() {
        Arco conCondicion = Arco.builder().id(50L).empresa(empresa).origen(gateway).condicion("stock > 0").build();
        Arco porDefecto = Arco.builder().id(51L).empresa(empresa).origen(gateway).porDefecto(true).build();
        when(gatewayRepository.findByIdAndEmpresaId(31L, EMPRESA)).thenReturn(Optional.of(gateway));
        when(nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaIdAndIdNot("Split", 100L,
                EMPRESA, 31L)).thenReturn(false);
        when(arcoRepository.findAllByOrigenIdAndEmpresaId(31L, EMPRESA))
                .thenReturn(List.of(conCondicion, porDefecto));
        when(gatewayRepository.saveAndFlush(any(Gateway.class))).thenAnswer(inv -> inv.getArgument(0));

        GatewayResponse respuesta = gatewayService.editar(EMPRESA, AUTOR, 31L, "Split", TipoGateway.PARALELO, null,
                0, 0, null);

        assertThat(respuesta.tipoGateway()).isEqualTo(TipoGateway.PARALELO);
        assertThat(conCondicion.getCondicion()).isNull();
        assertThat(porDefecto.isPorDefecto()).isFalse();
        verify(arcoRepository).saveAll(List.of(conCondicion, porDefecto));
        verify(historialCambioService).registrar(any(), any(), any(), contains("se retiraron 2 condiciones"));
    }

    @Test
    @DisplayName("R-34: un gateway que ya era paralelo se edita sin anunciar condiciones que no existen")
    void editar_paraleloSinCondiciones_seAnotaComoEdicion() {
        when(gatewayRepository.findByIdAndEmpresaId(31L, EMPRESA)).thenReturn(Optional.of(gateway));
        when(nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaIdAndIdNot("Split", 100L,
                EMPRESA, 31L)).thenReturn(false);
        when(arcoRepository.findAllByOrigenIdAndEmpresaId(31L, EMPRESA)).thenReturn(List.of());
        when(gatewayRepository.saveAndFlush(any(Gateway.class))).thenAnswer(inv -> inv.getArgument(0));

        gatewayService.editar(EMPRESA, AUTOR, 31L, "Split", TipoGateway.PARALELO, null, 0, 0, null);

        verify(historialCambioService).registrar(any(), any(), any(), contains("editado"));
    }

    @Test
    @DisplayName("HU-16: al eliminar un gateway se van los arcos que entran y los que salen de el")
    void eliminar_arrastraLosArcosQueEntranYSalen() {
        Arco saliente = Arco.builder().id(50L).empresa(empresa).build();
        when(gatewayRepository.findByIdAndEmpresaId(31L, EMPRESA)).thenReturn(Optional.of(gateway));
        when(arcoRepository.findAllByOrigenIdAndEmpresaId(31L, EMPRESA)).thenReturn(List.of(saliente));
        when(arcoRepository.findAllByDestinoIdAndEmpresaId(31L, EMPRESA)).thenReturn(List.of());

        gatewayService.eliminar(EMPRESA, AUTOR, 31L);

        verify(arcoRepository).deleteAll(List.of(saliente));
        verify(gatewayRepository).delete(gateway);
    }
}
