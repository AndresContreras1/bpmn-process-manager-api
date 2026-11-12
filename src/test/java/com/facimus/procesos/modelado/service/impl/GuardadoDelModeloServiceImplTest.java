package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.PrecondicionFallidaException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.xml.LimitesXml;
import com.facimus.procesos.gestion.dto.response.HistorialCambioResponse;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.ModeloBpmn;
import com.facimus.procesos.modelado.dto.response.ImportacionResponse;
import com.facimus.procesos.modelado.dto.response.InformeImportacionResponse;
import com.facimus.procesos.modelado.dto.response.RevisionBorradorResponse;
import com.facimus.procesos.modelado.model.MotivoDeRevision;
import com.facimus.procesos.modelado.model.RevisionBorrador;
import com.facimus.procesos.modelado.repository.RevisionBorradorRepository;
import com.facimus.procesos.modelado.service.ImportacionBpmnService;
import com.facimus.procesos.modelado.service.ModeloBpmnService;

/**
 * El guardado del modelo: la huella es la del archivo que el borrador exporta; un If-Match viejo se rechaza con los
 * ultimos cambios; el mismo archivo no importa nada; un autoguardado deja revision solo si cambio algo y la ultima ya
 * tiene sus minutos; las revisiones que pasan del maximo se borran; y restaurar guarda antes lo que habia.
 */
@ExtendWith(MockitoExtension.class)
class GuardadoDelModeloServiceImplTest {

    private static final Long EMPRESA_ID = 1L;
    private static final Long USUARIO_ID = 2L;
    private static final Long PROCESO_ID = 10L;
    private static final Long REVISION_ID = 5L;
    private static final int MAXIMO = 3;
    private static final String XML_ACTUAL = "<definitions id=\"actual\"/>";
    private static final String XML_NUEVO = "<definitions id=\"nuevo\"/>";
    private static final String XML_VIEJO = "<definitions id=\"viejo\"/>";
    private static final InformeImportacionResponse INFORME = new InformeImportacionResponse(Map.of(), List.of(),
            List.of(), List.of(), List.of());

    @Mock
    private ImportacionBpmnService importacionBpmnService;

    @Mock
    private ModeloBpmnService modeloBpmnService;

    @Mock
    private ProcesoRepository procesoRepository;

    @Mock
    private RevisionBorradorRepository revisionRepository;

    @Mock
    private HistorialCambioService historialCambioService;

    private GuardadoDelModeloServiceImpl servicio;
    private Empresa tienda;
    private Proceso proceso;

    @BeforeEach
    void elServicio() {
        servicio = new GuardadoDelModeloServiceImpl(importacionBpmnService, modeloBpmnService, procesoRepository,
                revisionRepository, historialCambioService, Duration.ofMinutes(5), MAXIMO);
        tienda = Empresa.builder().id(EMPRESA_ID).nombre("Demo Store").build();
        proceso = Proceso.builder().id(PROCESO_ID).empresa(tienda).nombre("Order fulfillment").build();
    }

    // ---------- Guardar ----------

    @Test
    @DisplayName("Guardar en un proceso que no existe no lee el archivo ni el borrador")
    void guardar_procesoQueNoExiste() {
        given(procesoRepository.bloquear(PROCESO_ID, EMPRESA_ID)).willReturn(Optional.empty());
        ByteArrayInputStream archivo = entrada(XML_NUEVO);

        assertThatThrownBy(() -> servicio.guardar(EMPRESA_ID, USUARIO_ID, RolAcceso.EDITOR, PROCESO_ID, archivo, null))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Proceso no encontrado.");
        verifyNoInteractions(modeloBpmnService, importacionBpmnService);
    }

