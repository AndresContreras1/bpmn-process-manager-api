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

import com.facimus.procesos.common.ConflictoDeVersionException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.mapper.ActividadMapper;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Evento;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.NodoFlujo;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.repository.ActividadRepository;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;
import com.facimus.procesos.modelado.service.impl.ElementosPegados;
import com.facimus.procesos.modelado.service.impl.SubprocesoServiceImpl;

/**
 * PR 56: los subprocesos. Un nodo solo entra en un subproceso expandido de su mismo pool, nunca en si mismo ni en
 * algo de dentro de el, y con flujos no cambia de contenedor; un evento de borde va con su actividad. Al moverse, el
 * nodo y lo que lleva pegado toman la lane del subproceso, y sus eventos de borde, el mismo padre. Una actividad de
 * llamada apunta a otro proceso activo de la tienda, nunca al suyo.
 */
@ExtendWith(MockitoExtension.class)
class SubprocesoServiceTest {

    private static final Long EMPRESA = 1L;
    private static final Long AUTOR = 10L;

    @Mock
    private NodoFlujoRepository nodoFlujoRepository;
    @Mock
    private ActividadRepository actividadRepository;
    @Mock
    private ArcoRepository arcoRepository;
    @Mock
    private ProcesoRepository procesoRepository;
    @Spy
    private ActividadMapper actividadMapper = Mappers.getMapper(ActividadMapper.class);
    @Mock
    private HistorialCambioService historialCambioService;
    @Mock
    private ElementosPegados pegados;

    @InjectMocks
    private SubprocesoServiceImpl subprocesoService;

    private Empresa empresa;
    private Proceso proceso;
    private Lane lane;
    private Lane laneDelSubproceso;
    private Lane laneDeOtroPool;
    private Actividad tarea;
    private Actividad subproceso;

    @BeforeEach
    void setUp() {
        empresa = Empresa.builder().id(EMPRESA).nombre("Demo Store").build();
        proceso = Proceso.builder().id(100L).empresa(empresa).nombre("Order fulfillment").build();
        Pool pool = Pool.builder().id(5L).empresa(empresa).proceso(proceso).nombre("Demo Store").build();
        Pool otroPool = Pool.builder().id(6L).empresa(empresa).proceso(proceso).nombre("Carrier").build();
        lane = Lane.builder().id(7L).empresa(empresa).pool(pool).nombre("Sales").build();
        laneDelSubproceso = Lane.builder().id(9L).empresa(empresa).pool(pool).nombre("Warehouse").build();
        laneDeOtroPool = Lane.builder().id(8L).empresa(empresa).pool(otroPool).nombre("Dispatch").build();
        tarea = Actividad.builder().id(30L).empresa(empresa).lane(lane).nombre("Pack order")
                .tipoActividad(TipoActividad.USUARIO).version(2L).build();
        subproceso = Actividad.builder().id(20L).empresa(empresa).lane(laneDelSubproceso).nombre("Handle return")
                .tipoActividad(TipoActividad.SUBPROCESO).build();
    }

    private void nodo(NodoFlujo nodo) {
        when(nodoFlujoRepository.findByIdAndEmpresaId(nodo.getId(), EMPRESA)).thenReturn(Optional.of(nodo));
    }

    private void movible(NodoFlujo nodo, List<NodoFlujo> conEl) {
        nodo(nodo);
        when(arcoRepository.tieneArcos(nodo.getId(), EMPRESA)).thenReturn(false);
        when(pegados.pegadosA(EMPRESA, nodo.getId())).thenReturn(conEl);
    }

    private Evento borde(Long id, NodoFlujo adjuntoA, NodoFlujo padre) {
        return Evento.builder().id(id).empresa(empresa).lane(lane).nombre("Timeout " + id)
                .tipoEvento(TipoEvento.TEMPORIZADOR_BORDE).adjuntoA(adjuntoA).padre(padre).build();
    }

    // ---- Mover

