package com.facimus.procesos.ejecucion.service.impl;

import static com.facimus.procesos.modelado.service.DiagramaArmado.TIENDA;
import static com.facimus.procesos.modelado.service.DiagramaArmado.VENTAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.security.ContextoDeTienda.Alcance;
import com.facimus.procesos.ejecucion.dto.response.TemporizadorResponse;
import com.facimus.procesos.ejecucion.mapper.CasoMapper;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.ClaseTemporizador;
import com.facimus.procesos.ejecucion.model.EstadoTemporizador;
import com.facimus.procesos.ejecucion.model.Temporizador;
import com.facimus.procesos.ejecucion.repository.CasoRepository;
import com.facimus.procesos.ejecucion.repository.TemporizadorRepository;
import com.facimus.procesos.ejecucion.repository.TemporizadorVencido;
import com.facimus.procesos.gestion.model.ModoProceso;
import com.facimus.procesos.gestion.model.VersionProceso;
import com.facimus.procesos.gestion.service.VersionService;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoTemporizador;
import com.facimus.procesos.modelado.service.DiagramaArmado;

/**
 * PR 68: el planificador, el tick y la sincronizacion de los inicios temporizados. Cada salto es de otro componente
 * (con su transaccion); aqui se prueba que se pide uno por uno, con la tienda de cada uno, y que un fallo no frena a
 * los demas.
 */
@ExtendWith(MockitoExtension.class)
class TemporizadorServiceImplTest {

    private static final Long UNA = 1L;
    private static final Long OTRA = 2L;
    private static final Long PROCESO = 10L;
    private static final Instant AHORA = Instant.parse("2026-01-09T20:00:00Z");

    @Mock
    private TemporizadorRepository temporizadorRepository;
    @Mock
    private CasoRepository casoRepository;
    @Mock
    private VersionService versionService;
    @Mock
    private GrafosDeVersion grafos;
    @Mock
    private Temporizadores temporizadores;
    @Mock
    private DisparoDeTemporizadores disparo;
    @Mock
    private CasoMapper casoMapper;

    private TemporizadorServiceImpl service;
    private final List<Alcance> alcances = new ArrayList<>();

