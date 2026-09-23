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

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.mapper.ActividadMapper;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.repository.ActividadRepository;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;
import com.facimus.procesos.modelado.service.impl.ActividadServiceImpl;

/** HU-08 a HU-10: las tareas del proceso. */
@ExtendWith(MockitoExtension.class)
class ActividadServiceTest {

    private static final Long EMPRESA = 1L;
    private static final Long AUTOR = 10L;

    @Mock
    private HistorialCambioService historialCambioService;
    @Mock
    private ActividadRepository actividadRepository;
    @Mock
    private NodoFlujoRepository nodoFlujoRepository;
    @Mock
    private LaneRepository laneRepository;
    @Mock
    private ArcoRepository arcoRepository;

    @Spy
    private ActividadMapper actividadMapper = Mappers.getMapper(ActividadMapper.class);

    @InjectMocks
    private ActividadServiceImpl actividadService;

    private Empresa empresa;
    private Proceso proceso;
    private Lane lane;
    private Actividad actividad;

    @BeforeEach
    void setUp() {
        empresa = Empresa.builder().id(EMPRESA).nombre("Demo Store").build();
        proceso = Proceso.builder().id(100L).empresa(empresa).nombre("Order fulfillment").build();
        Pool pool = Pool.builder().id(5L).empresa(empresa).proceso(proceso).nombre("Demo Store").build();
        lane = Lane.builder().id(7L).empresa(empresa).pool(pool)
                .rolProceso(RolProceso.builder().id(20L).empresa(empresa).nombre("Warehouse").build())
                .nombre("Picking").build();
        actividad = Actividad.builder().id(30L).empresa(empresa).lane(lane).nombre("Pick items")
                .tipoActividad(TipoActividad.USUARIO).build();
    }

    @Test
    @DisplayName("HU-08: la actividad se guarda en su lane y queda anotada en el historial")
    void crear_guardaEnSuLaneYQuedaEnElHistorial() {
        when(laneRepository.findByIdAndEmpresaId(7L, EMPRESA)).thenReturn(Optional.of(lane));
        when(nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaId("Pack order", 100L,
                EMPRESA)).thenReturn(false);
        when(actividadRepository.save(any(Actividad.class))).thenAnswer(inv -> inv.getArgument(0));

        ActividadResponse respuesta = actividadService.crear(EMPRESA, AUTOR, 7L, "Pack order", "Empacar",
                TipoActividad.ENVIO, 40, 50);

        assertThat(respuesta.laneId()).isEqualTo(7L);
        assertThat(respuesta.tipoActividad()).isEqualTo(TipoActividad.ENVIO);
        assertThat(respuesta.posicionX()).isEqualTo(40);
        verify(historialCambioService).registrar(eq(EMPRESA), eq(AUTOR), eq(proceso), contains("Pack order"));
    }

    @Test
    @DisplayName("Una actividad creada sin tipo es trabajo de una persona del rol de su lane")
    void crear_sinTipo_quedaComoActividadDeUsuario() {
        when(laneRepository.findByIdAndEmpresaId(7L, EMPRESA)).thenReturn(Optional.of(lane));
        when(nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaId("Pack order", 100L,
                EMPRESA)).thenReturn(false);
        when(actividadRepository.save(any(Actividad.class))).thenAnswer(inv -> inv.getArgument(0));

        ActividadResponse respuesta = actividadService.crear(EMPRESA, AUTOR, 7L, "Pack order", "Empacar", null,
                40, 50);

        assertThat(respuesta.tipoActividad()).isEqualTo(TipoActividad.USUARIO);
    }

    @Test
    @DisplayName("Dos nodos del mismo proceso no pueden llamarse igual")
    void crear_conNombreRepetido_lanzaReglaNegocio() {
        when(laneRepository.findByIdAndEmpresaId(7L, EMPRESA)).thenReturn(Optional.of(lane));
        when(nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaId("Pick items", 100L,
                EMPRESA)).thenReturn(true);

        assertThatThrownBy(() -> actividadService.crear(EMPRESA, AUTOR, 7L, "Pick items", "Recoger",
                TipoActividad.USUARIO, 0, 0))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Pick items");
        verify(actividadRepository, never()).save(any());
    }

    @Test
    @DisplayName("Al renombrar, el choque de nombres no cuenta el nombre propio de la actividad")
    void editar_conservandoSuNombre_noChocaConSigoMisma() {
        when(actividadRepository.findByIdAndEmpresaId(30L, EMPRESA)).thenReturn(Optional.of(actividad));
        when(nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaIdAndIdNot("Pick items", 100L,
                EMPRESA, 30L)).thenReturn(false);
        when(actividadRepository.saveAndFlush(any(Actividad.class))).thenAnswer(inv -> inv.getArgument(0));

        ActividadResponse respuesta = actividadService.editar(EMPRESA, AUTOR, 30L, "Pick items", "Otra",
                TipoActividad.SERVICIO, 1, 2, null);

        assertThat(respuesta.descripcion()).isEqualTo("Otra");
        assertThat(actividad.getTipoActividad()).isEqualTo(TipoActividad.SERVICIO);
        assertThat(actividad.getPosicionY()).isEqualTo(2);
    }

    @Test
    @DisplayName("HU-10: al eliminar una actividad se van los arcos que entran y los que salen de ella")
    void eliminar_arrastraLosArcosQueEntranYSalen() {
        Arco saliente = Arco.builder().id(50L).empresa(empresa).build();
        Arco entrante = Arco.builder().id(51L).empresa(empresa).build();
        when(actividadRepository.findByIdAndEmpresaId(30L, EMPRESA)).thenReturn(Optional.of(actividad));
        when(arcoRepository.findAllByOrigenIdAndEmpresaId(30L, EMPRESA)).thenReturn(List.of(saliente));
        when(arcoRepository.findAllByDestinoIdAndEmpresaId(30L, EMPRESA)).thenReturn(List.of(entrante));

        actividadService.eliminar(EMPRESA, AUTOR, 30L);

        verify(arcoRepository).deleteAll(List.of(saliente));
        verify(arcoRepository).deleteAll(List.of(entrante));
        verify(actividadRepository).delete(actividad);
        verify(historialCambioService).registrar(eq(EMPRESA), eq(AUTOR), eq(proceso), contains("eliminada"));
    }

    @Test
    @DisplayName("Una actividad de otra tienda no existe para esta")
    void obtener_deOtraTienda_lanzaNoEncontrado() {
        when(actividadRepository.findByIdAndEmpresaId(30L, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> actividadService.obtener(EMPRESA, 30L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
