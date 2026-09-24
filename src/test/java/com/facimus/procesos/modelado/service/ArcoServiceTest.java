package com.facimus.procesos.modelado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.ArcoResponse;
import com.facimus.procesos.modelado.mapper.ArcoMapper;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.NodoFlujo;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;
import com.facimus.procesos.modelado.service.impl.ArcoServiceImpl;

/** HU-11 a HU-13: el flujo entre nodos, con las reglas BPMN que lo acotan. */
@ExtendWith(MockitoExtension.class)
class ArcoServiceTest {

    private static final Long EMPRESA = 1L;
    private static final Long AUTOR = 10L;

    @Mock
    private HistorialCambioService historialCambioService;
    @Mock
    private ArcoRepository arcoRepository;
    @Mock
    private NodoFlujoRepository nodoFlujoRepository;
    @Mock
    private PoolRepository poolRepository;

    @Spy
    private ArcoMapper arcoMapper = Mappers.getMapper(ArcoMapper.class);

    @InjectMocks
    private ArcoServiceImpl arcoService;

    private Empresa empresa;
    private Pool pool;
    private Lane lane;
    private Actividad recoger;
    private Actividad empacar;

    @BeforeEach
    void setUp() {
        empresa = Empresa.builder().id(EMPRESA).nombre("Demo Store").build();
        Proceso proceso = Proceso.builder().id(100L).empresa(empresa).nombre("Order fulfillment").build();
        pool = Pool.builder().id(5L).empresa(empresa).proceso(proceso).nombre("Demo Store").build();
        lane = Lane.builder().id(7L).empresa(empresa).pool(pool)
                .rolProceso(RolProceso.builder().id(20L).empresa(empresa).nombre("Warehouse").build())
                .nombre("Picking").build();
        recoger = Actividad.builder().id(30L).empresa(empresa).lane(lane).nombre("Pick items").build();
        empacar = Actividad.builder().id(31L).empresa(empresa).lane(lane).nombre("Pack order").build();
    }

