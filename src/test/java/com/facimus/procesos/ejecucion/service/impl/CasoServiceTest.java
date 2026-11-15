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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.facimus.procesos.common.CamposInvalidosException;
import com.facimus.procesos.common.ConflictoDeVersionException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.formularios.CampoArmado;
import com.facimus.procesos.common.formularios.TipoDeCampo;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.ejecucion.dto.response.FormularioDeInicioResponse;
import com.facimus.procesos.ejecucion.mapper.CasoMapper;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.ModoCaso;
import com.facimus.procesos.ejecucion.model.TipoNodoCaso;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.repository.CasoRepository;
import com.facimus.procesos.ejecucion.repository.EventoCasoRepository;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.EstadoVersion;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.VersionProceso;
import com.facimus.procesos.gestion.service.VersionService;
import com.facimus.procesos.modelado.dto.response.CampoDeFormularioResponse;
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
    /** El tick en que la tienda esta cuando se llama al service: todo lo de esa operacion lleva ese numero. */
    private static final int TICK = 7;

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
    private RelojDeLaTienda reloj;

    @Mock
    private Bitacora bitacora;

    @Mock
    private CasoMapper casoMapper;

    /** PR 69: los procesos de estas pruebas no tienen formularios; el que valida es el de verdad. */
    @Spy
    private FormulariosDelCaso formularios = new FormulariosDelCaso(null, null);

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
        given(reloj.ahora(TIENDA_ID)).willReturn(TICK);
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
        verify(motor).arrancar(any(Caso.class), any(GrafoDeVersion.class), any(), eq(new Momento(TICK, ADMIN)));
    }

    @Test
    @DisplayName("R-51: un caso ya cerrado no se cancela otra vez")
    void cancelar_casoCerrado_esUnaReglaDeNegocio() {
        given(casoRepository.bloquear(CASO_ID, TIENDA_ID)).willReturn(Optional.of(caso(EstadoCaso.TERMINADO)));

        assertThatThrownBy(() -> casoService.cancelar(TIENDA_ID, ADMIN, CASO_ID))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El caso ya está cerrado.");
        verify(motor, never()).apagarTokens(any(), any());
    }

    @Test
    @DisplayName("Cancelar apaga los tokens vivos y deja el caso cerrado")
    void cancelar_apagaLosTokensYCierra() {
        Caso caso = caso(EstadoCaso.ABIERTO);
        given(casoRepository.bloquear(CASO_ID, TIENDA_ID)).willReturn(Optional.of(caso));

        casoService.cancelar(TIENDA_ID, ADMIN, CASO_ID);

        verify(motor).apagarTokens(caso, new Momento(TICK, ADMIN));
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
        given(grafos.del(TIENDA_ID, version)).willReturn(GrafoDeVersion.de(conInicioAMano().diagrama()));

        casoService.reintentar(TIENDA_ID, ADMIN, CASO_ID);

        assertThat(gateway.getEstado()).isEqualTo(EstadoActividadCaso.PENDIENTE);
        assertThat(tarea.getEstado()).isEqualTo(EstadoActividadCaso.EN_ESPERA);
        assertThat(caso.getEstado()).isEqualTo(EstadoCaso.ABIERTO);
        verify(motor).avanzar(eq(caso), any(GrafoDeVersion.class), eq(new Momento(TICK, ADMIN)));
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

    // ---------- PR 69: el formulario del inicio ----------

    private static DiagramaArmado conFormularioDeInicio() {
        DiagramaArmado armado = conInicioAMano();
        armado.formulario("Start", CampoArmado.formulario(
                CampoArmado.campo("total", TipoDeCampo.NUMERO).requerido().minimo("1"),
                CampoArmado.campo("canal", TipoDeCampo.LISTA).opciones("WEB", "TIENDA")));
        return armado;
    }

    @Test
    @DisplayName("PR 69: con formulario de inicio, las variables del caso son sus respuestas, con su tipo")
    void abrir_conFormulario_guardaLasRespuestasConTipo() {
        conGrafo(conFormularioDeInicio());

        casoService.abrir(TIENDA_ID, ADMIN, PROCESO_ID, "ORD-1", Map.of("total", 150.0, "canal", "WEB"),
                ModoCaso.PRODUCCION);

        verify(casoMapper).aJson(Map.of("total", 150L, "canal", "WEB"));
        verify(motor).arrancar(any(Caso.class), any(GrafoDeVersion.class), any(), any());
    }

    @Test
    @DisplayName("PR 69: con respuestas que no cumplen el formulario no se abre nada: 400 con cada campo")
    void abrir_conFormulario_respuestasMalas() {
        conGrafo(conFormularioDeInicio());
        Map<String, Object> malas = Map.of("canal", "FAX");

        assertThatThrownBy(() -> casoService.abrir(TIENDA_ID, ADMIN, PROCESO_ID, "ORD-1", malas))
                .isInstanceOfSatisfying(CamposInvalidosException.class, error -> assertThat(error.errores())
                        .containsOnlyKeys("variables.total", "variables.canal"));
        verify(casoRepository, never()).save(any());
        verify(motor, never()).arrancar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("PR 69: sin formulario de inicio, un caso de produccion no se abre con variables libres")
    void abrir_enProduccionSinFormulario_sinVariablesLibres() {
        conGrafo(conInicioAMano());
        Map<String, Object> libres = Map.of("order", Map.of("total", 150));

        assertThatThrownBy(() -> casoService.abrir(TIENDA_ID, ADMIN, PROCESO_ID, "ORD-1", libres,
                ModoCaso.PRODUCCION)).isInstanceOf(CamposInvalidosException.class);
        verify(casoRepository, never()).save(any());
    }

    @Test
    @DisplayName("PR 69: el formulario de inicio es el de la version vigente, con su numero y su evento")
    void formularioDeInicio_deLaVersionVigente() {
        conGrafo(conFormularioDeInicio());

        FormularioDeInicioResponse respuesta = casoService.formularioDeInicio(TIENDA_ID, PROCESO_ID);

        assertThat(respuesta.procesoId()).isEqualTo(PROCESO_ID);
        assertThat(respuesta.version()).isEqualTo(1);
        assertThat(respuesta.evento()).isEqualTo("Start");
        assertThat(respuesta.formulario().campos()).extracting(CampoDeFormularioResponse::nombre)
                .containsExactly("total", "canal");
    }

    @Test
    @DisplayName("PR 69: sin formulario, o sin inicio a mano, la respuesta lo dice con uno vacio")
    void formularioDeInicio_sinFormulario() {
        conGrafo(conInicioAMano());
        assertThat(casoService.formularioDeInicio(TIENDA_ID, PROCESO_ID).formulario()).isNull();

        conGrafo(DiagramaArmado.demo());
        FormularioDeInicioResponse porMensaje = casoService.formularioDeInicio(TIENDA_ID, PROCESO_ID);
        assertThat(porMensaje.evento()).isNull();
        assertThat(porMensaje.formulario()).isNull();
    }

    @Test
    @DisplayName("PR 69: sin version vigente no hay formulario de inicio: 404")
    void formularioDeInicio_sinVersion() {
        given(versionService.vigente(TIENDA_ID, PROCESO_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> casoService.formularioDeInicio(TIENDA_ID, PROCESO_ID))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El proceso no tiene una versión publicada vigente.");
    }

    private void conGrafo(DiagramaArmado armado) {
        given(versionService.vigente(TIENDA_ID, PROCESO_ID)).willReturn(Optional.of(version));
        given(grafos.del(TIENDA_ID, version)).willReturn(GrafoDeVersion.de(armado.diagrama()));
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
