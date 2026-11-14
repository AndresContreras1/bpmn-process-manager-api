package com.facimus.procesos.ejecucion.service.impl;

import static com.facimus.procesos.modelado.service.DiagramaArmado.TIENDA;
import static com.facimus.procesos.modelado.service.DiagramaArmado.VENTAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.ejecucion.dto.response.MensajeEntranteResponse;
import com.facimus.procesos.ejecucion.dto.response.PanelDeSimulacionResponse;
import com.facimus.procesos.ejecucion.dto.response.PedidosSimuladosResponse;
import com.facimus.procesos.ejecucion.dto.response.PendientesPorSocioResponse;
import com.facimus.procesos.ejecucion.model.EstadoMensajeSaliente;
import com.facimus.procesos.ejecucion.model.OrigenMensajeEntrante;
import com.facimus.procesos.ejecucion.model.ResultadoCorrelacion;
import com.facimus.procesos.ejecucion.puerto.GeneradorDePedidos;
import com.facimus.procesos.ejecucion.puerto.ParametrosDeSimulacion;
import com.facimus.procesos.ejecucion.repository.CasoRepository;
import com.facimus.procesos.ejecucion.repository.MensajeEntranteRepository;
import com.facimus.procesos.ejecucion.repository.MensajeSalienteRepository;
import com.facimus.procesos.ejecucion.service.DatosDelEntrante;
import com.facimus.procesos.ejecucion.service.MensajeriaService;
import com.facimus.procesos.gestion.dto.response.ConfiguracionTiendaResponse;
import com.facimus.procesos.gestion.model.ModoSimulacion;
import com.facimus.procesos.gestion.model.VersionProceso;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;
import com.facimus.procesos.gestion.service.VersionService;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.service.DiagramaArmado;

/**
 * El panel de la simulacion, el tick y los pedidos simulados sin la base: que cuenta el panel, en que orden se
 * entrega lo que vence en un tick, y como se numeran y se mandan los pedidos de una tanda.
 */
@ExtendWith(MockitoExtension.class)
class SimulacionServiceTest {

    private static final Long TIENDA_ID = 1L;
    private static final Long PROCESO_ID = 10L;

    @Mock
    private MensajeSalienteRepository mensajeSalienteRepository;
    @Mock
    private MensajeEntranteRepository mensajeEntranteRepository;
    @Mock
    private CasoRepository casoRepository;
    @Mock
    private ConfiguracionTiendaService configuracionTiendaService;
    @Mock
    private VersionService versionService;
    @Mock
    private GrafosDeVersion grafos;
    @Mock
    private MensajeriaService mensajeriaService;
    @Mock
    private GeneradorDePedidos generadorDePedidos;
    @Mock
    private ParametrosDeLaTienda parametros;
    @Mock
    private EntregaDeMensajes entrega;
    @Mock
    private RelojDeLaTienda reloj;

    @InjectMocks
    private SimulacionServiceImpl service;

    private final VersionProceso version = VersionProceso.builder().id(5L).build();

    @Test
    @DisplayName("El panel cuenta el reloj, el modo, lo pendiente por socio y lo que espera correlacion")
    void panel() {
        List<PendientesPorSocioResponse> porSocio = conLoQueHay(12);

        assertThat(service.panel(TIENDA_ID))
                .isEqualTo(new PanelDeSimulacionResponse(12, ModoSimulacion.AUTOMATICO, 4, porSocio, 2));
    }

    @Test
    @DisplayName("Un tick reintenta primero lo que llego antes y despues entrega lo que vence, y devuelve el panel")
    void tick() {
        conLoQueHay(15);
        when(reloj.avanzar(TIENDA_ID, 3)).thenReturn(15);
        when(entrega.pendientes(TIENDA_ID, 15)).thenReturn(List.of(21L, 22L));
        when(entrega.vencidos(TIENDA_ID, 15)).thenReturn(List.of(31L));

        PanelDeSimulacionResponse panel = service.tick(TIENDA_ID, 3);

        InOrder orden = inOrder(entrega);
        orden.verify(entrega).reintentar(TIENDA_ID, 21L);
        orden.verify(entrega).reintentar(TIENDA_ID, 22L);
        orden.verify(entrega).entregar(TIENDA_ID, 31L, 15);
        assertThat(panel.reloj()).isEqualTo(15);
    }