    @Test
    @DisplayName("HU-11: el arco une dos nodos del mismo pool y queda anotado en el historial")
    void crear_uneDosNodosDelMismoPool() {
        preparar(recoger, empacar);
        when(arcoRepository.existsByOrigenIdAndDestinoIdAndEmpresaId(30L, 31L, EMPRESA)).thenReturn(false);
        when(arcoRepository.save(any(Arco.class))).thenAnswer(inv -> inv.getArgument(0));

        ArcoResponse respuesta = arcoService.crear(EMPRESA, AUTOR, 30L, 31L, "Listo", null, false, 0);

        assertThat(respuesta.origenId()).isEqualTo(30L);
        assertThat(respuesta.destinoId()).isEqualTo(31L);
        assertThat(respuesta.poolId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Un arco no puede salir y llegar al mismo nodo")
    void crear_mismoNodo_lanzaReglaNegocio() {
        assertThatThrownBy(() -> arcoService.crear(EMPRESA, AUTOR, 30L, 30L, null, null, false, 0))
                .isInstanceOf(ReglaNegocioException.class);
        verifyNoInteractions(nodoFlujoRepository, arcoRepository);
    }

    @Test
    @DisplayName("Los dos nodos de un arco tienen que estar en el mismo pool")
    void crear_nodosDePoolsDistintos_lanzaReglaNegocio() {
        Pool otroPool = Pool.builder().id(6L).empresa(empresa).proceso(pool.getProceso()).nombre("Carrier").build();
        Lane otraLane = Lane.builder().id(8L).empresa(empresa).pool(otroPool).nombre("Routing").build();
        Actividad ajena = Actividad.builder().id(32L).empresa(empresa).lane(otraLane).nombre("Route").build();
        preparar(recoger, ajena);

        assertThatThrownBy(() -> arcoService.crear(EMPRESA, AUTOR, 30L, 32L, null, null, false, 0))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("mismo pool");
        verify(arcoRepository, never()).save(any());
    }

    @Test
    @DisplayName("No se repite un arco entre el mismo par de nodos")
    void crear_arcoRepetido_lanzaReglaNegocio() {
        preparar(recoger, empacar);
        when(arcoRepository.existsByOrigenIdAndDestinoIdAndEmpresaId(30L, 31L, EMPRESA)).thenReturn(true);

        assertThatThrownBy(() -> arcoService.crear(EMPRESA, AUTOR, 30L, 31L, null, null, false, 0))
                .isInstanceOf(ReglaNegocioException.class);
        verify(arcoRepository, never()).save(any());
    }

    @Test
    @DisplayName("BPMN: un arco que sale de un gateway exclusivo necesita condicion")
    void crear_desdeGatewayExclusivoSinCondicion_lanzaReglaNegocio() {
        Gateway decision = Gateway.builder().id(33L).empresa(empresa).lane(lane).nombre("Stock available?")
                .tipoGateway(TipoGateway.EXCLUSIVO).build();
        preparar(decision, empacar);
        when(arcoRepository.existsByOrigenIdAndDestinoIdAndEmpresaId(33L, 31L, EMPRESA)).thenReturn(false);

        assertThatThrownBy(() -> arcoService.crear(EMPRESA, AUTOR, 33L, 31L, "Si", "  ", false, 0))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("sale de un gateway");
        verify(arcoRepository, never()).save(any());
    }

    @Test
    @DisplayName("BPMN: el gateway paralelo sigue todas sus salidas, asi que no pide condicion")
    void crear_desdeGatewayParalelo_noExigeCondicion() {
        Gateway paralelo = Gateway.builder().id(34L).empresa(empresa).lane(lane).nombre("Split")
                .tipoGateway(TipoGateway.PARALELO).build();
        preparar(paralelo, empacar);
        when(arcoRepository.existsByOrigenIdAndDestinoIdAndEmpresaId(34L, 31L, EMPRESA)).thenReturn(false);
        when(arcoRepository.save(any(Arco.class))).thenAnswer(inv -> inv.getArgument(0));

        ArcoResponse respuesta = arcoService.crear(EMPRESA, AUTOR, 34L, 31L, "Rama", null, false, 0);

        assertThat(respuesta.condicion()).isNull();
    }

    @Test
    @DisplayName("BPMN: al editar tampoco se le puede quitar la condicion a la salida de un gateway exclusivo")
    void editar_quitandoLaCondicionDeUnaSalida_lanzaReglaNegocio() {
        Gateway decision = Gateway.builder().id(33L).empresa(empresa).lane(lane).nombre("Stock available?")
                .tipoGateway(TipoGateway.INCLUSIVO).build();
        Arco arco = Arco.builder().id(50L).empresa(empresa).pool(pool).origen(decision).destino(empacar)
                .condicion("stock > 0").build();
        when(arcoRepository.findByIdAndEmpresaId(50L, EMPRESA)).thenReturn(Optional.of(arco));

        assertThatThrownBy(() -> arcoService.editar(EMPRESA, AUTOR, 50L, "Si", null, false, 0, null))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(arco.getCondicion()).isEqualTo("stock > 0");
        verify(arcoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("R-36: la salida por defecto de un gateway que decide no necesita condicion")
    void crear_salidaPorDefecto_noExigeCondicion() {
        Gateway decision = Gateway.builder().id(33L).empresa(empresa).lane(lane).nombre("Stock available?")
                .tipoGateway(TipoGateway.EXCLUSIVO).build();
        preparar(decision, empacar);
        when(arcoRepository.existsByOrigenIdAndDestinoIdAndEmpresaId(33L, 31L, EMPRESA)).thenReturn(false);
        when(arcoRepository.findAllByOrigenIdAndEmpresaId(33L, EMPRESA)).thenReturn(List.of());
        when(arcoRepository.save(any(Arco.class))).thenAnswer(inv -> inv.getArgument(0));

        ArcoResponse respuesta = arcoService.crear(EMPRESA, AUTOR, 33L, 31L, "Si no", null, true, 2);

        assertThat(respuesta.porDefecto()).isTrue();
        assertThat(respuesta.orden()).isEqualTo(2);
    }

    @Test
    @DisplayName("R-35: la salida por defecto se toma cuando ninguna condicion se cumple, asi que no lleva una")
    void crear_salidaPorDefectoConCondicion_lanzaReglaNegocio() {
        Gateway decision = Gateway.builder().id(33L).empresa(empresa).lane(lane).nombre("Stock available?")
                .tipoGateway(TipoGateway.EXCLUSIVO).build();
        preparar(decision, empacar);
        when(arcoRepository.existsByOrigenIdAndDestinoIdAndEmpresaId(33L, 31L, EMPRESA)).thenReturn(false);

        assertThatThrownBy(() -> arcoService.crear(EMPRESA, AUTOR, 33L, 31L, "Si no", "stock > 0", true, 0))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no lleva condicion");
        verify(arcoRepository, never()).save(any());
    }

    @Test
    @DisplayName("R-35: un gateway solo tiene una salida por defecto")
    void crear_segundaSalidaPorDefecto_lanzaReglaNegocio() {
        Gateway decision = Gateway.builder().id(33L).empresa(empresa).lane(lane).nombre("Stock available?")
                .tipoGateway(TipoGateway.EXCLUSIVO).build();
        preparar(decision, empacar);
        when(arcoRepository.existsByOrigenIdAndDestinoIdAndEmpresaId(33L, 31L, EMPRESA)).thenReturn(false);
        when(arcoRepository.findAllByOrigenIdAndEmpresaId(33L, EMPRESA)).thenReturn(List.of(Arco.builder()
                .id(51L).empresa(empresa).pool(pool).origen(decision).destino(recoger).porDefecto(true).build()));

        assertThatThrownBy(() -> arcoService.crear(EMPRESA, AUTOR, 33L, 31L, "Si no", null, true, 0))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("una salida por defecto");
        verify(arcoRepository, never()).save(any());
    }

    @Test
    @DisplayName("R-35: una actividad no elige, asi que su arco no es una salida por defecto")
    void crear_salidaPorDefectoDesdeActividad_lanzaReglaNegocio() {
        preparar(recoger, empacar);
        when(arcoRepository.existsByOrigenIdAndDestinoIdAndEmpresaId(30L, 31L, EMPRESA)).thenReturn(false);

        assertThatThrownBy(() -> arcoService.crear(EMPRESA, AUTOR, 30L, 31L, "Listo", null, true, 0))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Solo un gateway");
        verify(arcoRepository, never()).save(any());
    }

    @Test
    @DisplayName("R-35: la salida que ya era la por defecto no choca consigo misma al editarse")
    void editar_laMismaSalidaPorDefecto_seGuarda() {
        Gateway decision = Gateway.builder().id(33L).empresa(empresa).lane(lane).nombre("Stock available?")
                .tipoGateway(TipoGateway.EXCLUSIVO).build();
        Arco arco = Arco.builder().id(50L).empresa(empresa).pool(pool).origen(decision).destino(empacar)
                .porDefecto(true).build();
        when(arcoRepository.findByIdAndEmpresaId(50L, EMPRESA)).thenReturn(Optional.of(arco));
        when(arcoRepository.findAllByOrigenIdAndEmpresaId(33L, EMPRESA)).thenReturn(List.of(arco));
        when(arcoRepository.saveAndFlush(any(Arco.class))).thenAnswer(inv -> inv.getArgument(0));

        ArcoResponse respuesta = arcoService.editar(EMPRESA, AUTOR, 50L, "Si no", null, true, 5, null);

        assertThat(respuesta.porDefecto()).isTrue();
        assertThat(respuesta.orden()).isEqualTo(5);
    }

    private void preparar(NodoFlujo origen, NodoFlujo destino) {
        when(nodoFlujoRepository.findByIdAndEmpresaId(origen.getId(), EMPRESA)).thenReturn(Optional.of(origen));
        when(nodoFlujoRepository.findByIdAndEmpresaId(destino.getId(), EMPRESA)).thenReturn(Optional.of(destino));
    }
}
