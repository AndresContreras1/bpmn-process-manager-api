package com.facimus.procesos.modelado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.AsociacionResponse;
import com.facimus.procesos.modelado.mapper.AsociacionMapper;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.Artefacto;
import com.facimus.procesos.modelado.model.Asociacion;
import com.facimus.procesos.modelado.model.DireccionAsociacion;
import com.facimus.procesos.modelado.model.Evento;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.TipoArtefacto;
import com.facimus.procesos.modelado.model.TipoAsociacion;
import com.facimus.procesos.modelado.repository.ActividadRepository;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.ArtefactoRepository;
import com.facimus.procesos.modelado.repository.AsociacionRepository;
import com.facimus.procesos.modelado.repository.EventoRepository;
import com.facimus.procesos.modelado.repository.GatewayRepository;
import com.facimus.procesos.modelado.service.impl.AsociacionServiceImpl;

/**
 * PR 55: las asociaciones. Unen dos elementos distintos que el proceso tiene hoy, nombrados por su bpmn_id; una
 * asociacion lleva un artefacto en alguno de sus lados, una entrada de datos va de un dato a una actividad y una
 * salida de una actividad a un dato. No se repite la misma union, y solo una asociacion simple guarda su direccion.
 */
@ExtendWith(MockitoExtension.class)
class AsociacionServiceTest {

    private static final Long EMPRESA = 1L;
    private static final Long AUTOR = 10L;
    private static final Long PROCESO = 100L;

    private static final String TAREA = "Activity_1";
    private static final String GATEWAY = "Gateway_1";
    private static final String EVENTO = "Event_1";
    private static final String FLUJO = "Flow_1";
    private static final String ANOTACION = "TextAnnotation_1";
    private static final String GRUPO = "Group_1";
    private static final String OBJETO = "DataObjectReference_1";
    private static final String ALMACEN = "DataStoreReference_1";

    @Mock
    private AsociacionRepository asociacionRepository;
    @Mock
    private ProcesoRepository procesoRepository;
    @Mock
    private ActividadRepository actividadRepository;
    @Mock
    private GatewayRepository gatewayRepository;
    @Mock
    private EventoRepository eventoRepository;
    @Mock
    private ArcoRepository arcoRepository;
    @Mock
    private ArtefactoRepository artefactoRepository;
    @Spy
    private AsociacionMapper asociacionMapper = Mappers.getMapper(AsociacionMapper.class);
    @Mock
    private HistorialCambioService historialCambioService;

    @InjectMocks
    private AsociacionServiceImpl asociacionService;

    private Empresa empresa;
    private Proceso proceso;

    @BeforeEach
    void setUp() {
        empresa = Empresa.builder().id(EMPRESA).nombre("Demo Store").build();
        proceso = Proceso.builder().id(PROCESO).empresa(empresa).nombre("Order fulfillment").build();
    }

