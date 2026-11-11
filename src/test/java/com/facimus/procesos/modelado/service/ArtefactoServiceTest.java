package com.facimus.procesos.modelado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import com.facimus.procesos.modelado.dto.response.ArtefactoResponse;
import com.facimus.procesos.modelado.mapper.ArtefactoMapper;
import com.facimus.procesos.modelado.model.Artefacto;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoArtefacto;
import com.facimus.procesos.modelado.repository.ArtefactoRepository;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;
import com.facimus.procesos.modelado.service.impl.ArtefactoServiceImpl;
import com.facimus.procesos.modelado.service.impl.ElementosPegados;

/**
 * PR 55: los artefactos. Una anotacion o un grupo puede ir suelto; un objeto de datos o un almacen necesita un pool
 * modelado por dentro. La lane manda sobre el pool y los dos tienen que ser del proceso; el texto se guarda sin
 * espacios de mas, el tamano sale del tipo si no se dice, y el historial nombra el artefacto con su texto recortado.
 * Editar no cambia el tipo, y eliminar suelta antes sus asociaciones.
 */
@ExtendWith(MockitoExtension.class)
class ArtefactoServiceTest {

    private static final Long EMPRESA = 1L;
    private static final Long AUTOR = 10L;
    private static final Long PROCESO = 100L;

    @Mock
    private ArtefactoRepository artefactoRepository;
    @Mock
    private ProcesoRepository procesoRepository;
    @Mock
    private PoolRepository poolRepository;
    @Mock
    private LaneRepository laneRepository;
    @Spy
    private ArtefactoMapper artefactoMapper = Mappers.getMapper(ArtefactoMapper.class);
    @Mock
    private HistorialCambioService historialCambioService;
    @Mock
    private ElementosPegados pegados;

    @InjectMocks
    private ArtefactoServiceImpl artefactoService;

    private Empresa empresa;
    private Proceso proceso;
    private Pool pool;
    private Lane lane;
    private Pool poolAjeno;
    private Lane laneAjena;

    @BeforeEach
    void setUp() {
        empresa = Empresa.builder().id(EMPRESA).nombre("Demo Store").build();
        proceso = Proceso.builder().id(PROCESO).empresa(empresa).nombre("Order fulfillment").build();
        pool = Pool.builder().id(5L).empresa(empresa).proceso(proceso).nombre("Demo Store").build();
        lane = Lane.builder().id(7L).empresa(empresa).pool(pool).nombre("Sales").build();
        Proceso otro = Proceso.builder().id(200L).empresa(empresa).nombre("Returns").build();
        poolAjeno = Pool.builder().id(6L).empresa(empresa).proceso(otro).nombre("Returns desk").build();
        laneAjena = Lane.builder().id(8L).empresa(empresa).pool(poolAjeno).nombre("Desk").build();
    }

    private static DatosDeArtefacto datos(TipoArtefacto tipo, String texto, Long poolId, Long laneId) {
        return new DatosDeArtefacto(tipo, texto, poolId, laneId, 220, 40, null, null);
    }