    @Test
    @DisplayName("Un If-Match que no es la huella del borrador se rechaza con la actual y los diez ultimos cambios")
    void guardar_conIfMatchViejo_avisaConLosUltimosCambios() {
        elProcesoSeBloquea();
        exporta(XML_ACTUAL);
        List<HistorialCambioResponse> historial = LongStream.rangeClosed(1, 12)
                .mapToObj(id -> new HistorialCambioResponse(id, LocalDateTime.now(), "Cambio " + id, "Ana", null, null))
                .toList();
        given(historialCambioService.listarPorProceso(EMPRESA_ID, PROCESO_ID)).willReturn(historial);
        ByteArrayInputStream archivo = entrada(XML_NUEVO);

        assertThatThrownBy(() -> servicio.guardar(EMPRESA_ID, USUARIO_ID, RolAcceso.EDITOR, PROCESO_ID, archivo,
                "\"otra-huella\"")).isInstanceOf(PrecondicionFallidaException.class)
                .hasMessage("El borrador cambió desde que lo leíste.")
                .satisfies(error -> {
                    Map<String, Object> detalle = ((PrecondicionFallidaException) error).getDetalle();
                    assertThat(detalle).containsEntry("huellaActual", Huella.de(XML_ACTUAL))
                            .containsEntry("ultimosCambios", historial.subList(0, 10));
                });
        verifyNoInteractions(importacionBpmnService);
    }

