package com.facimus.procesos.ejecucion.service.impl;

import static com.facimus.procesos.modelado.service.DiagramaArmado.TIENDA;
import static com.facimus.procesos.modelado.service.DiagramaArmado.VENTAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.facimus.procesos.common.ConflictoDeVersionException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.ejecucion.mapper.CasoMapper;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.TipoNodoCaso;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.repository.CasoRepository;
import com.facimus.procesos.ejecucion.repository.EventoCasoRepository;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.EstadoVersion;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.VersionProceso;
import com.facimus.procesos.gestion.service.VersionService;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.service.DiagramaArmado;

/**
 * Las reglas de los casos: cuando se puede abrir uno, cuando se puede cerrar y que no se puede hacer con el caso de
 * otra tienda. Lo que hace avanzar el caso es el motor, que tiene su propia prueba.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CasoServiceTest {

    private static final Long TIENDA_ID = 1L;
    private static final Long ADMIN = 2L;
    private static final Long PROCESO_ID = 10L;
    private static final Long CASO_ID = 42L;

    @Mock
    private CasoRepository casoRepository;

    @Mock
    private ActividadCasoRepository actividadCasoRepository;

    @Mock
    private EventoCasoRepository eventoCasoRepository;

    @Mock
    private VersionService versionService;

    @Mock
    private GrafosDeVersion grafos;

    @Mock
    private MotorDeProcesos motor;

    @Mock
    private Bitacora bitacora;

    @Mock
    private CasoMapper casoMapper;

    @InjectMocks
    private CasoServiceImpl casoService;

    private Empresa tienda;
    private VersionProceso version;

    @BeforeEach
    void laTiendaYSuVersion() {
        tienda = Empresa.builder().id(TIENDA_ID).nombre("Demo Store").build();
        Proceso proceso = Proceso.builder().id(PROCESO_ID).empresa(tienda).nombre("Order fulfillment")
                .estado(EstadoProceso.PUBLICADO).activo(true).build();
        version = VersionProceso.builder().id(5L).empresa(tienda).proceso(proceso).numero(1)
                .estado(EstadoVersion.VIGENTE).definicion("{}").build();
        given(casoMapper.aJson(any())).willReturn("{}");
        given(casoRepository.save(any(Caso.class))).willAnswer(llamada -> llamada.getArgument(0));
    }

    @Test
    @DisplayName("R-47: un proceso sin version vigente no abre casos")
    void abrir_sinVersionVigente_esUnaReglaDeNegocio() {
        given(versionService.vigente(TIENDA_ID, PROCESO_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> casoService.abrir(TIENDA_ID, ADMIN, PROCESO_ID, "ORD-1", Map.of()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El proceso no tiene una versión publicada vigente.");
        verify(motor, never()).arrancar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("R-48: un proceso que empieza por mensaje dice con que mensaje se abre")
    void abrir_procesoQueEmpiezaPorMensaje_diceComoSeAbre() {
        conGrafo(DiagramaArmado.demo());

        assertThatThrownBy(() -> casoService.abrir(TIENDA_ID, ADMIN, PROCESO_ID, "ORD-1", Map.of()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Este proceso se inicia con el mensaje \"Order placed\"; envíelo como mensaje entrante.");
    }

    @Test
    @DisplayName("Una version sin ningun evento de inicio tampoco abre casos")
    void abrir_versionSinInicio_loDice() {
        conGrafo(DiagramaArmado.reciennacido());

        assertThatThrownBy(() -> casoService.abrir(TIENDA_ID, ADMIN, PROCESO_ID, "ORD-1", Map.of()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La versión publicada no tiene ningún evento de inicio.");
    }

    @Test
    @DisplayName("Abrir guarda el caso sobre la version vigente y se lo pasa al motor")
    void abrir_guardaElCasoYLoArranca() {
        conGrafo(conInicioAMano());

        casoService.abrir(TIENDA_ID, ADMIN, PROCESO_ID, "ORD-1", Map.of("order", Map.of("total", 150)));

        verify(casoRepository).save(any(Caso.class));
        verify(motor).arrancar(any(Caso.class), any(GrafoDeVersion.class), any(), eq(ADMIN));
    }

    @Test
    @DisplayName("R-51: un caso ya cerrado no se cancela otra vez")
    void cancelar_casoCerrado_esUnaReglaDeNegocio() {
        given(casoRepository.bloquear(CASO_ID, TIENDA_ID)).willReturn(Optional.of(caso(EstadoCaso.TERMINADO)));

        assertThatThrownBy(() -> casoService.cancelar(TIENDA_ID, ADMIN, CASO_ID))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El caso ya está cerrado.");
        verify(motor, never()).apagarTokens(any());
    }

    @Test
    @DisplayName("Cancelar apaga los tokens vivos y deja el caso cerrado")
    void cancelar_apagaLosTokensYCierra() {
        Caso caso = caso(EstadoCaso.ABIERTO);
        given(casoRepository.bloquear(CASO_ID, TIENDA_ID)).willReturn(Optional.of(caso));

        casoService.cancelar(TIENDA_ID, ADMIN, CASO_ID);

        verify(motor).apagarTokens(caso);
        assertThat(caso.getEstado()).isEqualTo(EstadoCaso.CANCELADO);
        assertThat(caso.getFechaFin()).isNotNull();
    }

    @Test
    @DisplayName("Un caso en error si se puede cancelar: todavia no esta cerrado")
    void cancelar_casoEnError_seCancela() {
        Caso caso = caso(EstadoCaso.ERROR);
        given(casoRepository.bloquear(CASO_ID, TIENDA_ID)).willReturn(Optional.of(caso));

        casoService.cancelar(TIENDA_ID, ADMIN, CASO_ID);

        assertThat(caso.getEstado()).isEqualTo(EstadoCaso.CANCELADO);
    }

    @Test
    @DisplayName("El caso de otra tienda no existe para esta")
    void cancelar_deOtraTienda_noExiste() {
        given(casoRepository.bloquear(CASO_ID, TIENDA_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> casoService.cancelar(TIENDA_ID, ADMIN, CASO_ID))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Caso no encontrado.");
    }

    @Test
    @DisplayName("Solo se reintenta un caso que esta en error")
    void reintentar_casoQueNoEstaEnError_esUnaReglaDeNegocio() {
        given(casoRepository.bloquear(CASO_ID, TIENDA_ID)).willReturn(Optional.of(caso(EstadoCaso.ABIERTO)));

        assertThatThrownBy(() -> casoService.reintentar(TIENDA_ID, ADMIN, CASO_ID))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El caso no está en error: no hay nada que reintentar.");
    }

    @Test
    @DisplayName("Reintentar vuelve a poner pendiente el gateway que se quedo esperando y avanza el caso")
    void reintentar_vuelveAPonerPendienteElGateway() {
        Caso caso = caso(EstadoCaso.ERROR);
        ActividadCaso gateway = ActividadCaso.builder().id(8L).empresa(tienda).caso(caso).nodoId(3L)
                .nodoNombre("Payment approved?").tipoNodo(TipoNodoCaso.GATEWAY).subtipo("EXCLUSIVO")
                .estado(EstadoActividadCaso.EN_ESPERA).build();
        ActividadCaso tarea = ActividadCaso.builder().id(9L).empresa(tienda).caso(caso).nodoId(4L)
                .nodoNombre("Receive order").tipoNodo(TipoNodoCaso.ACTIVIDAD).subtipo("USUARIO")
                .estado(EstadoActividadCaso.EN_ESPERA).build();
        given(casoRepository.bloquear(CASO_ID, TIENDA_ID)).willReturn(Optional.of(caso));
        given(actividadCasoRepository.findAllByCasoIdAndEmpresaIdAndEstadoOrderByIdAsc(CASO_ID, TIENDA_ID,
                EstadoActividadCaso.EN_ESPERA)).willReturn(List.of(gateway, tarea));
        given(grafos.del(version)).willReturn(GrafoDeVersion.de(conInicioAMano().diagrama()));

        casoService.reintentar(TIENDA_ID, ADMIN, CASO_ID);

        assertThat(gateway.getEstado()).isEqualTo(EstadoActividadCaso.PENDIENTE);
        assertThat(tarea.getEstado()).isEqualTo(EstadoActividadCaso.EN_ESPERA);
        assertThat(caso.getEstado()).isEqualTo(EstadoCaso.ABIERTO);
        verify(motor).avanzar(eq(caso), any(GrafoDeVersion.class), eq(ADMIN));
    }

    @Test
    @DisplayName("Corregir las variables con una version vieja es un conflicto")
    void corregirVariables_conVersionVieja_esConflicto() {
        given(casoRepository.bloquear(CASO_ID, TIENDA_ID)).willReturn(Optional.of(caso(EstadoCaso.ERROR)));

        assertThatThrownBy(() -> casoService.corregirVariables(TIENDA_ID, CASO_ID, Map.of(), 7L))
                .isInstanceOf(ConflictoDeVersionException.class);
    }

    @Test
    @DisplayName("Las variables de un caso cerrado ya no se tocan")
    void corregirVariables_casoCerrado_esUnaReglaDeNegocio() {
        given(casoRepository.bloquear(CASO_ID, TIENDA_ID)).willReturn(Optional.of(caso(EstadoCaso.CANCELADO)));

        assertThatThrownBy(() -> casoService.corregirVariables(TIENDA_ID, CASO_ID, Map.of(), null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El caso ya está cerrado.");
    }

    @Test
    @DisplayName("Mirar el caso de otra tienda es un 404, no un 403")
    void obtener_deOtraTienda_noExiste() {
        given(casoRepository.findByIdAndEmpresaId(CASO_ID, TIENDA_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> casoService.obtener(TIENDA_ID, CASO_ID))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Caso no encontrado.");
    }

    private void conGrafo(DiagramaArmado armado) {
        given(versionService.vigente(TIENDA_ID, PROCESO_ID)).willReturn(Optional.of(version));
        given(grafos.del(version)).willReturn(GrafoDeVersion.de(armado.diagrama()));
    }

    /** Lo minimo que se abre a mano: pool de la tienda, una lane, un inicio y una tarea. */
    private static DiagramaArmado conInicioAMano() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(TIENDA, VENTAS, 1L);
        armado.evento(VENTAS, "Start", TipoEvento.INICIO);
        armado.actividad(VENTAS, "Receive order", TipoActividad.USUARIO);
        armado.arco("Start", "Receive order");
        return armado;
    }

    private Caso caso(EstadoCaso estado) {
        return Caso.builder().id(CASO_ID).empresa(tienda).proceso(version.getProceso()).versionProceso(version)
                .referencia("ORD-1").estado(estado).variables("{}").build();
    }
}
