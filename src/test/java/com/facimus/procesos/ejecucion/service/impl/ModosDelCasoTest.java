package com.facimus.procesos.ejecucion.service.impl;

import static com.facimus.procesos.modelado.service.DiagramaArmado.TIENDA;
import static com.facimus.procesos.modelado.service.DiagramaArmado.VENTAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.ejecucion.mapper.CasoMapper;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.ModoCaso;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.repository.CasoRepository;
import com.facimus.procesos.ejecucion.repository.EventoCasoRepository;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.EstadoVersion;
import com.facimus.procesos.gestion.model.ModoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.TipoVersion;
import com.facimus.procesos.gestion.model.VersionProceso;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.VersionService;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoTemporizador;
import com.facimus.procesos.modelado.service.DiagramaArmado;

/**
 * PR 68: los dos modos de un caso y la prueba del borrador. Un caso abierto sin decir modo corre en PRUEBA, como todos
 * los de antes; una prueba congela el borrador en una version de prueba y corre en PRUEBA siempre.
 */
@ExtendWith(MockitoExtension.class)
class ModosDelCasoTest {

    private static final Long TIENDA_ID = 1L;
    private static final Long PROCESO_ID = 10L;
    private static final Long ADMIN = 2L;
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
    @Mock
    private ProcesoService procesoService;
    @Mock
    private BorradoDePruebas borradoDePruebas;

    @InjectMocks
    private CasoServiceImpl casoService;

    private VersionProceso version;

    @BeforeEach
    void laVersion() {
        Empresa tienda = Empresa.builder().id(TIENDA_ID).build();
        Proceso proceso = Proceso.builder().id(PROCESO_ID).empresa(tienda).estado(EstadoProceso.PUBLICADO)
                .activo(true).build();
        version = VersionProceso.builder().id(5L).empresa(tienda).proceso(proceso).numero(1)
                .estado(EstadoVersion.VIGENTE).build();
    }

    private void abrible() {
        given(reloj.ahora(TIENDA_ID)).willReturn(TICK);
        given(casoMapper.aJson(any())).willReturn("{}");
        given(casoRepository.save(any(Caso.class))).willAnswer(llamada -> llamada.getArgument(0));
    }

    private Caso guardado() {
        ArgumentCaptor<Caso> caso = ArgumentCaptor.forClass(Caso.class);
        verify(casoRepository).save(caso.capture());
        return caso.getValue();
    }

    @Test
    @DisplayName("Sin decir modo un caso corre en PRUEBA; en PRODUCCION, si se pide")
    void abrir_conModo() {
        abrible();
        given(versionService.vigente(TIENDA_ID, PROCESO_ID)).willReturn(Optional.of(version));
        given(grafos.del(TIENDA_ID, version)).willReturn(GrafoDeVersion.de(conInicioAMano().diagrama()));

        casoService.abrir(TIENDA_ID, ADMIN, PROCESO_ID, "ORD-1", Map.of(), ModoCaso.PRODUCCION);

        Caso caso = guardado();
        assertThat(caso.getModo()).isEqualTo(ModoCaso.PRODUCCION);
        assertThat(caso.getEstado()).isEqualTo(EstadoCaso.ABIERTO);
        assertThat(caso.getTickInicio()).isEqualTo(TICK);
        assertThat(caso.getVersionProceso()).isSameAs(version);
        assertThat(caso.getReferencia()).isEqualTo("ORD-1");
    }

    @Test
    @DisplayName("El abrir de siempre, sin modo, abre en PRUEBA")
    void abrir_sinModo() {
        abrible();
        given(versionService.vigente(TIENDA_ID, PROCESO_ID)).willReturn(Optional.of(version));
        given(grafos.del(TIENDA_ID, version)).willReturn(GrafoDeVersion.de(conInicioAMano().diagrama()));

        casoService.abrir(TIENDA_ID, ADMIN, PROCESO_ID, "ORD-1", Map.of());

        assertThat(guardado().getModo()).isEqualTo(ModoCaso.PRUEBA);
    }

    @Test
    @DisplayName("Un proceso que empieza por un temporizador no se abre a mano: lo dice")
    void abrir_inicioTemporizado() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(TIENDA, VENTAS, 3L);
        armado.temporizador(VENTAS, "Every Monday", TipoEvento.TEMPORIZADOR_INICIO, TipoTemporizador.CICLO,
                "0 0 9 * * MON", null);
        given(versionService.vigente(TIENDA_ID, PROCESO_ID)).willReturn(Optional.of(version));
        given(grafos.del(TIENDA_ID, version)).willReturn(GrafoDeVersion.de(armado.diagrama()));

        assertThatThrownBy(() -> casoService.abrir(TIENDA_ID, ADMIN, PROCESO_ID, "ORD-1", Map.of()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Este proceso se inicia con el temporizador \"Every Monday\": sus casos se abren solos "
                        + "cuando salta.");
    }

