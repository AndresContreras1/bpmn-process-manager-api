package com.facimus.procesos.ejecucion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.ejecucion.dto.response.MensajeEntranteResponse;
import com.facimus.procesos.ejecucion.dto.response.MensajeSalienteResponse;
import com.facimus.procesos.ejecucion.mapper.MensajeriaMapper;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.EstadoMensajeSaliente;
import com.facimus.procesos.ejecucion.model.MensajeEntrante;
import com.facimus.procesos.ejecucion.model.MensajeSaliente;
import com.facimus.procesos.ejecucion.model.OrigenMensajeEntrante;
import com.facimus.procesos.ejecucion.model.ResultadoCorrelacion;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.repository.CasoRepository;
import com.facimus.procesos.ejecucion.repository.MensajeEntranteRepository;
import com.facimus.procesos.ejecucion.repository.MensajeSalienteRepository;
import com.facimus.procesos.ejecucion.service.DatosDelEntrante;
import com.facimus.procesos.gestion.model.ModoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.VersionProceso;
import com.facimus.procesos.gestion.service.VersionService;
import com.facimus.procesos.modelado.model.PoliticaSinCaso;
import com.facimus.procesos.modelado.service.DiagramaArmado;

import tools.jackson.databind.json.JsonMapper;

/**
 * Lo que la mensajeria decide antes de que el motor mueva nada: un mensaje repetido no entra dos veces, uno que la
 * version de hoy ya no recibe se descarta, con que clave se busca el caso, y que pasa al reintentar uno que esperaba.
 * Como el motor avanza el caso lo prueba MotorDeProcesosTest.
 */
@ExtendWith(MockitoExtension.class)
class MensajeriaServiceTest {

    private static final Long TIENDA = 1L;
    private static final Long PROCESO = 10L;

    @Mock
    private MensajeSalienteRepository mensajeSalienteRepository;
    @Mock
    private MensajeEntranteRepository mensajeEntranteRepository;
    @Mock
    private CasoRepository casoRepository;
    @Mock
    private VersionService versionService;
    @Mock
    private GrafosDeVersion grafos;
    @Mock
    private Correlacionador correlacionador;
    @Mock
    private MotorDeProcesos motor;
    @Mock
    private RelojDeLaTienda reloj;
    @Mock
    private Bitacora bitacora;
    @Mock
    private MensajeriaMapper mensajeriaMapper;

    @Mock
    private MensajeEntranteResponse respuesta;

    private final JsonMapper json = JsonMapper.builder().build();
    private MensajeriaServiceImpl service;
    private VersionProceso version;
    private Proceso proceso;
    private DiagramaArmado armado;

    @BeforeEach
    void setUp() {
        service = new MensajeriaServiceImpl(mensajeSalienteRepository, mensajeEntranteRepository, casoRepository,
                versionService, grafos, correlacionador, motor, reloj, bitacora, mensajeriaMapper, json);
        Empresa tienda = Empresa.builder().id(TIENDA).nombre("Demo Store").build();
        proceso = Proceso.builder().id(PROCESO).empresa(tienda).nombre("Order fulfillment").activo(true).build();
        version = VersionProceso.builder().id(5L).empresa(tienda).proceso(proceso).numero(1).definicion("{}").build();
        armado = DiagramaArmado.demo();
    }