    @Test
    @DisplayName("Con If-Match comodin y el mismo archivo que el borrador no se importa nada")
    void guardar_mismoArchivo_noImporta() {
        elProcesoSeBloquea();
        exporta(XML_ACTUAL);

        ImportacionResponse respuesta = servicio.guardar(EMPRESA_ID, USUARIO_ID, RolAcceso.EDITOR, PROCESO_ID,
                entrada(XML_ACTUAL), "*");

        assertThat(respuesta).isEqualTo(new ImportacionResponse(PROCESO_ID, "Order fulfillment", 0, 0, 0, INFORME,
                Huella.de(XML_ACTUAL)));
        verifyNoInteractions(importacionBpmnService);
        verify(revisionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un archivo que cambia el borrador se autoguarda y, sin revisiones, deja la primera")
    void guardar_conCambiosYSinRevisiones_dejaUnaRevision() throws IOException {
        elProcesoSeBloquea();
        exporta(XML_ACTUAL, XML_NUEVO);
        ArgumentCaptor<InputStream> archivo = ArgumentCaptor.forClass(InputStream.class);
        given(importacionBpmnService.autoguardar(eq(EMPRESA_ID), eq(USUARIO_ID), eq(RolAcceso.EDITOR), eq(PROCESO_ID),
                archivo.capture())).willReturn(importacion(0, 1, 0));
        given(revisionRepository.ultimas(PROCESO_ID, EMPRESA_ID, PageRequest.of(0, 1))).willReturn(List.of());
        guardaLaRevision();
        given(revisionRepository.idsDesde(PROCESO_ID, EMPRESA_ID, PageRequest.of(MAXIMO, 1))).willReturn(List.of());

        // El If-Match con comillas y debil es la misma huella
        ImportacionResponse respuesta = servicio.guardar(EMPRESA_ID, USUARIO_ID, RolAcceso.EDITOR, PROCESO_ID,
                entrada(XML_NUEVO), "W/\"" + Huella.de(XML_ACTUAL) + "\"");

        assertThat(archivo.getValue().readAllBytes()).isEqualTo(XML_NUEVO.getBytes(StandardCharsets.UTF_8));
        assertThat(respuesta.editados()).isEqualTo(1);
        assertThat(respuesta.huella()).isEqualTo(Huella.de(XML_NUEVO));
        RevisionBorrador revision = revisionGuardada();
        assertThat(revision.getEmpresa()).isSameAs(tienda);
        assertThat(revision.getProcesoId()).isEqualTo(PROCESO_ID);
        assertThat(revision.getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(revision.getMotivo()).isEqualTo(MotivoDeRevision.AUTOGUARDADO);
        assertThat(revision.getHuella()).isEqualTo(Huella.de(XML_NUEVO));
        assertThat(descomprimir(revision.getXml())).isEqualTo(XML_NUEVO);
        assertThat(revision.getFecha()).isNotNull();
        verify(revisionRepository, never()).borrarHasta(any(), any(), any());
    }

    @Test
    @DisplayName("Con una revision de hace un minuto, el autoguardado no deja otra")
    void guardar_conUnaRevisionReciente_noDejaOtra() {
        elProcesoSeBloquea();
        exporta(XML_ACTUAL, XML_NUEVO);
        given(importacionBpmnService.autoguardar(eq(EMPRESA_ID), eq(USUARIO_ID), eq(RolAcceso.EDITOR), eq(PROCESO_ID),
                any(InputStream.class))).willReturn(importacion(1, 0, 0));
        given(revisionRepository.ultimas(PROCESO_ID, EMPRESA_ID, PageRequest.of(0, 1)))
                .willReturn(List.of(resumen("otra", LocalDateTime.now().minusMinutes(1))));

        ImportacionResponse respuesta = servicio.guardar(EMPRESA_ID, USUARIO_ID, RolAcceso.EDITOR, PROCESO_ID,
                entrada(XML_NUEVO), null);

        assertThat(respuesta.huella()).isEqualTo(Huella.de(XML_NUEVO));
        verify(revisionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Con la ultima revision vieja deja otra y borra las que pasan del maximo")
    void guardar_conUnaRevisionVieja_dejaOtraYRecorta() {
        elProcesoSeBloquea();
        exporta(XML_ACTUAL, XML_NUEVO);
        given(importacionBpmnService.autoguardar(eq(EMPRESA_ID), eq(USUARIO_ID), eq(RolAcceso.EDITOR), eq(PROCESO_ID),
                any(InputStream.class))).willReturn(importacion(0, 0, 1));
        given(revisionRepository.ultimas(PROCESO_ID, EMPRESA_ID, PageRequest.of(0, 1)))
                .willReturn(List.of(resumen("otra", LocalDateTime.now().minusMinutes(6))));
        guardaLaRevision();
        given(revisionRepository.idsDesde(PROCESO_ID, EMPRESA_ID, PageRequest.of(MAXIMO, 1))).willReturn(List.of(4L));

        servicio.guardar(EMPRESA_ID, USUARIO_ID, RolAcceso.EDITOR, PROCESO_ID, entrada(XML_NUEVO), null);

        assertThat(revisionGuardada().getMotivo()).isEqualTo(MotivoDeRevision.AUTOGUARDADO);
        verify(revisionRepository).borrarHasta(PROCESO_ID, EMPRESA_ID, 4L);
    }

    @Test
    @DisplayName("Un autoguardado que no cambio nada no deja revision")
    void guardar_queNoCambioNada_noDejaRevision() {
        elProcesoSeBloquea();
        exporta(XML_ACTUAL, XML_ACTUAL);
        given(importacionBpmnService.autoguardar(eq(EMPRESA_ID), eq(USUARIO_ID), eq(RolAcceso.EDITOR), eq(PROCESO_ID),
                any(InputStream.class))).willReturn(importacion(0, 0, 0));

        ImportacionResponse respuesta = servicio.guardar(EMPRESA_ID, USUARIO_ID, RolAcceso.EDITOR, PROCESO_ID,
                entrada(XML_NUEVO), null);

        assertThat(respuesta.huella()).isEqualTo(Huella.de(XML_ACTUAL));
        verify(revisionRepository, never()).ultimas(any(), any(), any());
        verify(revisionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un archivo que pasa del tope llega a la importacion con un byte de mas, para que lo rechace")
    void guardar_archivoQueSePasaDelTope_llegaConUnByteDeMas() throws IOException {
        elProcesoSeBloquea();
        exporta(XML_ACTUAL, XML_ACTUAL);
        ArgumentCaptor<InputStream> archivo = ArgumentCaptor.forClass(InputStream.class);
        given(importacionBpmnService.autoguardar(eq(EMPRESA_ID), eq(USUARIO_ID), eq(RolAcceso.EDITOR), eq(PROCESO_ID),
                archivo.capture())).willReturn(importacion(0, 0, 0));
        int tope = (int) LimitesXml.BPMN.bytes();

        servicio.guardar(EMPRESA_ID, USUARIO_ID, RolAcceso.EDITOR, PROCESO_ID, new ByteArrayInputStream(new byte[tope
                + 10]), null);

        assertThat(archivo.getValue().readAllBytes()).hasSize(tope + 1);
    }

    @Test
    @DisplayName("Un archivo que no se puede leer falla antes de tocar el borrador")
    void guardar_archivoQueNoSePuedeLeer() {
        elProcesoSeBloquea();
        InputStream roto = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Se corto la conexion.");
            }
        };

        assertThatThrownBy(() -> servicio.guardar(EMPRESA_ID, USUARIO_ID, RolAcceso.EDITOR, PROCESO_ID, roto, null))
                .isInstanceOf(UncheckedIOException.class).hasRootCauseMessage("Se corto la conexion.");
        verifyNoInteractions(modeloBpmnService, importacionBpmnService);
    }

    // ---------- Punto de guardado ----------

    @Test
    @DisplayName("Un punto de guardado igual a la ultima revision la devuelve sin guardar otra")
    void puntoDeGuardado_igualALaUltima_laDevuelve() {
        elProcesoSeBloquea();
        exporta(XML_ACTUAL);
        RevisionBorradorResponse ultima = resumen(Huella.de(XML_ACTUAL), LocalDateTime.now().minusHours(1));
        given(revisionRepository.ultimas(PROCESO_ID, EMPRESA_ID, PageRequest.of(0, 1))).willReturn(List.of(ultima));

        assertThat(servicio.puntoDeGuardado(EMPRESA_ID, USUARIO_ID, PROCESO_ID, MotivoDeRevision.CIERRE))
                .isSameAs(ultima);
        verify(revisionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un punto de guardado distinto de la ultima revision guarda una con su motivo")
    void puntoDeGuardado_distinto_guardaUnaRevision() {
        elProcesoSeBloquea();
        exporta(XML_ACTUAL);
        given(revisionRepository.ultimas(PROCESO_ID, EMPRESA_ID, PageRequest.of(0, 1)))
                .willReturn(List.of(resumen("otra", LocalDateTime.now().minusHours(1))));
        guardaLaRevision();
        given(revisionRepository.idsDesde(PROCESO_ID, EMPRESA_ID, PageRequest.of(MAXIMO, 1))).willReturn(List.of());

        RevisionBorradorResponse respuesta = servicio.puntoDeGuardado(EMPRESA_ID, USUARIO_ID, PROCESO_ID,
                MotivoDeRevision.CIERRE);

        assertThat(respuesta.id()).isEqualTo(REVISION_ID);
        assertThat(respuesta.motivo()).isEqualTo(MotivoDeRevision.CIERRE);
        assertThat(respuesta.huella()).isEqualTo(Huella.de(XML_ACTUAL));
        assertThat(respuesta.usuarioId()).isEqualTo(USUARIO_ID);
        assertThat(respuesta.fecha()).isNotNull();
        assertThat(descomprimir(revisionGuardada().getXml())).isEqualTo(XML_ACTUAL);
    }

    @Test
    @DisplayName("El primer punto de guardado de un proceso siempre guarda")
    void puntoDeGuardado_sinRevisiones_guarda() {
        elProcesoSeBloquea();
        exporta(XML_ACTUAL);
        given(revisionRepository.ultimas(PROCESO_ID, EMPRESA_ID, PageRequest.of(0, 1))).willReturn(List.of());
        guardaLaRevision();
        given(revisionRepository.idsDesde(PROCESO_ID, EMPRESA_ID, PageRequest.of(MAXIMO, 1))).willReturn(List.of());

        servicio.puntoDeGuardado(EMPRESA_ID, USUARIO_ID, PROCESO_ID, MotivoDeRevision.CIERRE);

        assertThat(revisionGuardada().getMotivo()).isEqualTo(MotivoDeRevision.CIERRE);
    }

    // ---------- Revisiones y su modelo ----------

    @Test
    @DisplayName("Las revisiones de un proceso que no existe no se listan")
    void revisiones_procesoQueNoExiste() {
        given(procesoRepository.existsByIdAndEmpresaIdAndActivoTrue(PROCESO_ID, EMPRESA_ID)).willReturn(false);

        assertThatThrownBy(() -> servicio.revisiones(EMPRESA_ID, PROCESO_ID))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Proceso no encontrado.");
        verifyNoInteractions(revisionRepository);
    }

    @Test
    @DisplayName("Las revisiones de un proceso son sus resumenes")
    void revisiones_sonLosResumenes() {
        given(procesoRepository.existsByIdAndEmpresaIdAndActivoTrue(PROCESO_ID, EMPRESA_ID)).willReturn(true);
        List<RevisionBorradorResponse> resumenes = List.of(resumen("h", LocalDateTime.now()));
        given(revisionRepository.resumenes(PROCESO_ID, EMPRESA_ID)).willReturn(resumenes);

        assertThat(servicio.revisiones(EMPRESA_ID, PROCESO_ID)).isEqualTo(resumenes);
    }

    @Test
    @DisplayName("El modelo de una revision es su archivo descomprimido")
    void modelo_esElArchivoDescomprimido() {
        given(procesoRepository.existsByIdAndEmpresaIdAndActivoTrue(PROCESO_ID, EMPRESA_ID)).willReturn(true);
        laRevisionExiste(XML_VIEJO);

        assertThat(servicio.modelo(EMPRESA_ID, PROCESO_ID, REVISION_ID)).isEqualTo(XML_VIEJO);
    }

    @Test
    @DisplayName("Una revision que pasa del tope se lee hasta un byte de mas, para que se note")
    void modelo_quePasaDelTope_seLeeConUnByteDeMas() {
        given(procesoRepository.existsByIdAndEmpresaIdAndActivoTrue(PROCESO_ID, EMPRESA_ID)).willReturn(true);
        int tope = (int) LimitesXml.BPMN.bytes();
        char[] largo = new char[tope + 10];
        Arrays.fill(largo, 'a');
        laRevisionExiste(new String(largo));

        assertThat(servicio.modelo(EMPRESA_ID, PROCESO_ID, REVISION_ID)).hasSize(tope + 1);
    }

    @Test
    @DisplayName("Una revision guardada que no es gzip falla al leerla")
    void modelo_revisionDanada() {
        given(procesoRepository.existsByIdAndEmpresaIdAndActivoTrue(PROCESO_ID, EMPRESA_ID)).willReturn(true);
        given(revisionRepository.findByIdAndProcesoIdAndEmpresaId(REVISION_ID, PROCESO_ID, EMPRESA_ID))
                .willReturn(Optional.of(RevisionBorrador.builder().id(REVISION_ID)
                        .xml(XML_VIEJO.getBytes(StandardCharsets.UTF_8)).build()));

        assertThatThrownBy(() -> servicio.modelo(EMPRESA_ID, PROCESO_ID, REVISION_ID))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    @DisplayName("El modelo de una revision que no existe no se encuentra")
    void modelo_revisionQueNoExiste() {
        given(procesoRepository.existsByIdAndEmpresaIdAndActivoTrue(PROCESO_ID, EMPRESA_ID)).willReturn(true);
        given(revisionRepository.findByIdAndProcesoIdAndEmpresaId(REVISION_ID, PROCESO_ID, EMPRESA_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.modelo(EMPRESA_ID, PROCESO_ID, REVISION_ID))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Revisión no encontrada.");
    }

    // ---------- Restaurar ----------

    @Test
    @DisplayName("Restaurar la revision que el borrador ya es no guarda ni reemplaza nada")
    void restaurar_mismaHuella_noHaceNada() {
        elProcesoSeBloquea();
        laRevisionExiste(XML_ACTUAL);
        exporta(XML_ACTUAL);

        ImportacionResponse respuesta = servicio.restaurar(EMPRESA_ID, USUARIO_ID, RolAcceso.ADMINISTRADOR,
                PROCESO_ID, REVISION_ID);

        assertThat(respuesta).isEqualTo(new ImportacionResponse(PROCESO_ID, "Order fulfillment", 0, 0, 0, INFORME,
                Huella.de(XML_ACTUAL)));
        verifyNoInteractions(importacionBpmnService);
        verify(revisionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Restaurar otra revision guarda antes el borrador y lo reemplaza por el archivo de la revision")
    void restaurar_otraRevision_guardaElActualYReemplaza() throws IOException {
        elProcesoSeBloquea();
        laRevisionExiste(XML_VIEJO);
        exporta(XML_ACTUAL, XML_VIEJO);
        guardaLaRevision();
        given(revisionRepository.idsDesde(PROCESO_ID, EMPRESA_ID, PageRequest.of(MAXIMO, 1))).willReturn(List.of());
        ArgumentCaptor<InputStream> archivo = ArgumentCaptor.forClass(InputStream.class);
        given(importacionBpmnService.reemplazar(eq(EMPRESA_ID), eq(USUARIO_ID), eq(RolAcceso.ADMINISTRADOR),
                eq(PROCESO_ID), archivo.capture())).willReturn(importacion(2, 1, 0));

        ImportacionResponse respuesta = servicio.restaurar(EMPRESA_ID, USUARIO_ID, RolAcceso.ADMINISTRADOR,
                PROCESO_ID, REVISION_ID);

        RevisionBorrador antes = revisionGuardada();
        assertThat(antes.getMotivo()).isEqualTo(MotivoDeRevision.ANTES_DE_RESTAURAR);
        assertThat(antes.getHuella()).isEqualTo(Huella.de(XML_ACTUAL));
        assertThat(descomprimir(antes.getXml())).isEqualTo(XML_ACTUAL);
        assertThat(archivo.getValue().readAllBytes()).isEqualTo(XML_VIEJO.getBytes(StandardCharsets.UTF_8));
        assertThat(respuesta.creados()).isEqualTo(2);
        assertThat(respuesta.huella()).isEqualTo(Huella.de(XML_VIEJO));
    }

    @Test
    @DisplayName("Restaurar una revision que no existe no toca el borrador")
    void restaurar_revisionQueNoExiste() {
        elProcesoSeBloquea();
        given(revisionRepository.findByIdAndProcesoIdAndEmpresaId(REVISION_ID, PROCESO_ID, EMPRESA_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.restaurar(EMPRESA_ID, USUARIO_ID, RolAcceso.ADMINISTRADOR, PROCESO_ID,
                REVISION_ID)).isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Revisión no encontrada.");
        verifyNoInteractions(importacionBpmnService, modeloBpmnService);
    }

    // ---------- Ayudas ----------

    private void elProcesoSeBloquea() {
        given(procesoRepository.bloquear(PROCESO_ID, EMPRESA_ID)).willReturn(Optional.of(proceso));
    }

    /** Lo que exporta el borrador, una llamada tras otra. */
    private void exporta(String xml, String... despues) {
        ModeloBpmn[] siguientes = Arrays.stream(despues).map(otro -> new ModeloBpmn("order-fulfillment.bpmn", otro))
                .toArray(ModeloBpmn[]::new);
        given(modeloBpmnService.exportar(EMPRESA_ID, PROCESO_ID))
                .willReturn(new ModeloBpmn("order-fulfillment.bpmn", xml), siguientes);
    }

    private void guardaLaRevision() {
        given(revisionRepository.save(any(RevisionBorrador.class))).willAnswer(llamada -> {
            RevisionBorrador revision = llamada.getArgument(0);
            revision.setId(REVISION_ID);
            return revision;
        });
    }

    private RevisionBorrador revisionGuardada() {
        ArgumentCaptor<RevisionBorrador> revision = ArgumentCaptor.forClass(RevisionBorrador.class);
        verify(revisionRepository).save(revision.capture());
        return revision.getValue();
    }

    private void laRevisionExiste(String xml) {
        given(revisionRepository.findByIdAndProcesoIdAndEmpresaId(REVISION_ID, PROCESO_ID, EMPRESA_ID))
                .willReturn(Optional.of(RevisionBorrador.builder().id(REVISION_ID).empresa(tienda)
                        .procesoId(PROCESO_ID).motivo(MotivoDeRevision.AUTOGUARDADO).huella(Huella.de(xml))
                        .xml(comprimir(xml)).fecha(LocalDateTime.now()).build()));
    }

    private static RevisionBorradorResponse resumen(String huella, LocalDateTime fecha) {
        return new RevisionBorradorResponse(4L, MotivoDeRevision.AUTOGUARDADO, huella, USUARIO_ID, fecha);
    }

    private static ImportacionResponse importacion(int creados, int editados, int dadosDeBaja) {
        return new ImportacionResponse(PROCESO_ID, "Order fulfillment", creados, editados, dadosDeBaja, INFORME);
    }

    private static ByteArrayInputStream entrada(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] comprimir(String xml) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(xml.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static String descomprimir(byte[] comprimido) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(comprimido))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