    @Test
    @DisplayName("Probar congela el borrador y abre en PRUEBA sobre la version de prueba, por su inicio")
    void probar() {
        abrible();
        VersionProceso deprueba = VersionProceso.builder().id(9L).empresa(version.getEmpresa())
                .proceso(version.getProceso()).numero(0).estado(EstadoVersion.RETIRADA).tipo(TipoVersion.PRUEBA)
                .build();
        given(procesoService.versionDePrueba(TIENDA_ID, PROCESO_ID, ADMIN)).willReturn(deprueba);
        GrafoDeVersion grafo = GrafoDeVersion.de(conInicioAMano().diagrama());
        given(grafos.del(TIENDA_ID, deprueba)).willReturn(grafo);

        casoService.probar(TIENDA_ID, ADMIN, PROCESO_ID, "TEST-1", Map.of());

        Caso caso = guardado();
        assertThat(caso.getModo()).isEqualTo(ModoCaso.PRUEBA);
        assertThat(caso.getVersionProceso()).isSameAs(deprueba);
        verify(motor).arrancar(caso, grafo, grafo.inicioAMano().orElseThrow(), new Momento(TICK, ADMIN));
        verify(versionService, never()).vigente(any(), any());
    }

    @Test
    @DisplayName("Una prueba empieza por el inicio temporizado sin esperar a su hora, o por el de mensaje")
    void probar_otrosInicios() {
        DiagramaArmado temporizado = DiagramaArmado.reciennacido();
        temporizado.lane(TIENDA, VENTAS, 3L);
        temporizado.temporizador(VENTAS, "Every Monday", TipoEvento.TEMPORIZADOR_INICIO, TipoTemporizador.CICLO,
                "0 0 9 * * MON", null);
        GrafoDeVersion conTemporizador = GrafoDeVersion.de(temporizado.diagrama());
        GrafoDeVersion conMensaje = GrafoDeVersion.de(DiagramaArmado.demo().diagrama());

        assertThat(conTemporizador.inicioDeUnaPrueba()).get().extracting(NodoDeLaVersion::nombre)
                .isEqualTo("Every Monday");
        assertThat(conMensaje.inicioDeUnaPrueba()).get().extracting(NodoDeLaVersion::nombre)
                .isEqualTo("Order received");
        assertThat(GrafoDeVersion.de(conInicioAMano().diagrama()).inicioDeUnaPrueba()).get()
                .extracting(NodoDeLaVersion::nombre).isEqualTo("Start");
    }

    @Test
    @DisplayName("Un borrador documental o sin inicio no se prueba")
    void probar_noCorre() {
        VersionProceso deprueba = VersionProceso.builder().id(9L).build();
        given(procesoService.versionDePrueba(TIENDA_ID, PROCESO_ID, ADMIN)).willReturn(deprueba);
        DiagramaArmado documental = conInicioAMano();
        documental.modo(ModoProceso.DOCUMENTAL);
        given(grafos.del(TIENDA_ID, deprueba)).willReturn(GrafoDeVersion.de(documental.diagrama()),
                GrafoDeVersion.de(DiagramaArmado.reciennacido().diagrama()));

        assertThatThrownBy(() -> casoService.probar(TIENDA_ID, ADMIN, PROCESO_ID, null, null))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("documental");
        assertThatThrownBy(() -> casoService.probar(TIENDA_ID, ADMIN, PROCESO_ID, null, null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El borrador no tiene ningún evento de inicio.");
        verify(casoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Olvidar las pruebas viejas borra lo de sus casos antes que cada version, y cuenta las que borro")
    void olvidarPruebas() {
        LocalDateTime limite = LocalDateTime.parse("2026-11-11T03:30:00");
        given(versionService.pruebasAntesDe(limite)).willReturn(List.of(8L, 9L, 10L));
        given(versionService.borrarPrueba(8L)).willReturn(true);
        given(versionService.borrarPrueba(9L)).willReturn(false);
        given(versionService.borrarPrueba(10L)).willReturn(true);

        assertThat(casoService.olvidarPruebasAntesDe(limite)).isEqualTo(2);

        InOrder orden = inOrder(borradoDePruebas, versionService);
        for (Long prueba : List.of(8L, 9L, 10L)) {
            orden.verify(borradoDePruebas).borrar(prueba);
            orden.verify(versionService).borrarPrueba(prueba);
        }
    }

    @Test
    @DisplayName("Sin pruebas viejas no se borra nada")
    void olvidarPruebas_ninguna() {
        given(versionService.pruebasAntesDe(any())).willReturn(List.of());

        assertThat(casoService.olvidarPruebasAntesDe(LocalDateTime.MAX)).isZero();

        verifyNoInteractions(borradoDePruebas);
        verify(versionService, never()).borrarPrueba(any());
    }

    private static DiagramaArmado conInicioAMano() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(TIENDA, VENTAS, 3L);
        armado.evento(VENTAS, "Start", TipoEvento.INICIO);
        armado.actividad(VENTAS, "Pick items", TipoActividad.USUARIO);
        armado.arco("Start", "Pick items");
        return armado;
    }
}
