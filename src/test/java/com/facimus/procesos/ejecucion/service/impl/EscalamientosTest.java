package com.facimus.procesos.ejecucion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.Delegacion;
import com.facimus.procesos.ejecucion.model.EstadoDelegacion;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.model.TipoNodoCaso;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.repository.CasoRepository;
import com.facimus.procesos.ejecucion.repository.DelegacionRepository;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.modelado.dto.response.EscalamientoResponse;
import com.facimus.procesos.modelado.model.AccionDeEscalamiento;

/** PR 71: lo que hace un borde que no interrumpe con la tarea al saltar: avisar, reasignar, o decir por que no. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EscalamientosTest {

    private static final Long TIENDA = 1L;
    private static final Long DUENO = 2L;
    private static final Long ANA = 5L;
    private static final Long SUPERVISORA = 7L;
    private static final int TICK = 4;
    private static final Momento MOMENTO = Momento.en(TICK);

    @Mock
    private ActividadCasoRepository actividadCasoRepository;
    @Mock
    private CasoRepository casoRepository;
    @Mock
    private DelegacionRepository delegacionRepository;
    @Mock
    private PersonasDeLaTarea personas;
    @Mock
    private Bitacora bitacora;
    @Mock
    private Auditoria auditoria;
    @Mock
    private AvisosDePlazo avisos;

    private final GrafoDeVersion grafo = mock(GrafoDeVersion.class);
    private final NodoDeLaVersion borde = new NodoDeLaVersion(50L, "Six hours", TipoNodoCaso.EVENTO,
            "TEMPORIZADOR_BORDE", 7L, null);
    private Escalamientos escalamientos;
    private Proceso proceso;
    private Caso caso;
    private ActividadCaso tarea;

    @BeforeEach
    void unaTareaConSuBorde() {
        escalamientos = new Escalamientos(actividadCasoRepository, personas,
                new Tareas(actividadCasoRepository, casoRepository, delegacionRepository), bitacora, auditoria, avisos);
        Empresa tienda = Empresa.builder().id(TIENDA).build();
        proceso = Proceso.builder().id(100L).empresa(tienda).nombre("Refunds").duenoId(DUENO).build();
        caso = Caso.builder().id(42L).empresa(tienda).proceso(proceso).build();
        tarea = ActividadCaso.builder().id(77L).empresa(tienda).caso(caso).nodoNombre("Approve the refund")
                .tipoNodo(TipoNodoCaso.ACTIVIDAD).subtipo("USUARIO").asignadoA(ANA).build();
        given(personas.siPuedeTenerla(TIENDA, SUPERVISORA)).willReturn(Optional.of(SUPERVISORA));
        given(personas.siPuedeTenerla(TIENDA, DUENO)).willReturn(Optional.of(DUENO));
        given(grafo.escalamientoDe(any())).willReturn(Optional.empty());
    }

    @Test
    @DisplayName("Un borde sin escalamiento solo abre su camino: nada se escribe ni se avisa")
    void sinEscalamiento() {
        escalamientos.alSaltar(caso, grafo, tarea, borde, MOMENTO);

        verifyNoInteractions(bitacora, avisos, auditoria, actividadCasoRepository);
    }

    @Test
    @DisplayName("AVISAR le avisa a quien la tiene y al supervisor; la tarea sigue con quien la tenia")
    void avisar() {
        escala(AccionDeEscalamiento.AVISAR, SUPERVISORA);

        escalamientos.alSaltar(caso, grafo, tarea, borde, MOMENTO);

        assertThat(tarea.getAsignadoA()).isEqualTo(ANA);
        verify(bitacora).anotar(caso, TICK, TipoEventoCaso.TAREA_ESCALADA,
                "\"Six hours\" escala \"Approve the refund\": se le avisa al usuario 7, que la supervisa.");
        verify(avisos).deUnEscalamiento(caso, Arrays.asList(ANA, SUPERVISORA));
        verify(actividadCasoRepository, never()).save(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("Sin supervisor en el evento, el supervisor es el dueno del proceso")
    void supervisor_esElDueno() {
        escala(AccionDeEscalamiento.AVISAR, null);

        escalamientos.alSaltar(caso, grafo, tarea, borde, MOMENTO);

        verify(avisos).deUnEscalamiento(caso, Arrays.asList(ANA, DUENO));
    }

    @Test
    @DisplayName("Sin supervisor y sin dueno no tiene a quien escalar: la linea de tiempo lo dice y nadie recibe nada")
    void sinNadieAQuien() {
        escala(AccionDeEscalamiento.REASIGNAR, null);
        proceso.setDuenoId(null);

        escalamientos.alSaltar(caso, grafo, tarea, borde, MOMENTO);

        assertThat(tarea.getAsignadoA()).isEqualTo(ANA);
        verify(bitacora).anotar(caso, TICK, TipoEventoCaso.TAREA_ESCALADA, "\"Six hours\" escala \"Approve the "
                + "refund\", pero no tiene a quien: el evento no dice un supervisor y el proceso no tiene dueno.");
        verifyNoInteractions(avisos, auditoria);
    }

    @Test
    @DisplayName("REASIGNAR le pasa la tarea al supervisor, anula la delegacion, y queda en la bitacora como del sistema")
    void reasignar() {
        escala(AccionDeEscalamiento.REASIGNAR, SUPERVISORA);
        tarea.setDelegadaPor(3L);
        Delegacion abierta = Delegacion.builder().id(9L).estado(EstadoDelegacion.ACTIVA).build();
        given(delegacionRepository.findFirstByTareaIdAndEmpresaIdAndEstado(77L, TIENDA, EstadoDelegacion.ACTIVA))
                .willReturn(Optional.of(abierta));

        escalamientos.alSaltar(caso, grafo, tarea, borde, MOMENTO);

        assertThat(tarea.getAsignadoA()).isEqualTo(SUPERVISORA);
        assertThat(tarea.getDelegadaPor()).isNull();
        assertThat(abierta.getEstado()).isEqualTo(EstadoDelegacion.ANULADA);
        verify(actividadCasoRepository).save(tarea);
        verify(bitacora).anotar(caso, TICK, TipoEventoCaso.TAREA_ESCALADA,
                "\"Six hours\" escala \"Approve the refund\": pasa del usuario 5 al usuario 7, que la supervisa.");
        verify(auditoria).registrar(TIENDA, null, Escalamientos.ESCALADA, TareaServiceImpl.RECURSO, 77L,
                Map.of(AsignacionDeTareaServiceImpl.CASO_ID, 42L, "de", ANA, "a", SUPERVISORA));
        verify(avisos).deUnEscalamiento(caso, Arrays.asList(ANA, SUPERVISORA));
    }

    @Test
    @DisplayName("REASIGNAR una que nadie tenia la saca de la bandeja de su rol")
    void reasignar_desdeLaBandeja() {
        escala(AccionDeEscalamiento.REASIGNAR, SUPERVISORA);
        tarea.setAsignadoA(null);

        escalamientos.alSaltar(caso, grafo, tarea, borde, MOMENTO);

        assertThat(tarea.getAsignadoA()).isEqualTo(SUPERVISORA);
        verify(bitacora).anotar(caso, TICK, TipoEventoCaso.TAREA_ESCALADA, "\"Six hours\" escala \"Approve the "
                + "refund\": pasa de la bandeja de su rol al usuario 7, que la supervisa.");
        verify(auditoria).registrar(TIENDA, null, Escalamientos.ESCALADA, TareaServiceImpl.RECURSO, 77L,
                Map.of(AsignacionDeTareaServiceImpl.CASO_ID, 42L, "a", SUPERVISORA));
        verify(avisos).deUnEscalamiento(caso, Arrays.asList(null, SUPERVISORA));
    }

    @Test
    @DisplayName("REASIGNAR a quien no puede tener tareas no se la pasa: se le avisa, y la linea de tiempo dice por que")
    void reasignar_aQuienNoPuede() {
        escala(AccionDeEscalamiento.REASIGNAR, SUPERVISORA);
        given(personas.siPuedeTenerla(TIENDA, SUPERVISORA)).willReturn(Optional.empty());

        escalamientos.alSaltar(caso, grafo, tarea, borde, MOMENTO);

        assertThat(tarea.getAsignadoA()).isEqualTo(ANA);
        verify(bitacora).anotar(caso, TICK, TipoEventoCaso.TAREA_ESCALADA, "\"Six hours\" escala \"Approve the "
                + "refund\": no se le pasa al usuario 7 porque no puede tener tareas: esta desactivado o solo lee; "
                + "se le avisa.");
        verify(avisos).deUnEscalamiento(caso, Arrays.asList(ANA, SUPERVISORA));
        verifyNoInteractions(auditoria);
        verify(actividadCasoRepository, never()).save(any());
    }

    @Test
    @DisplayName("REASIGNAR sobre una actividad que no es tarea, o que ya tiene el supervisor, solo avisa")
    void reasignar_queNoSePuede() {
        escala(AccionDeEscalamiento.REASIGNAR, ANA);

        escalamientos.alSaltar(caso, grafo, tarea, borde, MOMENTO);
        verify(bitacora).anotar(caso, TICK, TipoEventoCaso.TAREA_ESCALADA, "\"Six hours\" escala \"Approve the "
                + "refund\": no se le pasa al usuario 5 porque ya la tiene; se le avisa.");

        tarea.setSubtipo("RECEPCION");
        escala(AccionDeEscalamiento.REASIGNAR, SUPERVISORA);
        escalamientos.alSaltar(caso, grafo, tarea, borde, MOMENTO);
        verify(bitacora).anotar(caso, TICK, TipoEventoCaso.TAREA_ESCALADA, "\"Six hours\" escala \"Approve the "
                + "refund\": no se le pasa al usuario 7 porque no es una tarea de usuario; se le avisa.");
        verify(personas, never()).siPuedeTenerla(anyLong(), any());
        assertThat(tarea.getAsignadoA()).isEqualTo(ANA);
        verifyNoInteractions(auditoria);
        verify(avisos).deUnEscalamiento(caso, List.of(ANA, SUPERVISORA));
    }

    private void escala(AccionDeEscalamiento accion, Long supervisor) {
        given(grafo.escalamientoDe(50L)).willReturn(Optional.of(new EscalamientoResponse(accion, supervisor)));
    }
}
