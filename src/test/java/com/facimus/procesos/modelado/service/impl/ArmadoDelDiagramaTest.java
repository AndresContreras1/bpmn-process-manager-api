package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.mapper.ActividadMapper;
import com.facimus.procesos.modelado.mapper.ArcoMapper;
import com.facimus.procesos.modelado.mapper.CorrelacionMapper;
import com.facimus.procesos.modelado.mapper.EventoMapper;
import com.facimus.procesos.modelado.mapper.GatewayMapper;
import com.facimus.procesos.modelado.mapper.LaneMapper;
import com.facimus.procesos.modelado.mapper.MensajeMapper;
import com.facimus.procesos.modelado.mapper.PoolMapper;
import com.facimus.procesos.modelado.repository.ActividadRepository;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.CorrelacionRepository;
import com.facimus.procesos.modelado.repository.EventoRepository;
import com.facimus.procesos.modelado.repository.GatewayRepository;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;

/** Los elementos son de la empresa duena del proceso, que en uno compartido no es la de quien lo lee (HU-23). */
@ExtendWith(MockitoExtension.class)
class ArmadoDelDiagramaTest {

    private static final Long DUENA = 9L;
    private static final Long PROCESO = 100L;

    @Mock
    private PoolRepository poolRepository;
    @Mock
    private LaneRepository laneRepository;
    @Mock
    private ActividadRepository actividadRepository;
    @Mock
    private GatewayRepository gatewayRepository;
    @Mock
    private EventoRepository eventoRepository;
    @Mock
    private ArcoRepository arcoRepository;
    @Mock
    private MensajeRepository mensajeRepository;
    @Mock
    private CorrelacionRepository correlacionRepository;

    @Spy
    private PoolMapper poolMapper = Mappers.getMapper(PoolMapper.class);
    @Spy
    private LaneMapper laneMapper = Mappers.getMapper(LaneMapper.class);
    @Spy
    private ActividadMapper actividadMapper = Mappers.getMapper(ActividadMapper.class);
    @Spy
    private GatewayMapper gatewayMapper = Mappers.getMapper(GatewayMapper.class);
    @Spy
    private EventoMapper eventoMapper = Mappers.getMapper(EventoMapper.class);
    @Spy
    private ArcoMapper arcoMapper = Mappers.getMapper(ArcoMapper.class);
    @Spy
    private MensajeMapper mensajeMapper = Mappers.getMapper(MensajeMapper.class);
    @Spy
    private CorrelacionMapper correlacionMapper = Mappers.getMapper(CorrelacionMapper.class);

    @InjectMocks
    private ArmadoDelDiagrama armado;

    @Test
    @DisplayName("Cada elemento se consulta con el proceso y la empresa duena que recibe")
    void armar_consultaTodoConLaTiendaDuena() {
        when(poolRepository.findAllByProcesoIdAndEmpresaIdOrderByOrdenAsc(PROCESO, DUENA)).thenReturn(List.of());

        DiagramaResponse diagrama = armado.armar(proceso(), true, DUENA);

        assertThat(diagrama.compartido()).isTrue();
        assertThat(diagrama.proceso().nombre()).isEqualTo("Order fulfillment");
        assertThat(diagrama.pools()).isEmpty();
        verify(laneRepository).delProcesoEnOrden(PROCESO, DUENA);
        verify(actividadRepository).findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, DUENA);
        verify(gatewayRepository).findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, DUENA);
        verify(eventoRepository).findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, DUENA);
        verify(arcoRepository).findAllByPool_ProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, DUENA);
        verify(mensajeRepository).findAllByProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, DUENA);
        verify(correlacionRepository).findAllByMensaje_ProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, DUENA);
    }

    private static ProcesoResponse proceso() {
        LocalDateTime ahora = LocalDateTime.now();
        return new ProcesoResponse(PROCESO, "Order fulfillment", "De la compra a la entrega", "Fulfillment",
                EstadoProceso.PUBLICADO, true, ahora, ahora, 0L, null, null, 1, false);
    }
}