    @BeforeEach
    void armar() {
        service = new TemporizadorServiceImpl(temporizadorRepository, casoRepository, versionService, grafos,
                temporizadores, disparo, casoMapper, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @AfterEach
    void sinAlcance() {
        ContextoDeTienda.restaurar(null);
    }

    @Test
    @DisplayName("Los vencidos de produccion se buscan con el reloj de pared y saltan uno por uno, cada uno en su tienda")
    void dispararVencidos_cadaUnoEnSuTienda() {
        given(temporizadorRepository.vencidosEnProduccion(LocalDateTime.parse("2026-01-09T20:00:00"),
                PageRequest.of(0, TemporizadorServiceImpl.LOTE)))
                .willReturn(List.of(new TemporizadorVencido(31L, UNA), new TemporizadorVencido(32L, OTRA)));
        given(disparo.enProduccion(anyLong(), anyLong())).willAnswer(llamada -> {
            alcances.add(ContextoDeTienda.actual().orElseThrow());
            return !Long.valueOf(32L).equals(llamada.getArgument(1));
        });

        int disparados = ContextoDeTienda.comoSistema(service::dispararVencidos);

        assertThat(disparados).isOne();
        verify(disparo).enProduccion(UNA, 31L);
        verify(disparo).enProduccion(OTRA, 32L);
        assertThat(alcances).containsExactly(Alcance.deLaTienda(UNA), Alcance.deLaTienda(OTRA));
        assertThat(ContextoDeTienda.actual()).isEmpty();
    }

    @Test
    @DisplayName("Uno que falla queda anotado con su error y los demas saltan igual")
    void dispararVencidos_unFalloNoFrena() {
        given(temporizadorRepository.vencidosEnProduccion(any(), any()))
                .willReturn(List.of(new TemporizadorVencido(31L, UNA), new TemporizadorVencido(32L, OTRA)));
        given(disparo.enProduccion(UNA, 31L)).willThrow(new IllegalStateException("ERROR: insert into casos falla"));
        given(disparo.enProduccion(OTRA, 32L)).willReturn(true);

        assertThat(service.dispararVencidos()).isOne();

        // Lo anotado lo lee cualquier rol: dice que fallo y con que, no el mensaje de la excepcion
        verify(disparo).anotarFallo(UNA, 31L, "No pudo saltar (IllegalStateException); el detalle está en el "
                + "registro del servidor.");
        verify(disparo, never()).anotarFallo(eq(OTRA), anyLong(), anyString());
    }

    @Test
    @DisplayName("El tick dispara los de prueba de su tienda vencidos en ese tick, y cuenta los que saltaron")
    void dispararEnTick() {
        given(temporizadorRepository.vencidosEnPrueba(UNA, 7))
                .willReturn(List.of(new TemporizadorVencido(41L, UNA), new TemporizadorVencido(42L, UNA),
                        new TemporizadorVencido(43L, UNA)));
        given(disparo.enPrueba(UNA, 41L, 7)).willReturn(true);
        given(disparo.enPrueba(UNA, 42L, 7)).willReturn(false);
        given(disparo.enPrueba(UNA, 43L, 7)).willThrow(new IllegalArgumentException("roto"));

        assertThat(service.dispararEnTick(UNA, 7)).isOne();

        verify(disparo).anotarFallo(UNA, 43L, "No pudo saltar (IllegalArgumentException); el detalle está en el "
                + "registro del servidor.");
    }

    @Test
    @DisplayName("Sincronizar deja los inicios de la vigente: cancela los de otra version y arma los que faltan")
    void sincronizarInicios() {
        DiagramaArmado armado = conDosInicios();
        VersionProceso vigente = VersionProceso.builder().id(5L).empresa(Empresa.builder().id(UNA).build()).build();
        given(versionService.vigente(UNA, PROCESO)).willReturn(Optional.of(vigente));
        given(grafos.del(UNA, vigente)).willReturn(GrafoDeVersion.de(armado.diagrama()));
        Temporizador deLaAnterior = inicio(4L, armado.id("Every hour"));
        Temporizador yaArmado = inicio(5L, armado.id("Every hour"));
        given(temporizadorRepository.findAllByProcesoIdAndEmpresaIdAndClaseAndEstadoOrderByIdAsc(PROCESO, UNA,
                ClaseTemporizador.INICIO, EstadoTemporizador.PROGRAMADO)).willReturn(List.of(deLaAnterior, yaArmado));

        service.sincronizarInicios(UNA, PROCESO);

        assertThat(deLaAnterior.getEstado()).isEqualTo(EstadoTemporizador.CANCELADO);
        assertThat(yaArmado.getEstado()).isEqualTo(EstadoTemporizador.PROGRAMADO);
        verify(temporizadorRepository).save(deLaAnterior);
        verify(temporizadorRepository, never()).save(yaArmado);
        verify(temporizadores).armarInicio(any(), any());
        verify(temporizadores).armarInicio(vigente, GrafoDeVersion.de(armado.diagrama()).iniciosTemporizados()
                .stream().filter(inicio -> inicio.nombre().equals("Every day")).findFirst().orElseThrow());
    }

    @Test
    @DisplayName("Sin version vigente se cancelan todos y no se arma nada")
    void sincronizarInicios_sinVigente() {
        Temporizador armado = inicio(4L, 1L);
        given(versionService.vigente(UNA, PROCESO)).willReturn(Optional.empty());
        given(temporizadorRepository.findAllByProcesoIdAndEmpresaIdAndClaseAndEstadoOrderByIdAsc(PROCESO, UNA,
                ClaseTemporizador.INICIO, EstadoTemporizador.PROGRAMADO)).willReturn(List.of(armado));

        service.sincronizarInicios(UNA, PROCESO);

        assertThat(armado.getEstado()).isEqualTo(EstadoTemporizador.CANCELADO);
        verifyNoInteractions(grafos, temporizadores);
    }

    @Test
    @DisplayName("Una vigente documental no abre casos: no arma sus inicios")
    void sincronizarInicios_documental() {
        DiagramaArmado armado = conDosInicios();
        armado.modo(ModoProceso.DOCUMENTAL);
        VersionProceso vigente = VersionProceso.builder().id(5L).build();
        given(versionService.vigente(UNA, PROCESO)).willReturn(Optional.of(vigente));
        given(grafos.del(UNA, vigente)).willReturn(GrafoDeVersion.de(armado.diagrama()));

        service.sincronizarInicios(UNA, PROCESO);

        verifyNoInteractions(temporizadores);
    }

    @Test
    @DisplayName("Dar de baja el proceso cancela sus inicios armados")
    void cancelarInicios() {
        Temporizador armado = inicio(4L, 1L);
        given(temporizadorRepository.findAllByProcesoIdAndEmpresaIdAndClaseAndEstadoOrderByIdAsc(PROCESO, UNA,
                ClaseTemporizador.INICIO, EstadoTemporizador.PROGRAMADO)).willReturn(List.of(armado));

        service.cancelarInicios(UNA, PROCESO);

        assertThat(armado.getEstado()).isEqualTo(EstadoTemporizador.CANCELADO);
        verify(temporizadorRepository).save(armado);
    }

    @Test
    @DisplayName("Los de un caso de otra tienda no existen; los del propio se leen en orden")
    void delCaso() {
        given(casoRepository.findByIdAndEmpresaId(42L, OTRA)).willReturn(Optional.empty());
        given(casoRepository.findByIdAndEmpresaId(42L, UNA)).willReturn(Optional.of(Caso.builder().id(42L).build()));
        List<Temporizador> armados = List.of(inicio(5L, 1L));
        List<TemporizadorResponse> respuesta = List.of();
        given(temporizadorRepository.findAllByCasoIdAndEmpresaIdOrderByIdAsc(42L, UNA)).willReturn(armados);
        given(casoMapper.toTemporizadores(armados)).willReturn(respuesta);

        assertThatThrownBy(() -> service.delCaso(OTRA, 42L)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(service.delCaso(UNA, 42L)).isSameAs(respuesta);
    }

    @Test
    @DisplayName("Los inicios de un proceso pasan por el proceso: uno ajeno o eliminado es un 404")
    void delProceso() {
        given(versionService.vigente(OTRA, PROCESO)).willThrow(new RecursoNoEncontradoException("Proceso no "
                + "encontrado."));
        Temporizador armado = inicio(5L, 1L);
        TemporizadorResponse respuesta = mock(TemporizadorResponse.class);
        Pageable pagina = PageRequest.of(1, 10);
        given(temporizadorRepository.findAllByProcesoIdAndEmpresaIdAndClaseOrderByIdDesc(PROCESO, UNA,
                ClaseTemporizador.INICIO, pagina)).willReturn(new PageImpl<>(List.of(armado), pagina, 11));
        given(casoMapper.toTemporizador(armado)).willReturn(respuesta);

        assertThatThrownBy(() -> service.delProceso(OTRA, PROCESO, pagina))
                .isInstanceOf(RecursoNoEncontradoException.class);
        PageResponse<TemporizadorResponse> leidos = service.delProceso(UNA, PROCESO, pagina);

        assertThat(leidos.content()).containsExactly(respuesta);
        assertThat(leidos.page()).isOne();
        assertThat(leidos.totalElements()).isEqualTo(11);
        verify(versionService).vigente(UNA, PROCESO);
    }

    private static DiagramaArmado conDosInicios() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(TIENDA, VENTAS, 3L);
        armado.temporizador(VENTAS, "Every hour", TipoEvento.TEMPORIZADOR_INICIO, TipoTemporizador.CICLO, "R/PT1H",
                null);
        armado.temporizador(VENTAS, "Every day", TipoEvento.TEMPORIZADOR_INICIO, TipoTemporizador.CICLO, "R/P1D",
                null);
        armado.actividad(VENTAS, "Count", TipoActividad.USUARIO);
        armado.arco("Every hour", "Count");
        armado.arco("Every day", "Count");
        return armado;
    }

    private static Temporizador inicio(Long versionId, Long nodoId) {
        return Temporizador.builder().id(versionId * 10).versionProcesoId(versionId).nodoId(nodoId)
                .clase(ClaseTemporizador.INICIO).estado(EstadoTemporizador.PROGRAMADO).build();
    }
}
