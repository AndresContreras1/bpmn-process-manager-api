package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.Asociacion;
import com.facimus.procesos.modelado.model.Evento;
import com.facimus.procesos.modelado.model.NodoFlujo;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.AsociacionRepository;
import com.facimus.procesos.modelado.repository.EventoRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;

/**
 * PR 55 y 56: lo que va pegado a una actividad se va con ella. La actividad 1 tiene el evento de borde 2 y, por ser un
 * subproceso, la actividad 3 adentro, que a su vez lleva el evento 4.
 */
@ExtendWith(MockitoExtension.class)
class ElementosPegadosTest {

    private static final Long EMPRESA = 7L;
    private static final Long PROCESO = 10L;

    @Mock
    private AsociacionRepository asociacionRepository;
    @Mock
    private EventoRepository eventoRepository;
    @Mock
    private ArcoRepository arcoRepository;
    @Mock
    private NodoFlujoRepository nodoFlujoRepository;

    @InjectMocks
    private ElementosPegados pegados;

    private final Actividad subproceso = Actividad.builder().id(1L).bpmnId("Activity_1").build();
    private final Evento borde = Evento.builder().id(2L).bpmnId("Event_2").build();
    private final Actividad hija = Actividad.builder().id(3L).bpmnId("Activity_3").build();
    private final Evento nieto = Evento.builder().id(4L).bpmnId("Event_4").build();

    @Test
    @DisplayName("Borrar una actividad se lleva sus bordes con sus flujos, su contenido y las asociaciones de todos")
    void borrarConLaActividad() {
        conElArbol();
        List<Arco> delBorde = List.of(Arco.builder().id(20L).build());
        List<Arco> deLaHija = List.of(Arco.builder().id(30L).build());
        List<Arco> alNieto = List.of(Arco.builder().id(40L).build());
        when(arcoRepository.findAllByOrigenIdAndEmpresaId(2L, EMPRESA)).thenReturn(delBorde);
        when(arcoRepository.findAllByOrigenIdAndEmpresaId(3L, EMPRESA)).thenReturn(deLaHija);
        when(arcoRepository.findAllByOrigenIdAndEmpresaId(4L, EMPRESA)).thenReturn(List.of());
        when(arcoRepository.findAllByDestinoIdAndEmpresaId(4L, EMPRESA)).thenReturn(alNieto);
        List<Asociacion> delBordeA = List.of(Asociacion.builder().id(50L).build());
        List<Asociacion> delSubproceso = List.of(Asociacion.builder().id(51L).build());
        List<Asociacion> deLaHijaA = List.of(Asociacion.builder().id(52L).build());
        List<Asociacion> delNieto = List.of(Asociacion.builder().id(53L).build());
        when(asociacionRepository.queTocan(EMPRESA, PROCESO, "Event_2")).thenReturn(delBordeA);
        when(asociacionRepository.queTocan(EMPRESA, PROCESO, "Activity_1")).thenReturn(delSubproceso);
        when(asociacionRepository.queTocan(EMPRESA, PROCESO, "Activity_3")).thenReturn(deLaHijaA);
        when(asociacionRepository.queTocan(EMPRESA, PROCESO, "Event_4")).thenReturn(delNieto);

        pegados.borrarConLaActividad(EMPRESA, PROCESO, subproceso);

        verify(arcoRepository).deleteAll(delBorde);
        verify(eventoRepository).delete(borde);
        verify(arcoRepository).deleteAll(deLaHija);
        verify(arcoRepository).deleteAll(alNieto);
        verify(asociacionRepository).deleteAll(delBordeA);
        verify(asociacionRepository).deleteAll(delSubproceso);
        verify(asociacionRepository).deleteAll(deLaHijaA);
        verify(asociacionRepository).deleteAll(delNieto);
        verify(nodoFlujoRepository).delete(hija);
        verify(nodoFlujoRepository).delete(nieto);
        verify(nodoFlujoRepository, never()).delete(subproceso);
    }

    @Test
    @DisplayName("Un elemento sin bpmn_id no tiene asociaciones que soltar")
    void sinBpmnId() {
        pegados.soltarAsociaciones(EMPRESA, PROCESO, null);

        verify(asociacionRepository, never()).queTocan(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("Lo pegado a una actividad son sus bordes y su contenido, a cualquier profundidad")
    void pegadosA() {
        conElArbol();

        assertThat(pegados.pegadosA(EMPRESA, 1L)).containsExactly(borde, hija, nieto);
        assertThat(pegados.bordesDe(EMPRESA, 1L)).containsExactly(borde);
    }

    private void conElArbol() {
        when(eventoRepository.findAllByAdjuntoA_IdAndEmpresaIdOrderByIdAsc(1L, EMPRESA)).thenReturn(List.of(borde));
        when(nodoFlujoRepository.findAllByPadre_IdAndEmpresaIdOrderByIdAsc(1L, EMPRESA))
                .thenReturn(List.<NodoFlujo>of(hija));
        when(nodoFlujoRepository.findAllByPadre_IdAndEmpresaIdOrderByIdAsc(3L, EMPRESA))
                .thenReturn(List.<NodoFlujo>of(nieto));
    }
}
