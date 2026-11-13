package com.facimus.procesos.modelado.service.impl;

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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.facimus.procesos.common.ConflictoDeVersionException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.markdown.Markdown;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.CaracterizacionResponse;
import com.facimus.procesos.modelado.dto.response.DocumentacionResponse;
import com.facimus.procesos.modelado.model.Caracterizacion;
import com.facimus.procesos.modelado.model.Documentacion;
import com.facimus.procesos.modelado.repository.CaracterizacionRepository;
import com.facimus.procesos.modelado.repository.DocumentacionRepository;
import com.facimus.procesos.modelado.service.DatosDeCaracterizacion;

/** PR 62: la documentacion y la ficha, con la base de mentira y el Markdown de verdad. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentacionServiceImplTest {

    private static final Long EMPRESA = 1L;
    private static final Long AUTOR = 7L;
    private static final Long PROCESO = 3L;

    @Mock
    private DocumentacionRepository documentacionRepository;
    @Mock
    private CaracterizacionRepository caracterizacionRepository;
    @Mock
    private ProcesoRepository procesoRepository;
    @Mock
    private ElementosDelProceso elementos;
    @Mock
    private HistorialCambioService historial;

    private DocumentacionServiceImpl servicio;
    private Proceso proceso;

    @BeforeEach
    void preparar() {
        servicio = new DocumentacionServiceImpl(documentacionRepository, caracterizacionRepository, procesoRepository,
                elementos, historial, new Markdown());
        proceso = Proceso.builder().id(PROCESO).empresa(Empresa.builder().id(EMPRESA).build()).build();
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.of(proceso));
        when(documentacionRepository.saveAndFlush(any(Documentacion.class))).thenAnswer(llamada -> llamada
                .getArgument(0));
        when(caracterizacionRepository.saveAndFlush(any(Caracterizacion.class))).thenAnswer(llamada -> llamada
                .getArgument(0));
    }

    @Test
    @DisplayName("la del proceso se crea con el HTML saneado al lado, y lo dice el historial")
    void delProceso() {
        DocumentacionResponse respuesta = servicio.guardar(EMPRESA, AUTOR, PROCESO, " ", "**Ojo** <script>x</script>",
                null);

        ArgumentCaptor<Documentacion> guardada = ArgumentCaptor.forClass(Documentacion.class);
        verify(documentacionRepository).saveAndFlush(guardada.capture());
        assertThat(guardada.getValue().getBpmnId()).isNull();
        assertThat(guardada.getValue().getMarkdown()).isEqualTo("**Ojo** <script>x</script>");
        assertThat(respuesta.html()).contains("<strong>Ojo</strong>", "&lt;script&gt;").doesNotContain("<script>");
        verify(historial).registrar(EMPRESA, AUTOR, proceso, "Documentación del proceso actualizada.");
        verifyNoInteractions(elementos);
    }

    @Test
    @DisplayName("la de un elemento necesita que exista, y se edita con la version que se leyo")
    void deUnElemento() {
        when(elementos.existe(EMPRESA, PROCESO, "Activity_1")).thenReturn(true);
        Documentacion actual = Documentacion.builder().id(4L).proceso(proceso).bpmnId("Activity_1").markdown("a")
                .html("<p>a</p>").build();
        when(documentacionRepository.findByProcesoIdAndBpmnIdAndEmpresaId(PROCESO, "Activity_1", EMPRESA))
                .thenReturn(Optional.of(actual));

        DocumentacionResponse respuesta = servicio.guardar(EMPRESA, AUTOR, PROCESO, "Activity_1", "b", null);

        assertThat(respuesta.markdown()).isEqualTo("b");
        assertThat(respuesta.html()).isEqualTo("<p>b</p>");
        verify(historial).registrar(EMPRESA, AUTOR, proceso, "Documentación de Activity_1 actualizada.");
        assertThatThrownBy(() -> servicio.guardar(EMPRESA, AUTOR, PROCESO, "Activity_9", "c", null))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessageContaining("Activity_9");
    }

    @Test
    @DisplayName("una version vieja, o una version para algo que no existe, es un conflicto")
    void conflictos() {
        Documentacion actual = Documentacion.builder().id(4L).proceso(proceso).markdown("a").html("<p>a</p>").build();
        when(documentacionRepository.findByProcesoIdAndBpmnIdIsNullAndEmpresaId(PROCESO, EMPRESA))
                .thenReturn(Optional.of(actual), Optional.empty());

        assertThatThrownBy(() -> servicio.guardar(EMPRESA, AUTOR, PROCESO, null, "b", 5L))
                .isInstanceOf(ConflictoDeVersionException.class);
        assertThatThrownBy(() -> servicio.guardar(EMPRESA, AUTOR, PROCESO, null, "b", 0L))
                .isInstanceOf(ConflictoDeVersionException.class);
        verify(documentacionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("un texto vacio la borra; vacio sobre nada no hace nada")
    void borrar() {
        Documentacion actual = Documentacion.builder().id(4L).proceso(proceso).markdown("a").html("<p>a</p>").build();
        when(documentacionRepository.findByProcesoIdAndBpmnIdIsNullAndEmpresaId(PROCESO, EMPRESA))
                .thenReturn(Optional.of(actual), Optional.empty());

        DocumentacionResponse borrada = servicio.guardar(EMPRESA, AUTOR, PROCESO, null, "  ", null);
        servicio.guardar(EMPRESA, AUTOR, PROCESO, null, "", null);

        assertThat(borrada.markdown()).isEmpty();
        assertThat(borrada.version()).isNull();
        verify(documentacionRepository).delete(actual);
        verify(historial).registrar(EMPRESA, AUTOR, proceso, "Documentación del proceso borrada.");
        verify(documentacionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("la lista y la vista previa; un proceso ajeno no existe")
    void listarYVer() {
        when(documentacionRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, EMPRESA)).thenReturn(List.of(
                Documentacion.builder().id(1L).proceso(proceso).markdown("a").html("<p>a</p>").build()));

        assertThat(servicio.listar(EMPRESA, PROCESO)).extracting(DocumentacionResponse::html).containsExactly("<p>a</p>");
        assertThat(servicio.vistaPrevia("# T").html()).isEqualTo("<h1>T</h1>");
        assertThatThrownBy(() -> servicio.listar(EMPRESA, 99L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("la ficha vacia se lee sin version; guardarla deja nulos los campos en blanco")
    void ficha() {
        CaracterizacionResponse vacia = servicio.caracterizacion(EMPRESA, PROCESO);
        CaracterizacionResponse guardada = servicio.guardarCaracterizacion(EMPRESA, AUTOR, PROCESO,
                new DatosDeCaracterizacion("  Entregar  ", "", null, "s", "p", "c", "i", "r", "n", null));

        assertThat(vacia.version()).isNull();
        assertThat(vacia.objetivo()).isNull();
        assertThat(guardada.objetivo()).isEqualTo("Entregar");
        assertThat(guardada.alcance()).isNull();
        assertThat(guardada.entradas()).isNull();
        assertThat(guardada.normativa()).isEqualTo("n");
        verify(historial).registrar(EMPRESA, AUTOR, proceso, "Ficha de caracterización actualizada.");
    }

    @Test
    @DisplayName("la ficha se edita con la version que se leyo, y una version para una ficha que no hay es conflicto")
    void fichaConVersion() {
        Caracterizacion actual = Caracterizacion.builder().id(2L).proceso(proceso).objetivo("a").build();
        when(caracterizacionRepository.findByProcesoIdAndEmpresaId(PROCESO, EMPRESA))
                .thenReturn(Optional.of(actual), Optional.empty());
        DatosDeCaracterizacion vieja = new DatosDeCaracterizacion("b", null, null, null, null, null, null, null, null,
                9L);

        assertThatThrownBy(() -> servicio.guardarCaracterizacion(EMPRESA, AUTOR, PROCESO, vieja))
                .isInstanceOf(ConflictoDeVersionException.class);
        assertThatThrownBy(() -> servicio.guardarCaracterizacion(EMPRESA, AUTOR, PROCESO, vieja))
                .isInstanceOf(ConflictoDeVersionException.class);
        assertThat(servicio.caracterizacion(EMPRESA, PROCESO).objetivo()).isNull();
    }
}