    private void procesoActivo() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.of(proceso));
    }

    private void seGuarda() {
        when(artefactoRepository.save(any(Artefacto.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Artefacto guardado() {
        ArgumentCaptor<Artefacto> guardado = ArgumentCaptor.forClass(Artefacto.class);
        verify(artefactoRepository).save(guardado.capture());
        return guardado.getValue();
    }

    private Artefacto artefacto(TipoArtefacto tipo, String texto) {
        return Artefacto.builder().id(40L).bpmnId(Artefacto.prefijoBpmn(tipo) + "_1").empresa(empresa)
                .proceso(proceso).tipo(tipo).texto(texto).version(3L).build();
    }

    // ---- Crear

    @Test
    @DisplayName("Crear un artefacto en un proceso de otra tienda responde no encontrado")
    void crear_procesoAjeno_lanzaNoEncontrado() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> artefactoService.crear(EMPRESA, AUTOR, PROCESO,
                datos(TipoArtefacto.ANOTACION, "Nota", null, null)))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Proceso no encontrado.");
        verify(artefactoRepository, never()).save(any());
        verify(historialCambioService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Una anotacion va suelta: guarda su texto sin espacios de mas, su posicion y el tamano de su tipo")
    void crear_anotacionSuelta_tomaElTamanoDeSuTipo() {
        procesoActivo();
        seGuarda();

        ArtefactoResponse respuesta = artefactoService.crear(EMPRESA, AUTOR, PROCESO,
                datos(TipoArtefacto.ANOTACION, "  Paid by card  ", null, null));

        Artefacto artefacto = guardado();
        assertThat(artefacto.getEmpresa()).isSameAs(empresa);
        assertThat(artefacto.getProceso()).isSameAs(proceso);
        assertThat(artefacto.getTipo()).isEqualTo(TipoArtefacto.ANOTACION);
        assertThat(artefacto.getPool()).isNull();
        assertThat(artefacto.getLane()).isNull();
        assertThat(artefacto.getTexto()).isEqualTo("Paid by card");
        assertThat(artefacto.getPosicionX()).isEqualTo(220);
        assertThat(artefacto.getPosicionY()).isEqualTo(40);
        assertThat(artefacto.getAncho()).isEqualTo(100);
        assertThat(artefacto.getAlto()).isEqualTo(30);
        assertThat(respuesta.texto()).isEqualTo("Paid by card");
        assertThat(respuesta.poolId()).isNull();
        verify(historialCambioService).registrar(EMPRESA, AUTOR, proceso, "Anotacion \"Paid by card\" agregado.");
    }

    @Test
    @DisplayName("Un texto en blanco no se guarda, y el historial nombra el artefacto solo por su clase")
    void crear_textoEnBlanco_quedaSinTexto() {
        procesoActivo();
        seGuarda();

        artefactoService.crear(EMPRESA, AUTOR, PROCESO, datos(TipoArtefacto.GRUPO, "   ", null, null));

        assertThat(guardado().getTexto()).isNull();
        verify(historialCambioService).registrar(EMPRESA, AUTOR, proceso, "Grupo agregado.");
    }

    @Test
    @DisplayName("Sin texto un grupo toma el tamano de su tipo")
    void crear_grupoSinTexto_tomaSuTamano() {
        procesoActivo();
        seGuarda();

        artefactoService.crear(EMPRESA, AUTOR, PROCESO, datos(TipoArtefacto.GRUPO, null, null, null));

        Artefacto artefacto = guardado();
        assertThat(artefacto.getTexto()).isNull();
        assertThat(artefacto.getAncho()).isEqualTo(300);
        assertThat(artefacto.getAlto()).isEqualTo(200);
        verify(historialCambioService).registrar(EMPRESA, AUTOR, proceso, "Grupo agregado.");
    }

    @Test
    @DisplayName("Un tamano dicho se respeta")
    void crear_conTamano_usaElDado() {
        procesoActivo();
        seGuarda();

        artefactoService.crear(EMPRESA, AUTOR, PROCESO,
                new DatosDeArtefacto(TipoArtefacto.GRUPO, "Payment", null, null, 0, 0, 400, 250));

        Artefacto artefacto = guardado();
        assertThat(artefacto.getAncho()).isEqualTo(400);
        assertThat(artefacto.getAlto()).isEqualTo(250);
    }

    @Test
    @DisplayName("El historial recorta un texto de mas de 60 caracteres y deja entero uno de 60")
    void crear_textoLargo_seRecortaEnElHistorial() {
        procesoActivo();
        seGuarda();
        String sesenta = "a".repeat(60);
        String sesentaYUno = "b".repeat(61);

        artefactoService.crear(EMPRESA, AUTOR, PROCESO, datos(TipoArtefacto.ANOTACION, sesenta, null, null));
        artefactoService.crear(EMPRESA, AUTOR, PROCESO, datos(TipoArtefacto.ANOTACION, sesentaYUno, null, null));

        verify(historialCambioService).registrar(EMPRESA, AUTOR, proceso, "Anotacion \"" + sesenta + "\" agregado.");
        verify(historialCambioService).registrar(EMPRESA, AUTOR, proceso,
                "Anotacion \"" + "b".repeat(59) + "...\" agregado.");
    }

    @Test
    @DisplayName("En una lane el artefacto toma el pool de la lane")
    void crear_enUnaLane_tomaSuPool() {
        procesoActivo();
        seGuarda();
        when(laneRepository.findByIdAndEmpresaId(7L, EMPRESA)).thenReturn(Optional.of(lane));

        ArtefactoResponse respuesta = artefactoService.crear(EMPRESA, AUTOR, PROCESO,
                datos(TipoArtefacto.OBJETO_DATOS, "Invoice", null, 7L));

        Artefacto artefacto = guardado();
        assertThat(artefacto.getLane()).isSameAs(lane);
        assertThat(artefacto.getPool()).isSameAs(pool);
        assertThat(respuesta.laneId()).isEqualTo(7L);
        assertThat(respuesta.poolId()).isEqualTo(5L);
        assertThat(artefacto.getAncho()).isEqualTo(36);
        assertThat(artefacto.getAlto()).isEqualTo(50);
        verify(historialCambioService).registrar(EMPRESA, AUTOR, proceso, "Objeto de datos \"Invoice\" agregado.");
    }

    @Test
    @DisplayName("Una lane con su mismo pool indicado se acepta")
    void crear_laneConSuPool_seAcepta() {
        procesoActivo();
        seGuarda();
        when(laneRepository.findByIdAndEmpresaId(7L, EMPRESA)).thenReturn(Optional.of(lane));

        artefactoService.crear(EMPRESA, AUTOR, PROCESO, datos(TipoArtefacto.ANOTACION, "Nota", 5L, 7L));

        assertThat(guardado().getPool()).isSameAs(pool);
        verify(poolRepository, never()).findByIdAndEmpresaId(any(), any());
    }

    @Test
    @DisplayName("Una lane de otro proceso se rechaza")
    void crear_laneDeOtroProceso_lanzaReglaNegocio() {
        procesoActivo();
        when(laneRepository.findByIdAndEmpresaId(8L, EMPRESA)).thenReturn(Optional.of(laneAjena));

        assertThatThrownBy(() -> artefactoService.crear(EMPRESA, AUTOR, PROCESO,
                datos(TipoArtefacto.ANOTACION, "Nota", null, 8L)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La lane no es de este proceso.");
        verify(artefactoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Una lane que no existe se rechaza igual que una ajena")
    void crear_laneInexistente_lanzaReglaNegocio() {
        procesoActivo();
        when(laneRepository.findByIdAndEmpresaId(9L, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> artefactoService.crear(EMPRESA, AUTOR, PROCESO,
                datos(TipoArtefacto.ANOTACION, "Nota", null, 9L)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La lane no es de este proceso.");
    }

    @Test
    @DisplayName("Una lane con un pool distinto del indicado se rechaza")
    void crear_laneDeOtroPool_lanzaReglaNegocio() {
        procesoActivo();
        when(laneRepository.findByIdAndEmpresaId(7L, EMPRESA)).thenReturn(Optional.of(lane));

        assertThatThrownBy(() -> artefactoService.crear(EMPRESA, AUTOR, PROCESO,
                datos(TipoArtefacto.ANOTACION, "Nota", 6L, 7L)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La lane no es del pool indicado.");
        verify(artefactoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un almacen en un pool modelado del proceso queda en el pool, sin lane, con su tamano")
    void crear_almacenEnPool_quedaEnElPool() {
        procesoActivo();
        seGuarda();
        when(poolRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(pool));

        artefactoService.crear(EMPRESA, AUTOR, PROCESO, datos(TipoArtefacto.ALMACEN_DATOS, "Orders", 5L, null));

        Artefacto artefacto = guardado();
        assertThat(artefacto.getPool()).isSameAs(pool);
        assertThat(artefacto.getLane()).isNull();
        assertThat(artefacto.getAncho()).isEqualTo(50);
        assertThat(artefacto.getAlto()).isEqualTo(50);
        verify(historialCambioService).registrar(EMPRESA, AUTOR, proceso, "Almacen de datos \"Orders\" agregado.");
    }

    @Test
    @DisplayName("Un pool de otro proceso se rechaza")
    void crear_poolDeOtroProceso_lanzaReglaNegocio() {
        procesoActivo();
        when(poolRepository.findByIdAndEmpresaId(6L, EMPRESA)).thenReturn(Optional.of(poolAjeno));

        assertThatThrownBy(() -> artefactoService.crear(EMPRESA, AUTOR, PROCESO,
                datos(TipoArtefacto.ANOTACION, "Nota", 6L, null)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El pool no es de este proceso.");
        verify(artefactoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un objeto de datos suelto se rechaza: es un dato del proceso de un pool")
    void crear_objetoDeDatosSuelto_lanzaReglaNegocio() {
        procesoActivo();

        assertThatThrownBy(() -> artefactoService.crear(EMPRESA, AUTOR, PROCESO,
                datos(TipoArtefacto.OBJETO_DATOS, "Invoice", null, null)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Un objeto de datos o un almacen va en un pool modelado por dentro: es un dato de su "
                        + "proceso.");
        verify(artefactoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un almacen en un pool de caja negra se rechaza")
    void crear_almacenEnCajaNegra_lanzaReglaNegocio() {
        pool.setCajaNegra(true);
        procesoActivo();
        when(poolRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(pool));

        assertThatThrownBy(() -> artefactoService.crear(EMPRESA, AUTOR, PROCESO,
                datos(TipoArtefacto.ALMACEN_DATOS, "Orders", 5L, null)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageStartingWith("Un objeto de datos o un almacen va en un pool modelado por dentro");
        verify(artefactoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Una anotacion si puede ir en un pool de caja negra")
    void crear_anotacionEnCajaNegra_seAcepta() {
        pool.setCajaNegra(true);
        procesoActivo();
        seGuarda();
        when(poolRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(pool));

        artefactoService.crear(EMPRESA, AUTOR, PROCESO, datos(TipoArtefacto.ANOTACION, "Carrier", 5L, null));

        assertThat(guardado().getPool()).isSameAs(pool);
    }

    // ---- Editar

    @Test
    @DisplayName("Editar con una version vieja responde conflicto y no guarda nada")
    void editar_versionVieja_lanzaConflicto() {
        Artefacto artefacto = artefacto(TipoArtefacto.ANOTACION, "Nota");
        when(artefactoRepository.findByIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.of(artefacto));

        assertThatThrownBy(() -> artefactoService.editar(EMPRESA, AUTOR, 40L,
                datos(TipoArtefacto.ANOTACION, "Otra", null, null), 2L))
                .isInstanceOf(ConflictoDeVersionException.class);
        assertThat(artefacto.getTexto()).isEqualTo("Nota");
        verify(artefactoRepository, never()).saveAndFlush(any());
        verify(historialCambioService, never()).registrar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Editar un artefacto que no existe responde no encontrado")
    void editar_inexistente_lanzaNoEncontrado() {
        when(artefactoRepository.findByIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> artefactoService.editar(EMPRESA, AUTOR, 40L,
                datos(TipoArtefacto.ANOTACION, "Otra", null, null), 3L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Artefacto no encontrado.");
    }

    @Test
    @DisplayName("Editar no cambia el tipo: un objeto de datos sigue necesitando su pool aunque se diga otro tipo")
    void editar_mantieneElTipo() {
        Artefacto artefacto = artefacto(TipoArtefacto.OBJETO_DATOS, "Invoice");
        artefacto.setPool(pool);
        when(artefactoRepository.findByIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.of(artefacto));

        assertThatThrownBy(() -> artefactoService.editar(EMPRESA, AUTOR, 40L,
                datos(TipoArtefacto.ANOTACION, "Invoice", null, null), 3L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageStartingWith("Un objeto de datos o un almacen");
        verify(artefactoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Editar mueve el artefacto, cambia su texto y su tamano, y queda en el historial")
    void editar_cambiaUbicacionTextoYTamano() {
        Artefacto artefacto = artefacto(TipoArtefacto.GRUPO, "Payment");
        when(artefactoRepository.findByIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.of(artefacto));
        when(laneRepository.findByIdAndEmpresaId(7L, EMPRESA)).thenReturn(Optional.of(lane));
        when(artefactoRepository.saveAndFlush(artefacto)).thenReturn(artefacto);

        ArtefactoResponse respuesta = artefactoService.editar(EMPRESA, AUTOR, 40L,
                new DatosDeArtefacto(TipoArtefacto.ANOTACION, " Billing ", null, 7L, 15, 25, 320, null), 3L);

        assertThat(artefacto.getTipo()).isEqualTo(TipoArtefacto.GRUPO);
        assertThat(artefacto.getTexto()).isEqualTo("Billing");
        assertThat(artefacto.getLane()).isSameAs(lane);
        assertThat(artefacto.getPool()).isSameAs(pool);
        assertThat(artefacto.getPosicionX()).isEqualTo(15);
        assertThat(artefacto.getPosicionY()).isEqualTo(25);
        assertThat(artefacto.getAncho()).isEqualTo(320);
        assertThat(artefacto.getAlto()).isEqualTo(200);
        assertThat(respuesta.id()).isEqualTo(40L);
        assertThat(respuesta.texto()).isEqualTo("Billing");
        verify(historialCambioService).registrar(EMPRESA, AUTOR, proceso, "Grupo \"Billing\" editado.");
    }

    // ---- Eliminar, obtener y listar

    @Test
    @DisplayName("Eliminar suelta antes las asociaciones del artefacto, lo borra y queda en el historial")
    void eliminar_sueltaSusAsociacionesYLoBorra() {
        Artefacto artefacto = artefacto(TipoArtefacto.ANOTACION, "Paid by card");
        when(artefactoRepository.findByIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.of(artefacto));

        artefactoService.eliminar(EMPRESA, AUTOR, 40L);

        InOrder orden = inOrder(pegados, artefactoRepository, historialCambioService);
        orden.verify(pegados).soltarAsociaciones(EMPRESA, PROCESO, "TextAnnotation_1");
        orden.verify(artefactoRepository).delete(artefacto);
        orden.verify(historialCambioService).registrar(EMPRESA, AUTOR, proceso,
                "Anotacion \"Paid by card\" eliminado.");
    }

    @Test
    @DisplayName("Eliminar un artefacto que no existe responde no encontrado y no suelta nada")
    void eliminar_inexistente_lanzaNoEncontrado() {
        when(artefactoRepository.findByIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> artefactoService.eliminar(EMPRESA, AUTOR, 40L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Artefacto no encontrado.");
        verify(pegados, never()).soltarAsociaciones(any(), any(), any());
        verify(artefactoRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Obtener devuelve el artefacto de la tienda")
    void obtener_devuelveElArtefacto() {
        when(artefactoRepository.findByIdAndEmpresaId(40L, EMPRESA))
                .thenReturn(Optional.of(artefacto(TipoArtefacto.ANOTACION, "Nota")));

        ArtefactoResponse respuesta = artefactoService.obtener(EMPRESA, 40L);

        assertThat(respuesta.id()).isEqualTo(40L);
        assertThat(respuesta.bpmnId()).isEqualTo("TextAnnotation_1");
        assertThat(respuesta.texto()).isEqualTo("Nota");
    }

    @Test
    @DisplayName("Obtener un artefacto de otra tienda responde no encontrado")
    void obtener_ajeno_lanzaNoEncontrado() {
        when(artefactoRepository.findByIdAndEmpresaId(40L, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> artefactoService.obtener(EMPRESA, 40L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Listar devuelve los artefactos del proceso en orden")
    void listar_devuelveLosDelProceso() {
        when(procesoRepository.existsByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(true);
        when(artefactoRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, EMPRESA))
                .thenReturn(List.of(artefacto(TipoArtefacto.ANOTACION, "Nota"), artefacto(TipoArtefacto.GRUPO, "G")));

        List<ArtefactoResponse> respuesta = artefactoService.listar(EMPRESA, PROCESO);

        assertThat(respuesta).extracting(ArtefactoResponse::texto).containsExactly("Nota", "G");
    }

    @Test
    @DisplayName("Listar los artefactos de un proceso de otra tienda responde no encontrado sin consultarlos")
    void listar_procesoAjeno_lanzaNoEncontrado() {
        when(procesoRepository.existsByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(false);

        assertThatThrownBy(() -> artefactoService.listar(EMPRESA, PROCESO))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Proceso no encontrado.");
        verify(artefactoRepository, never()).findAllByProcesoIdAndEmpresaIdOrderByIdAsc(any(), any());
    }
}
