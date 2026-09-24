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

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.repository.RolProcesoRepository;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.LaneResponse;
import com.facimus.procesos.modelado.mapper.LaneMapper;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;
import com.facimus.procesos.modelado.service.impl.LaneServiceImpl;

/** HU-22 y HU-24: las lanes de un pool y el rol responsable de cada una. */
@ExtendWith(MockitoExtension.class)
class LaneServiceTest {

    private static final Long EMPRESA = 1L;
    private static final Long AUTOR = 10L;

    @Mock
    private HistorialCambioService historialCambioService;
    @Mock
    private LaneRepository laneRepository;
    @Mock
    private PoolRepository poolRepository;
    @Mock
    private RolProcesoRepository rolProcesoRepository;
    @Mock
    private NodoFlujoRepository nodoFlujoRepository;

    @Spy
    private LaneMapper laneMapper = Mappers.getMapper(LaneMapper.class);

    @Mock
    private ConfiguracionTiendaService configuracionTiendaService;

    @InjectMocks
    private LaneServiceImpl laneService;

    private Empresa empresa;
    private Proceso proceso;
    private Pool pool;
    private RolProceso almacen;
    private Lane lane;

    @BeforeEach
    void setUp() {
        empresa = Empresa.builder().id(EMPRESA).nombre("Demo Store").build();
        proceso = Proceso.builder().id(100L).empresa(empresa).nombre("Order fulfillment").build();
        pool = Pool.builder().id(5L).empresa(empresa).proceso(proceso).nombre("Demo Store").build();
        almacen = RolProceso.builder().id(20L).empresa(empresa).nombre("Warehouse").build();
        lane = Lane.builder().id(7L).empresa(empresa).pool(pool).rolProceso(almacen).nombre("Picking").orden(0)
                .build();
    }

    @Test
    @DisplayName("R-33: una caja negra no se modela por dentro, asi que no admite lanes")
    void crear_dentroDeUnPoolDeCajaNegra_lanzaReglaNegocio() {
        pool.setCajaNegra(true);
        when(poolRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(pool));

        assertThatThrownBy(() -> laneService.crear(EMPRESA, AUTOR, 5L, "Routing", 20L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Un pool de caja negra no puede tener lanes.");
        verify(laneRepository, never()).save(any());
    }

    @Test
    @DisplayName("HU-22: la lane nueva va al final del pool, con su rol y su anotacion en el historial")
    void crear_vaAlFinalDelPoolConSuRol() {
        when(poolRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(pool));
        when(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(20L, EMPRESA)).thenReturn(Optional.of(almacen));
        when(laneRepository.siguienteOrden(5L, EMPRESA)).thenReturn(1);
        when(laneRepository.save(any(Lane.class))).thenAnswer(inv -> inv.getArgument(0));

        LaneResponse respuesta = laneService.crear(EMPRESA, AUTOR, 5L, "Packing", 20L);

        assertThat(respuesta.orden()).isEqualTo(1);
        assertThat(respuesta.rolProcesoId()).isEqualTo(20L);
        assertThat(respuesta.rolProcesoNombre()).isEqualTo("Warehouse");
        verify(historialCambioService).registrar(eq(EMPRESA), eq(AUTOR), eq(proceso), contains("Packing"));
    }

    @Test
    @DisplayName("HU-19: un rol eliminado ya no se asigna a una lane")
    void crear_conRolEliminado_lanzaNoEncontrado() {
        when(poolRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(pool));
        when(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(20L, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> laneService.crear(EMPRESA, AUTOR, 5L, "Packing", 20L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Rol de proceso");
        verify(laneRepository, never()).save(any());
    }

    @Test
    @DisplayName("Editar una lane cambia su rol responsable, que tambien tiene que estar activo")
    void editar_cambiaElRolResponsable() {
        RolProceso auditor = RolProceso.builder().id(21L).empresa(empresa).nombre("Auditor").build();
        when(laneRepository.findByIdAndEmpresaId(7L, EMPRESA)).thenReturn(Optional.of(lane));
        when(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(21L, EMPRESA)).thenReturn(Optional.of(auditor));
        when(laneRepository.saveAndFlush(any(Lane.class))).thenAnswer(inv -> inv.getArgument(0));

        LaneResponse respuesta = laneService.editar(EMPRESA, AUTOR, 7L, "Review", 21L, null);

        assertThat(respuesta.rolProcesoNombre()).isEqualTo("Auditor");
        assertThat(lane.getNombre()).isEqualTo("Review");
    }

    @Test
    @DisplayName("Una lane con actividades no se elimina")
    void eliminar_conActividades_lanzaReglaNegocio() {
        when(laneRepository.findByIdAndEmpresaId(7L, EMPRESA)).thenReturn(Optional.of(lane));
        when(nodoFlujoRepository.existsByLaneIdAndEmpresaId(7L, EMPRESA)).thenReturn(true);

        assertThatThrownBy(() -> laneService.eliminar(EMPRESA, AUTOR, 7L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Picking");
        verify(laneRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Una lane vacia se elimina y queda anotada en el historial de su proceso")
    void eliminar_laneVacia_seBorraYQuedaEnElHistorial() {
        when(laneRepository.findByIdAndEmpresaId(7L, EMPRESA)).thenReturn(Optional.of(lane));
        when(nodoFlujoRepository.existsByLaneIdAndEmpresaId(7L, EMPRESA)).thenReturn(false);

        laneService.eliminar(EMPRESA, AUTOR, 7L);

        verify(laneRepository).delete(lane);
        verify(historialCambioService).registrar(eq(EMPRESA), eq(AUTOR), eq(proceso), contains("eliminada"));
    }

    @Test
    @DisplayName("Listar las lanes de un pool de otra tienda responde no encontrado")
    void listarPorPool_poolAjeno_lanzaNoEncontrado() {
        when(poolRepository.existsByIdAndEmpresaId(5L, EMPRESA)).thenReturn(false);

        assertThatThrownBy(() -> laneService.listarPorPool(EMPRESA, 5L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