    private void procesoActivo() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.of(proceso));
    }

    /** Lo que el proceso tiene hoy: una tarea, un gateway, un evento, un flujo y un artefacto de cada tipo. */
    private void conElementos() {
        procesoActivo();
        when(actividadRepository.findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, EMPRESA))
                .thenReturn(List.of(Actividad.builder().id(1L).bpmnId(TAREA).nombre("Pack order").build()));
        when(gatewayRepository.findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, EMPRESA))
                .thenReturn(List.of(Gateway.builder().id(2L).bpmnId(GATEWAY).nombre("In stock?").build()));
        when(eventoRepository.findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, EMPRESA))
                .thenReturn(List.of(Evento.builder().id(3L).bpmnId(EVENTO).nombre("Order received").build()));
        when(arcoRepository.findAllByPool_ProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, EMPRESA))
                .thenReturn(List.of(Arco.builder().id(4L).bpmnId(FLUJO).build()));
        when(artefactoRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, EMPRESA)).thenReturn(List.of(
                artefacto(ANOTACION, TipoArtefacto.ANOTACION), artefacto(GRUPO, TipoArtefacto.GRUPO),
                artefacto(OBJETO, TipoArtefacto.OBJETO_DATOS), artefacto(ALMACEN, TipoArtefacto.ALMACEN_DATOS)));
    }

    private static Artefacto artefacto(String bpmnId, TipoArtefacto tipo) {
        return Artefacto.builder().bpmnId(bpmnId).tipo(tipo).build();
    }

    private void seGuarda() {
        when(asociacionRepository.save(any(Asociacion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Asociacion guardada() {
        ArgumentCaptor<Asociacion> guardada = ArgumentCaptor.forClass(Asociacion.class);
        verify(asociacionRepository).save(guardada.capture());
        return guardada.getValue();
    }

    private static Asociacion existente(TipoAsociacion tipo, String origen, String destino) {
        return Asociacion.builder().tipo(tipo).origen(origen).destino(destino).build();
    }

    private void rechaza(TipoAsociacion tipo, String origen, String destino, String mensaje) {
        assertThatThrownBy(() -> asociacionService.crear(EMPRESA, AUTOR, PROCESO, tipo, origen, destino, null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(mensaje);
        verify(asociacionRepository, never()).save(any());
    }

    // ---- Lo que no se une

    @Test
    @DisplayName("Una asociacion en un proceso de otra tienda responde no encontrado")
    void crear_procesoAjeno_lanzaNoEncontrado() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> asociacionService.crear(EMPRESA, AUTOR, PROCESO, TipoAsociacion.ASOCIACION,
                ANOTACION, TAREA, null))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Proceso no encontrado.");
        verify(asociacionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un elemento no se asocia consigo mismo, y ni se miran los elementos del proceso")
    void crear_mismosExtremos_lanzaReglaNegocio() {
        procesoActivo();

        rechaza(TipoAsociacion.ASOCIACION, ANOTACION, ANOTACION, "Una asociacion une dos elementos distintos.");
        verify(actividadRepository, never()).findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(any(), any());
    }

    @Test
    @DisplayName("Un extremo que el proceso no tiene se rechaza con su bpmn_id")
    void crear_extremoDesconocido_lanzaReglaNegocio() {
        conElementos();

        rechaza(TipoAsociacion.ASOCIACION, ANOTACION, "Activity_9", "El proceso no tiene ningun elemento Activity_9.");
    }

    @Test
    @DisplayName("Un origen que el proceso no tiene tambien se rechaza")
    void crear_origenDesconocido_lanzaReglaNegocio() {
        conElementos();

        rechaza(TipoAsociacion.ASOCIACION, "TextAnnotation_9", TAREA,
                "El proceso no tiene ningun elemento TextAnnotation_9.");
    }

    @Test
    @DisplayName("Una asociacion sin artefacto en ningun lado se rechaza")
    void crear_asociacionSinArtefacto_lanzaReglaNegocio() {
        conElementos();

        rechaza(TipoAsociacion.ASOCIACION, TAREA, GATEWAY,
                "Una asociacion une un artefacto con otro elemento del diagrama.");
    }

    @Test
    @DisplayName("Una asociacion entre un evento y un flujo tampoco lleva artefacto")
    void crear_asociacionEntreEventoYFlujo_lanzaReglaNegocio() {
        conElementos();

        rechaza(TipoAsociacion.ASOCIACION, EVENTO, FLUJO,
                "Una asociacion une un artefacto con otro elemento del diagrama.");
    }

    @Test
    @DisplayName("Una entrada de datos que sale de una actividad se rechaza")
    void crear_entradaDesdeActividad_lanzaReglaNegocio() {
        conElementos();

        rechaza(TipoAsociacion.ENTRADA_DATOS, TAREA, OBJETO,
                "Una entrada de datos va de un objeto de datos o un almacen a una actividad.");
    }

    @Test
    @DisplayName("Una anotacion no es un dato: no hace entrada de datos")
    void crear_entradaDesdeAnotacion_lanzaReglaNegocio() {
        conElementos();

        rechaza(TipoAsociacion.ENTRADA_DATOS, ANOTACION, TAREA,
                "Una entrada de datos va de un objeto de datos o un almacen a una actividad.");
    }

    @Test
    @DisplayName("Una entrada de datos llega a una actividad, no a un gateway")
    void crear_entradaAGateway_lanzaReglaNegocio() {
        conElementos();

        rechaza(TipoAsociacion.ENTRADA_DATOS, OBJETO, GATEWAY,
                "Una entrada de datos va de un objeto de datos o un almacen a una actividad.");
    }

    @Test
    @DisplayName("Una salida de datos que llega a una actividad se rechaza")
    void crear_salidaHaciaActividad_lanzaReglaNegocio() {
        conElementos();

        rechaza(TipoAsociacion.SALIDA_DATOS, OBJETO, TAREA,
                "Una salida de datos va de una actividad a un objeto de datos o un almacen.");
    }

    @Test
    @DisplayName("Una salida de datos sale de una actividad, no de un evento")
    void crear_salidaDesdeEvento_lanzaReglaNegocio() {
        conElementos();

        rechaza(TipoAsociacion.SALIDA_DATOS, EVENTO, OBJETO,
                "Una salida de datos va de una actividad a un objeto de datos o un almacen.");
    }

    @Test
    @DisplayName("Una salida de datos no termina en una anotacion")
    void crear_salidaHaciaAnotacion_lanzaReglaNegocio() {
        conElementos();

        rechaza(TipoAsociacion.SALIDA_DATOS, TAREA, GRUPO,
                "Una salida de datos va de una actividad a un objeto de datos o un almacen.");
    }

    @Test
    @DisplayName("La misma union del mismo tipo no se repite")
    void crear_repetida_lanzaReglaNegocio() {
        conElementos();
        when(asociacionRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, EMPRESA))
                .thenReturn(List.of(existente(TipoAsociacion.ASOCIACION, ANOTACION, TAREA)));

        rechaza(TipoAsociacion.ASOCIACION, ANOTACION, TAREA, "Esos dos elementos ya estan unidos asi.");
    }

    // ---- Lo que si se une

    @Test
    @DisplayName("Una anotacion se asocia a una tarea con su direccion, y queda en el historial")
    void crear_anotacionATarea_guardaConDireccion() {
        conElementos();
        seGuarda();

        AsociacionResponse respuesta = asociacionService.crear(EMPRESA, AUTOR, PROCESO, TipoAsociacion.ASOCIACION,
                ANOTACION, TAREA, DireccionAsociacion.UNA);

        Asociacion asociacion = guardada();
        assertThat(asociacion.getEmpresa()).isSameAs(empresa);
        assertThat(asociacion.getProceso()).isSameAs(proceso);
        assertThat(asociacion.getTipo()).isEqualTo(TipoAsociacion.ASOCIACION);
        assertThat(asociacion.getOrigen()).isEqualTo(ANOTACION);
        assertThat(asociacion.getDestino()).isEqualTo(TAREA);
        assertThat(asociacion.getDireccion()).isEqualTo(DireccionAsociacion.UNA);
        assertThat(respuesta.origen()).isEqualTo(ANOTACION);
        assertThat(respuesta.destino()).isEqualTo(TAREA);
        assertThat(respuesta.direccion()).isEqualTo(DireccionAsociacion.UNA);
        verify(historialCambioService).registrar(EMPRESA, AUTOR, proceso,
                "Asociacion de TextAnnotation_1 a Activity_1 agregada.");
    }

    @Test
    @DisplayName("Una asociacion sin direccion queda sin direccion, y el artefacto puede ir en el destino")
    void crear_tareaAGrupoSinDireccion_quedaNinguna() {
        conElementos();
        seGuarda();

        asociacionService.crear(EMPRESA, AUTOR, PROCESO, TipoAsociacion.ASOCIACION, GATEWAY, GRUPO, null);

        assertThat(guardada().getDireccion()).isEqualTo(DireccionAsociacion.NINGUNA);
    }

    @Test
    @DisplayName("Un flujo se puede asociar a un objeto de datos: un dato tambien es un artefacto")
    void crear_flujoADato_seAcepta() {
        conElementos();
        seGuarda();

        asociacionService.crear(EMPRESA, AUTOR, PROCESO, TipoAsociacion.ASOCIACION, FLUJO, OBJETO,
                DireccionAsociacion.AMBAS);

        Asociacion asociacion = guardada();
        assertThat(asociacion.getOrigen()).isEqualTo(FLUJO);
        assertThat(asociacion.getDireccion()).isEqualTo(DireccionAsociacion.AMBAS);
    }

    @Test
    @DisplayName("Un almacen se puede asociar a un evento")
    void crear_almacenAEvento_seAcepta() {
        conElementos();
        seGuarda();

        asociacionService.crear(EMPRESA, AUTOR, PROCESO, TipoAsociacion.ASOCIACION, ALMACEN, EVENTO, null);

        assertThat(guardada().getDestino()).isEqualTo(EVENTO);
    }

    @Test
    @DisplayName("Una entrada de datos va de un objeto de datos a una actividad, y no guarda direccion")
    void crear_entradaDeDatos_sinDireccion() {
        conElementos();
        seGuarda();

        asociacionService.crear(EMPRESA, AUTOR, PROCESO, TipoAsociacion.ENTRADA_DATOS, OBJETO, TAREA,
                DireccionAsociacion.UNA);

        Asociacion asociacion = guardada();
        assertThat(asociacion.getTipo()).isEqualTo(TipoAsociacion.ENTRADA_DATOS);
        assertThat(asociacion.getDireccion()).isEqualTo(DireccionAsociacion.NINGUNA);
    }

    @Test
    @DisplayName("Un almacen tambien es un dato: entra a una actividad")
    void crear_entradaDesdeAlmacen_seAcepta() {
        conElementos();
        seGuarda();

        asociacionService.crear(EMPRESA, AUTOR, PROCESO, TipoAsociacion.ENTRADA_DATOS, ALMACEN, TAREA, null);

        assertThat(guardada().getOrigen()).isEqualTo(ALMACEN);
    }

    @Test
    @DisplayName("Una salida de datos va de una actividad a un almacen")
    void crear_salidaDeDatos_seAcepta() {
        conElementos();
        seGuarda();

        asociacionService.crear(EMPRESA, AUTOR, PROCESO, TipoAsociacion.SALIDA_DATOS, TAREA, ALMACEN,
                DireccionAsociacion.AMBAS);

        Asociacion asociacion = guardada();
        assertThat(asociacion.getTipo()).isEqualTo(TipoAsociacion.SALIDA_DATOS);
        assertThat(asociacion.getDireccion()).isEqualTo(DireccionAsociacion.NINGUNA);
    }

    @Test
    @DisplayName("No es repetida si cambia el tipo, el origen o el destino")
    void crear_parecidaPeroDistinta_seAcepta() {
        conElementos();
        seGuarda();
        when(asociacionRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, EMPRESA)).thenReturn(List.of(
                existente(TipoAsociacion.ASOCIACION, OBJETO, TAREA),
                existente(TipoAsociacion.ENTRADA_DATOS, ALMACEN, TAREA),
                existente(TipoAsociacion.ENTRADA_DATOS, OBJETO, "Activity_2")));

        asociacionService.crear(EMPRESA, AUTOR, PROCESO, TipoAsociacion.ENTRADA_DATOS, OBJETO, TAREA, null);

        assertThat(guardada().getTipo()).isEqualTo(TipoAsociacion.ENTRADA_DATOS);
    }

    // ---- Eliminar y listar

    @Test
    @DisplayName("Eliminar una asociacion la borra y queda en el historial con sus extremos")
    void eliminar_laBorraYQuedaEnElHistorial() {
        Asociacion asociacion = existente(TipoAsociacion.ASOCIACION, ANOTACION, TAREA);
        asociacion.setProceso(proceso);
        when(asociacionRepository.findByIdAndEmpresaId(41L, EMPRESA)).thenReturn(Optional.of(asociacion));

        asociacionService.eliminar(EMPRESA, AUTOR, 41L);

        verify(asociacionRepository).delete(asociacion);
        verify(historialCambioService).registrar(EMPRESA, AUTOR, proceso,
                "Asociacion de TextAnnotation_1 a Activity_1 eliminada.");
    }

    @Test
    @DisplayName("Eliminar una asociacion que no existe responde no encontrado")
    void eliminar_inexistente_lanzaNoEncontrado() {
        when(asociacionRepository.findByIdAndEmpresaId(41L, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> asociacionService.eliminar(EMPRESA, AUTOR, 41L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Asociacion no encontrada.");
        verify(asociacionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Listar devuelve las asociaciones del proceso")
    void listar_devuelveLasDelProceso() {
        when(procesoRepository.existsByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(true);
        when(asociacionRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, EMPRESA))
                .thenReturn(List.of(existente(TipoAsociacion.ASOCIACION, ANOTACION, TAREA)));

        List<AsociacionResponse> respuesta = asociacionService.listar(EMPRESA, PROCESO);

        assertThat(respuesta).extracting(AsociacionResponse::origen).containsExactly(ANOTACION);
    }

    @Test
    @DisplayName("Listar las asociaciones de un proceso de otra tienda responde no encontrado sin consultarlas")
    void listar_procesoAjeno_lanzaNoEncontrado() {
        when(procesoRepository.existsByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(false);

        assertThatThrownBy(() -> asociacionService.listar(EMPRESA, PROCESO))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Proceso no encontrado.");
        verify(asociacionRepository, never()).findAllByProcesoIdAndEmpresaIdOrderByIdAsc(any(), any());
    }
}