    @Test
    @DisplayName("Un mensaje con una clave externa ya recibida se responde como repetido, sin volver a entrar")
    void repetido() {
        MensajeEntrante anterior = entrante("Order placed", ResultadoCorrelacion.CASO_NUEVO);
        when(mensajeEntranteRepository.findByEmpresaIdAndClaveExterna(TIENDA, "EXT-1"))
                .thenReturn(Optional.of(anterior));
        MensajeEntranteResponse repetida = mock(MensajeEntranteResponse.class);
        when(mensajeriaMapper.toEntrante(anterior)).thenReturn(respuesta);
        when(mensajeriaMapper.comoRepetido(respuesta)).thenReturn(repetida);

        assertThat(service.recibir(TIENDA, PROCESO, datos("Order placed", "ORD-1", "EXT-1"))).isSameAs(repetida);
        verify(versionService, never()).vigente(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Sin clave externa no se busca repetido; sin version vigente el mensaje no entra")
    void sinClaveExternaNiVersion() {
        DatosDelEntrante conClaveEnBlanco = datos("Order placed", "ORD-1", "  ");
        DatosDelEntrante sinClave = datos("Order placed", "ORD-1", null);

        assertThatThrownBy(() -> service.recibir(TIENDA, PROCESO, conClaveEnBlanco))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El proceso no tiene una versión publicada vigente.");
        assertThatThrownBy(() -> service.recibir(TIENDA, PROCESO, sinClave))
                .isInstanceOf(ReglaNegocioException.class);
        verify(mensajeEntranteRepository, never()).findByEmpresaIdAndClaveExterna(anyLong(), anyString());
    }

    @Test
    @DisplayName("Un mensaje que la version no recibe, o que la tienda manda, no entra")
    void mensajeQueNoSeRecibe() {
        conGrafo();
        DatosDelEntrante queNoRecibe = datos("Refund", "ORD-1", null);
        DatosDelEntrante queMandaLaTienda = datos("Payment authorization request", "ORD-1", null);

        assertThatThrownBy(() -> service.recibir(TIENDA, PROCESO, queNoRecibe))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La versión publicada del proceso no recibe ningún mensaje llamado \"Refund\".");
        assertThatThrownBy(() -> service.recibir(TIENDA, PROCESO, queMandaLaTienda))
                .isInstanceOf(ReglaNegocioException.class);
        verify(correlacionador, never()).correlacionar(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Un mensaje que espera se guarda con su clave, su origen, su tick y el resultado de la correlacion")
    void recibirEnEspera() {
        conGrafo();
        when(reloj.ahora(TIENDA)).thenReturn(7);
        when(correlacionador.correlacionar(eq(TIENDA), eq(PROCESO), any(), any(), eq("ORD-1")))
                .thenReturn(new Correlacion(ResultadoCorrelacion.EN_ESPERA, null, null, null));
        when(mensajeriaMapper.aJson(Map.of("status", "APPROVED"))).thenReturn("{\"status\":\"APPROVED\"}");
        when(mensajeEntranteRepository.save(any(MensajeEntrante.class))).thenAnswer(i -> i.getArgument(0));
        when(mensajeriaMapper.toEntrante(any(MensajeEntrante.class))).thenReturn(respuesta);
        ArgumentCaptor<MensajeEntrante> guardado = ArgumentCaptor.forClass(MensajeEntrante.class);

        assertThat(service.recibir(TIENDA, PROCESO, datos("Payment authorization result", "ORD-1", "EXT-9")))
                .isSameAs(respuesta);

        verify(mensajeEntranteRepository).save(guardado.capture());
        MensajeEntrante entrante = guardado.getValue();
        assertThat(entrante.getNombre()).isEqualTo("Payment authorization result");
        assertThat(entrante.getClave()).isEqualTo("ORD-1");
        assertThat(entrante.getCuerpo()).isEqualTo("{\"status\":\"APPROVED\"}");
        assertThat(entrante.getClaveExterna()).isEqualTo("EXT-9");
        assertThat(entrante.getOrigen()).isEqualTo(OrigenMensajeEntrante.MANUAL);
        assertThat(entrante.getResultado()).isEqualTo(ResultadoCorrelacion.EN_ESPERA);
        assertThat(entrante.getTick()).isEqualTo(7);
        assertThat(entrante.getCaso()).isNull();
        assertThat(entrante.getProceso()).isSameAs(proceso);
        assertThat(entrante.getFecha()).isNotNull();
        verify(motor, never()).mensajeRecibido(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Sin clave, el caso se busca por el campo del cuerpo que la correlacion declara")
    void claveDelCuerpo() {
        armado.correlacionPorCampo("Payment authorization result", "orderId", PoliticaSinCaso.DESCARTAR);
        conGrafo();
        when(correlacionador.correlacionar(eq(TIENDA), eq(PROCESO), any(), any(), any()))
                .thenReturn(Correlacion.descartado());
        when(mensajeEntranteRepository.save(any(MensajeEntrante.class))).thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<String> clave = ArgumentCaptor.forClass(String.class);

        service.recibir(TIENDA, PROCESO, new DatosDelEntrante("Payment authorization result", " ",
                Map.of("orderId", 42), null, OrigenMensajeEntrante.MANUAL));
        service.recibir(TIENDA, PROCESO, new DatosDelEntrante("Payment authorization result", null,
                Map.of("otro", 1), null, OrigenMensajeEntrante.MANUAL));
        service.recibir(TIENDA, PROCESO, new DatosDelEntrante("Payment authorization result", null, null, null,
                OrigenMensajeEntrante.MANUAL));

        verify(correlacionador, times(3)).correlacionar(eq(TIENDA), eq(PROCESO), any(), any(),
                clave.capture());
        assertThat(clave.getAllValues()).containsExactly("42", null, null);
    }

    @Test
    @DisplayName("Reintentar uno que ya no espera lo devuelve tal cual, sin mirar la version")
    void reintentarYaResuelto() {
        MensajeEntrante entrante = entrante("Order placed", ResultadoCorrelacion.CASO_NUEVO);
        when(mensajeEntranteRepository.findByIdAndEmpresaId(31L, TIENDA)).thenReturn(Optional.of(entrante));
        when(mensajeriaMapper.toEntrante(entrante)).thenReturn(respuesta);

        assertThat(service.reintentar(TIENDA, 31L)).isSameAs(respuesta);
        verify(versionService, never()).vigente(anyLong(), anyLong());
        verify(mensajeEntranteRepository, never()).save(any());
        assertThatThrownBy(() -> service.reintentar(TIENDA, 32L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Si la version de hoy ya no recibe el mensaje que esperaba, se descarta en vez de esperar siempre")
    void reintentarDescartado() {
        MensajeEntrante entrante = entrante("Payment authorization request", ResultadoCorrelacion.EN_ESPERA);
        conReintento(entrante);

        assertThat(service.reintentar(TIENDA, 31L)).isSameAs(respuesta);

        assertThat(entrante.getResultado()).isEqualTo(ResultadoCorrelacion.DESCARTADO);
        verify(mensajeEntranteRepository).save(entrante);
        verify(correlacionador, never()).correlacionar(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Un proceso que paso a documental descarta lo que esperaba, aunque el mensaje siga en el diagrama")
    void reintentarDocumental() {
        armado.modo(ModoProceso.DOCUMENTAL);
        MensajeEntrante entrante = entrante("Payment authorization result", ResultadoCorrelacion.PROGRAMADO);
        conReintento(entrante);

        service.reintentar(TIENDA, 31L);

        assertThat(entrante.getResultado()).isEqualTo(ResultadoCorrelacion.DESCARTADO);
    }

    @Test
    @DisplayName("Reintentar uno que sigue sin caso lo deja esperando, con la misma fila")
    void reintentarSigueEsperando() {
        MensajeEntrante entrante = entrante("Payment authorization result", ResultadoCorrelacion.EN_ESPERA);
        conReintento(entrante);
        when(correlacionador.correlacionar(eq(TIENDA), eq(PROCESO), any(), any(), eq("ORD-1")))
                .thenReturn(new Correlacion(ResultadoCorrelacion.EN_ESPERA, null, null, null));

        service.reintentar(TIENDA, 31L);

        assertThat(entrante.getResultado()).isEqualTo(ResultadoCorrelacion.EN_ESPERA);
        assertThat(entrante.getCaso()).isNull();
        verify(mensajeEntranteRepository).save(entrante);
    }

    @Test
    @DisplayName("Si ahora hay un caso que lo espera, se le entrega: el cuerpo entra a sus variables y el motor sigue")
    void reintentarEntregado() {
        MensajeEntrante entrante = entrante("Payment authorization result", ResultadoCorrelacion.EN_ESPERA);
        conReintento(entrante);
        when(reloj.ahora(TIENDA)).thenReturn(9);
        Caso caso = Caso.builder().id(42L).empresa(proceso.getEmpresa()).proceso(proceso).versionProceso(version)
                .referencia("ORD-1").estado(EstadoCaso.ABIERTO).variables("{}").build();
        ActividadCaso token = ActividadCaso.builder().id(3L).nodoNombre("Payment result received").build();
        when(correlacionador.correlacionar(eq(TIENDA), eq(PROCESO), any(), any(), eq("ORD-1")))
                .thenReturn(new Correlacion(ResultadoCorrelacion.ENTREGADO_A_CASO, caso, token, null));

        service.reintentar(TIENDA, 31L);

        assertThat(entrante.getResultado()).isEqualTo(ResultadoCorrelacion.ENTREGADO_A_CASO);
        assertThat(entrante.getCaso()).isSameAs(caso);
        assertThat(caso.getVariables()).contains("\"paymentAuthorizationResult\"").contains("APPROVED");
        verify(bitacora).anotar(caso, 9, TipoEventoCaso.MENSAJE_RECIBIDO,
                "Llego \"Payment authorization result\" y \"Payment result received\" deja de esperarlo.");
        verify(motor).mensajeRecibido(eq(caso), any(GrafoDeVersion.class), eq(token), eq(Momento.en(9)));
    }

    @Test
    @DisplayName("Las bandejas paginan lo de la tienda, y lo de un caso exige que el caso sea de ella")
    void bandejasYCaso() {
        PageRequest pagina = PageRequest.of(0, 10);
        MensajeSaliente saliente = mock(MensajeSaliente.class);
        MensajeSalienteResponse salienteResponse = mock(MensajeSalienteResponse.class);
        when(mensajeSalienteRepository.bandejaDeSalida(TIENDA, PROCESO, EstadoMensajeSaliente.PENDIENTE, pagina))
                .thenReturn(new PageImpl<>(List.of(saliente), pagina, 1));
        when(mensajeriaMapper.toSaliente(saliente)).thenReturn(salienteResponse);
        MensajeEntrante entrante = entrante("Order placed", ResultadoCorrelacion.EN_ESPERA);
        when(mensajeEntranteRepository.bandejaDeEntrada(TIENDA, PROCESO, ResultadoCorrelacion.EN_ESPERA, pagina))
                .thenReturn(new PageImpl<>(List.of(entrante), pagina, 1));
        when(mensajeriaMapper.toEntrante(entrante)).thenReturn(respuesta);

        assertThat(service.bandejaDeSalida(TIENDA, PROCESO, EstadoMensajeSaliente.PENDIENTE, pagina).content())
                .containsExactly(salienteResponse);
        assertThat(service.bandejaDeEntrada(TIENDA, PROCESO, ResultadoCorrelacion.EN_ESPERA, pagina).content())
                .containsExactly(respuesta);

        assertThatThrownBy(() -> service.salientesDelCaso(TIENDA, 42L))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Caso no encontrado.");
        assertThatThrownBy(() -> service.entrantesDelCaso(TIENDA, 42L))
                .isInstanceOf(RecursoNoEncontradoException.class);
        when(casoRepository.existsByIdAndEmpresaId(42L, TIENDA)).thenReturn(true);
        List<MensajeSalienteResponse> delCaso = List.of(salienteResponse);
        when(mensajeriaMapper.toSalientes(List.of(saliente))).thenReturn(delCaso);
        when(mensajeSalienteRepository.findAllByCasoIdAndEmpresaIdOrderByIdAsc(42L, TIENDA))
                .thenReturn(List.of(saliente));
        assertThat(service.salientesDelCaso(TIENDA, 42L)).isSameAs(delCaso);
    }

    private void conGrafo() {
        when(versionService.vigente(TIENDA, PROCESO)).thenReturn(Optional.of(version));
        when(grafos.del(TIENDA, version)).thenReturn(GrafoDeVersion.de(armado.diagrama()));
    }

    private void conReintento(MensajeEntrante entrante) {
        when(mensajeEntranteRepository.findByIdAndEmpresaId(31L, TIENDA)).thenReturn(Optional.of(entrante));
        conGrafo();
        when(mensajeEntranteRepository.save(entrante)).thenReturn(entrante);
        when(mensajeriaMapper.toEntrante(entrante)).thenReturn(respuesta);
    }

    private MensajeEntrante entrante(String nombre, ResultadoCorrelacion resultado) {
        return MensajeEntrante.builder().id(31L).empresa(proceso.getEmpresa()).proceso(proceso).nombre(nombre)
                .clave("ORD-1").cuerpo("{\"status\":\"APPROVED\"}").origen(OrigenMensajeEntrante.MANUAL)
                .claveExterna("EXT-1").resultado(resultado).build();
    }

    private static DatosDelEntrante datos(String nombre, String clave, String claveExterna) {
        return new DatosDelEntrante(nombre, clave, Map.of("status", "APPROVED"), claveExterna,
                OrigenMensajeEntrante.MANUAL);
    }
}