    @Test
    @DisplayName("El reloj se mueve entre 1 y 100 ticks por vez")
    void tickFueraDeRango() {
        assertThatThrownBy(() -> service.tick(TIENDA_ID, 0)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El reloj se mueve entre 1 y 100 ticks por vez.");
        assertThatThrownBy(() -> service.tick(TIENDA_ID, 101)).isInstanceOf(ReglaNegocioException.class);
        verify(reloj, never()).avanzar(anyLong(), anyInt());

        conLoQueHay(1);
        service.tick(TIENDA_ID, 1);
        service.tick(TIENDA_ID, 100);
        verify(reloj).avanzar(TIENDA_ID, 1);
        verify(reloj).avanzar(TIENDA_ID, 100);
    }

    @Test
    @DisplayName("Los pedidos se numeran despues de los casos que ya hay y se mandan con el mensaje que abre el proceso")
    void pedidos() {
        conGrafo(DiagramaArmado.demo());
        when(casoRepository.countByEmpresaIdAndProcesoId(TIENDA_ID, PROCESO_ID)).thenReturn(4L);
        ParametrosDeSimulacion deLaTienda = mock(ParametrosDeSimulacion.class);
        when(parametros.de(TIENDA_ID)).thenReturn(deLaTienda);
        Map<String, Object> plantilla = Map.of("order", Map.of("total", 100));
        List<String> referencias = List.of("SIM-10-5", "SIM-10-6", "SIM-10-7");
        List<Map<String, Object>> cuerpos = List.of(Map.of("n", 1), Map.of("n", 2), Map.of("n", 3));
        when(generadorDePedidos.pedidos(3, plantilla, referencias, deLaTienda)).thenReturn(cuerpos);
        MensajeEntranteResponse nuevo = entrante(ResultadoCorrelacion.CASO_NUEVO);
        MensajeEntranteResponse descartado = entrante(ResultadoCorrelacion.DESCARTADO);
        when(mensajeriaService.recibir(eq(TIENDA_ID), eq(PROCESO_ID), any(DatosDelEntrante.class)))
                .thenReturn(nuevo, descartado, nuevo);
        ArgumentCaptor<DatosDelEntrante> datos = ArgumentCaptor.forClass(DatosDelEntrante.class);

        PedidosSimuladosResponse respuesta = service.pedidos(TIENDA_ID, PROCESO_ID, 3, plantilla);

        assertThat(respuesta).isEqualTo(new PedidosSimuladosResponse("Order placed", 3, 2,
                List.of("SIM-10-5", "SIM-10-7")));
        verify(mensajeriaService, times(3)).recibir(eq(TIENDA_ID), eq(PROCESO_ID),
                datos.capture());
        assertThat(datos.getAllValues().getFirst()).isEqualTo(new DatosDelEntrante("Order placed", "SIM-10-5",
                Map.of("n", 1), "SIM-SIM-10-5", OrigenMensajeEntrante.CLIENTE_SIMULADO));
        assertThat(datos.getAllValues()).extracting(DatosDelEntrante::clave).containsExactlyElementsOf(referencias);
    }

    @Test
    @DisplayName("Se piden entre 1 y 200 pedidos por vez")
    void pedidosFueraDeRango() {
        assertThatThrownBy(() -> service.pedidos(TIENDA_ID, PROCESO_ID, 0, Map.of()))
                .isInstanceOf(ReglaNegocioException.class).hasMessage("Se piden entre 1 y 200 pedidos por vez.");
        assertThatThrownBy(() -> service.pedidos(TIENDA_ID, PROCESO_ID, 201, Map.of()))
                .isInstanceOf(ReglaNegocioException.class);
        verify(versionService, never()).vigente(anyLong(), anyLong());
    }

    @Test
    @DisplayName("En los bordes, uno y doscientos pedidos si se piden")
    void pedidosEnLosBordes() {
        conGrafo(DiagramaArmado.demo());
        MensajeEntranteResponse nuevo = entrante(ResultadoCorrelacion.CASO_NUEVO);
        when(mensajeriaService.recibir(eq(TIENDA_ID), eq(PROCESO_ID), any(DatosDelEntrante.class)))
                .thenReturn(nuevo);
        when(generadorDePedidos.pedidos(anyInt(), any(), any(), any())).thenAnswer(invocacion ->
                Collections.nCopies(invocacion.<Integer>getArgument(0), Map.<String, Object>of()));

        assertThat(service.pedidos(TIENDA_ID, PROCESO_ID, 1, Map.of()).casosNuevos()).isEqualTo(1);
        assertThat(service.pedidos(TIENDA_ID, PROCESO_ID, 200, Map.of()).referencias()).hasSize(200)
                .startsWith("SIM-10-1").endsWith("SIM-10-200");
    }

    @Test
    @DisplayName("Sin version vigente, o con un proceso que se abre a mano, no hay pedidos simulados")
    void pedidosSinMensajeDeInicio() {
        when(versionService.vigente(TIENDA_ID, PROCESO_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.pedidos(TIENDA_ID, PROCESO_ID, 2, Map.of()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El proceso no tiene una versión publicada vigente.");

        DiagramaArmado aMano = DiagramaArmado.reciennacido();
        aMano.lane(TIENDA, VENTAS, 1L);
        aMano.evento(VENTAS, "Start", TipoEvento.INICIO);
        aMano.actividad(VENTAS, "Receive order", TipoActividad.USUARIO);
        aMano.arco("Start", "Receive order");
        conGrafo(aMano);
        assertThatThrownBy(() -> service.pedidos(TIENDA_ID, PROCESO_ID, 2, Map.of()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Este proceso no se inicia con un mensaje: sus casos se abren uno a uno.");
        verify(mensajeriaService, never()).recibir(anyLong(), anyLong(), any());
    }

    private List<PendientesPorSocioResponse> conLoQueHay(int tick) {
        List<PendientesPorSocioResponse> porSocio = List.of(new PendientesPorSocioResponse(Integracion.PAGOS, 3));
        when(configuracionTiendaService.obtener(TIENDA_ID)).thenReturn(new ConfiguracionTiendaResponse(null, tick,
                ModoSimulacion.AUTOMATICO, null, 60, 24, null, false, 0L, null, null));
        when(mensajeSalienteRepository.countByEmpresaIdAndEstado(TIENDA_ID, EstadoMensajeSaliente.PENDIENTE))
                .thenReturn(4L);
        when(mensajeSalienteRepository.pendientesPorSocio(TIENDA_ID)).thenReturn(porSocio);
        when(mensajeEntranteRepository.countByEmpresaIdAndResultado(TIENDA_ID, ResultadoCorrelacion.EN_ESPERA))
                .thenReturn(2L);
        return porSocio;
    }

    private void conGrafo(DiagramaArmado armado) {
        when(versionService.vigente(TIENDA_ID, PROCESO_ID)).thenReturn(Optional.of(version));
        when(grafos.del(TIENDA_ID, version)).thenReturn(GrafoDeVersion.de(armado.diagrama()));
    }

    private static MensajeEntranteResponse entrante(ResultadoCorrelacion resultado) {
        MensajeEntranteResponse respuesta = mock(MensajeEntranteResponse.class);
        when(respuesta.resultado()).thenReturn(resultado);
        return respuesta;
    }
}
