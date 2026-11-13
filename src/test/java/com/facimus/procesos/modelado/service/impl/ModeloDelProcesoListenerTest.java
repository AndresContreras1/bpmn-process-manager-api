package com.facimus.procesos.modelado.service.impl;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.gestion.event.ProcesoEliminado;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.Artefacto;
import com.facimus.procesos.modelado.model.Asociacion;
import com.facimus.procesos.modelado.model.Correlacion;
import com.facimus.procesos.modelado.model.Evento;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.repository.ActividadRepository;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.ArtefactoRepository;
import com.facimus.procesos.modelado.repository.AsociacionRepository;
import com.facimus.procesos.modelado.repository.CorrelacionRepository;
import com.facimus.procesos.modelado.repository.EventoRepository;
import com.facimus.procesos.modelado.repository.GatewayRepository;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;

/**
 * HU-06: al eliminar un proceso, su modelo se da de baja entero y de hijos a padres, para que nada activo quede
 * apuntando a algo dado de baja.
 */
@ExtendWith(MockitoExtension.class)
class ModeloDelProcesoListenerTest {

    private static final Long EMPRESA = 7L;
    private static final Long PROCESO = 10L;

    @Mock
    private CorrelacionRepository correlacionRepository;
    @Mock
    private MensajeRepository mensajeRepository;
    @Mock
    private ArcoRepository arcoRepository;
    @Mock
    private ActividadRepository actividadRepository;
    @Mock
    private GatewayRepository gatewayRepository;
    @Mock
    private EventoRepository eventoRepository;
    @Mock
    private LaneRepository laneRepository;
    @Mock
    private PoolRepository poolRepository;
    @Mock
    private ArtefactoRepository artefactoRepository;
    @Mock
    private AsociacionRepository asociacionRepository;

    @InjectMocks
    private ModeloDelProcesoListener listener;

    @Test
    @DisplayName("Da de baja asociaciones, artefactos, mensajes, flujos, nodos, lanes y pools, en ese orden")
    void darDeBajaElModelo() {
        List<Asociacion> asociaciones = List.of(mock(Asociacion.class));
        List<Artefacto> artefactos = List.of(mock(Artefacto.class));
        List<Correlacion> correlaciones = List.of(mock(Correlacion.class));
        List<Mensaje> mensajes = List.of(mock(Mensaje.class));
        List<Arco> arcos = List.of(mock(Arco.class));
        List<Actividad> actividades = List.of(mock(Actividad.class));
        List<Gateway> gateways = List.of(mock(Gateway.class));
        List<Evento> eventos = List.of(mock(Evento.class));
        List<Lane> lanes = List.of(mock(Lane.class));
        List<Pool> pools = List.of(mock(Pool.class));
        when(asociacionRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, EMPRESA)).thenReturn(asociaciones);
        when(artefactoRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, EMPRESA)).thenReturn(artefactos);
        when(correlacionRepository.findAllByMensaje_ProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, EMPRESA))
                .thenReturn(correlaciones);
        when(mensajeRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, EMPRESA)).thenReturn(mensajes);
        when(arcoRepository.findAllByPool_ProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, EMPRESA)).thenReturn(arcos);
        when(actividadRepository.findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, EMPRESA))
                .thenReturn(actividades);
        when(gatewayRepository.findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, EMPRESA))
                .thenReturn(gateways);
        when(eventoRepository.findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, EMPRESA))
                .thenReturn(eventos);
        when(laneRepository.delProcesoEnOrden(PROCESO, EMPRESA)).thenReturn(lanes);
        when(poolRepository.findAllByProcesoIdAndEmpresaIdOrderByOrdenAsc(PROCESO, EMPRESA)).thenReturn(pools);

        listener.darDeBajaElModelo(new ProcesoEliminado(EMPRESA, PROCESO));

        InOrder orden = inOrder(asociacionRepository, artefactoRepository, correlacionRepository, mensajeRepository,
                arcoRepository, actividadRepository, gatewayRepository, eventoRepository, laneRepository,
                poolRepository);
        orden.verify(asociacionRepository).deleteAll(asociaciones);
        orden.verify(artefactoRepository).deleteAll(artefactos);
        orden.verify(correlacionRepository).deleteAll(correlaciones);
        orden.verify(mensajeRepository).deleteAll(mensajes);
        orden.verify(arcoRepository).deleteAll(arcos);
        orden.verify(actividadRepository).deleteAll(actividades);
        orden.verify(gatewayRepository).deleteAll(gateways);
        orden.verify(eventoRepository).deleteAll(eventos);
        orden.verify(laneRepository).deleteAll(lanes);
        orden.verify(poolRepository).deleteAll(pools);
    }
}