    @Test
    @DisplayName("Mover un nodo que no existe responde no encontrado")
    void mover_nodoInexistente_lanzaNoEncontrado() {
        when(nodoFlujoRepository.findByIdAndEmpresaId(30L, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subprocesoService.mover(EMPRESA, AUTOR, 30L, 20L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Nodo no encontrado.");
        verify(nodoFlujoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Un evento de borde no se mueve solo: va donde vaya su actividad")
    void mover_eventoDeBorde_lanzaReglaNegocio() {
        Evento evento = borde(40L, tarea, null);
        nodo(evento);

        assertThatThrownBy(() -> subprocesoService.mover(EMPRESA, AUTOR, 40L, 20L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Un evento de borde va donde vaya su actividad.");
        verify(arcoRepository, never()).tieneArcos(any(), any());
        verify(nodoFlujoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Un nodo con flujos no cambia de contenedor")
    void mover_conFlujos_lanzaReglaNegocio() {
        nodo(tarea);
        when(arcoRepository.tieneArcos(30L, EMPRESA)).thenReturn(true);

        assertThatThrownBy(() -> subprocesoService.mover(EMPRESA, AUTOR, 30L, 20L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Un nodo con flujos no cambia de contenedor: los flujos no cruzan el borde de un "
                        + "subproceso.");
        verify(pegados, never()).pegadosA(any(), any());
        verify(nodoFlujoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Un evento que no es de borde si se saca de su subproceso, y queda en el historial")
    void mover_eventoSinFlujosFueraDelSubproceso_quedaSinPadre() {
        Evento inicio = Evento.builder().id(41L).empresa(empresa).lane(laneDelSubproceso).nombre("Return received")
                .tipoEvento(TipoEvento.INICIO).padre(subproceso).build();
        movible(inicio, List.of());

        subprocesoService.mover(EMPRESA, AUTOR, 41L, null);

        assertThat(inicio.getPadre()).isNull();
        assertThat(inicio.getLane()).isSameAs(laneDelSubproceso);
        verify(historialCambioService).registrar(EMPRESA, AUTOR, proceso,
                "Nodo \"Return received\" sacado de su subproceso.");
        verify(nodoFlujoRepository).saveAndFlush(inicio);
    }

    @Test
    @DisplayName("Sacar una actividad de su subproceso saca tambien sus eventos de borde, sin cambiar de lane")
    void mover_sacarDelSubproceso_sacaSusBordes() {
        tarea.setPadre(subproceso);
        Evento suBorde = borde(42L, tarea, subproceso);
        movible(tarea, List.of(suBorde));

        subprocesoService.mover(EMPRESA, AUTOR, 30L, null);

        assertThat(tarea.getPadre()).isNull();
        assertThat(suBorde.getPadre()).isNull();
        assertThat(tarea.getLane()).isSameAs(lane);
        assertThat(suBorde.getLane()).isSameAs(lane);
        verify(historialCambioService).registrar(EMPRESA, AUTOR, proceso,
                "Nodo \"Pack order\" sacado de su subproceso.");
        verify(nodoFlujoRepository).saveAndFlush(tarea);
    }

    @Test
    @DisplayName("Al meterse en un subproceso, el nodo y lo pegado toman su lane, y solo sus bordes toman su padre")
    void mover_aUnSubproceso_llevaLoPegado() {
        Actividad otra = Actividad.builder().id(31L).empresa(empresa).lane(lane).nombre("Ship")
                .tipoActividad(TipoActividad.USUARIO).build();
        Evento suBorde = borde(42L, tarea, null);
        Evento bordeDeOtra = borde(43L, otra, null);
        Evento bordeSuelto = borde(44L, null, null);
        Evento intermedio = Evento.builder().id(45L).empresa(empresa).lane(lane).nombre("Wait")
                .tipoEvento(TipoEvento.TEMPORIZADOR_INTERMEDIO).adjuntoA(tarea).build();
        Actividad dentro = Actividad.builder().id(46L).empresa(empresa).lane(lane).nombre("Inner")
                .tipoActividad(TipoActividad.USUARIO).padre(tarea).build();
        movible(tarea, List.of(suBorde, bordeDeOtra, bordeSuelto, intermedio, dentro));
        nodo(subproceso);

        subprocesoService.mover(EMPRESA, AUTOR, 30L, 20L);

        assertThat(tarea.getPadre()).isSameAs(subproceso);
        assertThat(tarea.getLane()).isSameAs(laneDelSubproceso);
        assertThat(List.of(suBorde, bordeDeOtra, bordeSuelto, intermedio, dentro))
                .allSatisfy(pegado -> assertThat(pegado.getLane()).isSameAs(laneDelSubproceso));
        assertThat(suBorde.getPadre()).isSameAs(subproceso);
        assertThat(bordeDeOtra.getPadre()).isNull();
        assertThat(bordeSuelto.getPadre()).isNull();
        assertThat(intermedio.getPadre()).isNull();
        assertThat(dentro.getPadre()).isSameAs(tarea);
        verify(historialCambioService).registrar(EMPRESA, AUTOR, proceso,
                "Nodo \"Pack order\" movido al subproceso \"Handle return\".");
        verify(nodoFlujoRepository).saveAndFlush(tarea);
    }

    @Test
    @DisplayName("Un nodo solo entra en un subproceso expandido, no en una tarea")
    void mover_aUnaTarea_lanzaReglaNegocio() {
        Actividad otra = Actividad.builder().id(31L).empresa(empresa).lane(lane).nombre("Ship")
                .tipoActividad(TipoActividad.USUARIO).build();
        movible(tarea, List.of());
        nodo(otra);

        assertThatThrownBy(() -> subprocesoService.mover(EMPRESA, AUTOR, 30L, 31L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Un nodo solo va dentro de un subproceso expandido.");
        assertThat(tarea.getPadre()).isNull();
        verify(nodoFlujoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Un gateway no es un contenedor")
    void mover_aUnGateway_lanzaReglaNegocio() {
        Gateway gateway = Gateway.builder().id(50L).empresa(empresa).lane(lane).nombre("In stock?").build();
        movible(tarea, List.of());
        nodo(gateway);

        assertThatThrownBy(() -> subprocesoService.mover(EMPRESA, AUTOR, 30L, 50L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Un nodo solo va dentro de un subproceso expandido.");
    }

    @Test
    @DisplayName("Un contenedor que no existe se rechaza igual")
    void mover_aUnContenedorInexistente_lanzaReglaNegocio() {
        movible(tarea, List.of());
        when(nodoFlujoRepository.findByIdAndEmpresaId(99L, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subprocesoService.mover(EMPRESA, AUTOR, 30L, 99L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Un nodo solo va dentro de un subproceso expandido.");
    }

    @Test
    @DisplayName("Un subproceso no se mete dentro de si mismo")
    void mover_dentroDeSiMismo_lanzaReglaNegocio() {
        subproceso.setLane(lane);
        movible(subproceso, List.of());

        assertThatThrownBy(() -> subprocesoService.mover(EMPRESA, AUTOR, 20L, 20L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Un subproceso no se puede meter dentro de si mismo.");
        assertThat(subproceso.getPadre()).isNull();
        verify(nodoFlujoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Un subproceso no se mete en otro que lleva dentro")
    void mover_dentroDeLoQueLlevaDentro_lanzaReglaNegocio() {
        Actividad interior = Actividad.builder().id(21L).empresa(empresa).lane(laneDelSubproceso).nombre("Inner")
                .tipoActividad(TipoActividad.SUBPROCESO).padre(subproceso).build();
        Evento suBorde = borde(42L, subproceso, null);
        movible(subproceso, List.of(suBorde, interior));
        nodo(interior);

        assertThatThrownBy(() -> subprocesoService.mover(EMPRESA, AUTOR, 20L, 21L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Un subproceso no se puede meter dentro de si mismo.");
        verify(nodoFlujoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Un subproceso de otro pool no sirve de contenedor")
    void mover_aOtroPool_lanzaReglaNegocio() {
        Actividad lejano = Actividad.builder().id(22L).empresa(empresa).lane(laneDeOtroPool).nombre("Carrier flow")
                .tipoActividad(TipoActividad.SUBPROCESO).build();
        movible(tarea, List.of());
        nodo(lejano);

        assertThatThrownBy(() -> subprocesoService.mover(EMPRESA, AUTOR, 30L, 22L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El subproceso esta en otro pool.");
        assertThat(tarea.getLane()).isSameAs(lane);
        verify(nodoFlujoRepository, never()).saveAndFlush(any());
    }

    // ---- Llamar a otro proceso

    private Actividad llamada() {
        return Actividad.builder().id(60L).empresa(empresa).lane(lane).nombre("Check stock")
                .tipoActividad(TipoActividad.LLAMADA).version(4L).build();
    }

    @Test
    @DisplayName("Una actividad que no existe responde no encontrado")
    void llamarA_actividadInexistente_lanzaNoEncontrado() {
        when(actividadRepository.findByIdAndEmpresaId(60L, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subprocesoService.llamarA(EMPRESA, AUTOR, 60L, 200L, 4L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Actividad no encontrada.");
    }

    @Test
    @DisplayName("Con una version vieja responde conflicto y no cambia nada")
    void llamarA_versionVieja_lanzaConflicto() {
        Actividad actividad = llamada();
        when(actividadRepository.findByIdAndEmpresaId(60L, EMPRESA)).thenReturn(Optional.of(actividad));

        assertThatThrownBy(() -> subprocesoService.llamarA(EMPRESA, AUTOR, 60L, 200L, 3L))
                .isInstanceOf(ConflictoDeVersionException.class);
        verify(procesoRepository, never()).findByIdAndEmpresaIdAndActivoTrue(any(), any());
        verify(actividadRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Solo una actividad de llamada corre otro proceso")
    void llamarA_noEsDeLlamada_lanzaReglaNegocio() {
        when(actividadRepository.findByIdAndEmpresaId(30L, EMPRESA)).thenReturn(Optional.of(tarea));

        assertThatThrownBy(() -> subprocesoService.llamarA(EMPRESA, AUTOR, 30L, 200L, 2L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Solo una actividad de llamada corre otro proceso.");
        verify(actividadRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Llamar a otro proceso activo de la tienda lo guarda y queda en el historial")
    void llamarA_otroProceso_loGuarda() {
        Actividad actividad = llamada();
        Proceso devoluciones = Proceso.builder().id(200L).empresa(empresa).nombre("Returns").build();
        when(actividadRepository.findByIdAndEmpresaId(60L, EMPRESA)).thenReturn(Optional.of(actividad));
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(200L, EMPRESA)).thenReturn(Optional.of(devoluciones));
        when(actividadRepository.saveAndFlush(actividad)).thenReturn(actividad);

        ActividadResponse respuesta = subprocesoService.llamarA(EMPRESA, AUTOR, 60L, 200L, 4L);

        assertThat(actividad.getProcesoLlamado()).isSameAs(devoluciones);
        assertThat(respuesta.procesoLlamadoId()).isEqualTo(200L);
        verify(historialCambioService).registrar(EMPRESA, AUTOR, proceso,
                "Actividad \"Check stock\" llama a \"Returns\".");
    }

    @Test
    @DisplayName("Sin proceso, la actividad deja de llamar y queda en el historial")
    void llamarA_sinProceso_dejaDeLlamar() {
        Actividad actividad = llamada();
        actividad.setProcesoLlamado(Proceso.builder().id(200L).nombre("Returns").build());
        when(actividadRepository.findByIdAndEmpresaId(60L, EMPRESA)).thenReturn(Optional.of(actividad));
        when(actividadRepository.saveAndFlush(actividad)).thenReturn(actividad);

        ActividadResponse respuesta = subprocesoService.llamarA(EMPRESA, AUTOR, 60L, null, 4L);

        assertThat(actividad.getProcesoLlamado()).isNull();
        assertThat(respuesta).isNotNull();
        assertThat(respuesta.procesoLlamadoId()).isNull();
        verify(procesoRepository, never()).findByIdAndEmpresaIdAndActivoTrue(any(), any());
        verify(historialCambioService).registrar(EMPRESA, AUTOR, proceso,
                "Actividad \"Check stock\" sin proceso que llamar.");
    }

    @Test
    @DisplayName("Un proceso que no esta activo en la tienda no se puede llamar")
    void llamarA_procesoInexistente_lanzaNoEncontrado() {
        when(actividadRepository.findByIdAndEmpresaId(60L, EMPRESA)).thenReturn(Optional.of(llamada()));
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(200L, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subprocesoService.llamarA(EMPRESA, AUTOR, 60L, 200L, 4L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Proceso no encontrado.");
        verify(actividadRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Una actividad no llama a su propio proceso")
    void llamarA_propioProceso_lanzaReglaNegocio() {
        Actividad actividad = llamada();
        when(actividadRepository.findByIdAndEmpresaId(60L, EMPRESA)).thenReturn(Optional.of(actividad));
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(100L, EMPRESA)).thenReturn(Optional.of(proceso));

        assertThatThrownBy(() -> subprocesoService.llamarA(EMPRESA, AUTOR, 60L, 100L, 4L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Una actividad no puede llamar a su propio proceso.");
        assertThat(actividad.getProcesoLlamado()).isNull();
        verify(actividadRepository, never()).saveAndFlush(any());
    }
}
