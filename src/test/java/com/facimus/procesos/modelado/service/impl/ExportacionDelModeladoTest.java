package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.common.almacen.Almacen;
import com.facimus.procesos.common.almacen.ArchivoNoEncontradoException;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.LecturaDeFilasDePrueba;
import com.facimus.procesos.gestion.service.ModeloBpmn;
import com.facimus.procesos.gestion.service.PaqueteEnMemoria;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.service.DiagramaService;
import com.facimus.procesos.modelado.service.ModeloBpmnService;

/**
 * PR 66: lo que modelado pone en la exportacion: el borrador de los procesos activos, la documentacion, la ficha, los
 * manuales y los documentos de cada uno, sin las claves del almacen ni los archivos que no estan disponibles, y los
 * comentarios de la tienda.
 */
class ExportacionDelModeladoTest {

    private static final Long EMPRESA = 1L;

    private final ProcesoRepository procesoRepository = mock(ProcesoRepository.class);
    private final ModeloBpmnService modeloBpmnService = mock(ModeloBpmnService.class);
    private final DiagramaService diagramaService = mock(DiagramaService.class);
    private final LecturaDeFilasDePrueba lector = new LecturaDeFilasDePrueba();
    private final Almacen almacen = mock(Almacen.class);
    private final PaqueteEnMemoria paquete = new PaqueteEnMemoria();
    private final ExportacionDelModelado parte = new ExportacionDelModelado(procesoRepository, modeloBpmnService,
            diagramaService, lector, almacen);
    private final DiagramaResponse diagrama = mock(DiagramaResponse.class);

    @BeforeEach
    void preparar() {
        Proceso zeta = Proceso.builder().id(9L).nombre("Zeta").activo(false).build();
        Proceso despacho = Proceso.builder().id(3L).nombre("Despacho").activo(true).build();
        when(procesoRepository.findAllByEmpresaId(EMPRESA)).thenReturn(List.of(zeta, despacho));
        when(modeloBpmnService.exportar(EMPRESA, 3L)).thenReturn(ModeloBpmn.delBorrador("Despacho", "<bpmn/>"));
        when(diagramaService.obtener(EMPRESA, 3L)).thenReturn(diagrama);
    }

    private static Map<String, Object> fila(Object... claveYValor) {
        Map<String, Object> fila = new LinkedHashMap<>();
        for (int i = 0; i < claveYValor.length; i += 2) {
            fila.put((String) claveYValor[i], claveYValor[i + 1]);
        }
        return fila;
    }

    private static InputStream bytes(String contenido) {
        return new ByteArrayInputStream(contenido.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("cada proceso por su id: el activo con su borrador en BPMN y en JSON, el eliminado sin borrador, y "
            + "los comentarios de la tienda al final")
    void borradores() {
        lector.con(ExportacionDelModelado.COMENTARIOS, List.of(fila("id", 1L, "markdown", "Hola")), EMPRESA);

        parte.escribir(EMPRESA, paquete);

        assertThat(paquete.archivos().keySet()).containsExactly("procesos/3-despacho/borrador.bpmn",
                "procesos/3-despacho/borrador.json", "comentarios.json");
        assertThat(paquete.archivos()).containsEntry("procesos/3-despacho/borrador.bpmn", "<bpmn/>")
                .containsEntry("procesos/3-despacho/borrador.json", diagrama);
        assertThat(paquete.filas("comentarios.json")).containsExactly(fila("id", 1L, "markdown", "Hola"));
        verify(modeloBpmnService, never()).exportar(EMPRESA, 9L);
        verify(diagramaService, never()).obtener(EMPRESA, 9L);
    }

    @Test
    @DisplayName("la documentacion y la ficha van a la carpeta del proceso, tambien de uno eliminado; sin nada, nada")
    void documentacionYFicha() {
        lector.con(ExportacionDelModelado.DOCUMENTACION, List.of(fila("bpmnId", null, "markdown", "# Despacho")),
                EMPRESA, 3L)
                .con(ExportacionDelModelado.CARACTERIZACION, List.of(fila("objetivo", "Entregar")), EMPRESA, 9L);

        parte.escribir(EMPRESA, paquete);

        assertThat(paquete.archivos()).containsEntry("procesos/3-despacho/documentacion.json",
                List.of(fila("bpmnId", null, "markdown", "# Despacho")))
                .containsEntry("procesos/9-zeta/caracterizacion.json", fila("objetivo", "Entregar"))
                .doesNotContainKeys("procesos/3-despacho/caracterizacion.json", "procesos/9-zeta/documentacion.json");
    }

    @Test
    @DisplayName("los manuales salen en su lista sin sus claves, y el HTML y el PDF solo de los disponibles")
    void manuales() {
        lector.con(ExportacionDelModelado.MANUALES, List.of(
                fila("id", 5L, "versionNumero", 2, "estado", "DISPONIBLE", "claveHtml", "m/5/html", "clavePdf",
                        "m/5/pdf"),
                fila("id", 6L, "versionNumero", 3, "estado", "FALLIDO", "claveHtml", null, "clavePdf", null)),
                EMPRESA, 3L);
        when(almacen.abrir("m/5/html")).thenReturn(bytes("<html/>"));
        when(almacen.abrir("m/5/pdf")).thenReturn(bytes("%PDF"));

        parte.escribir(EMPRESA, paquete);

        assertThat(paquete.archivos()).containsEntry("procesos/3-despacho/manuales/manuales.json", List.of(
                fila("id", 5L, "versionNumero", 2, "estado", "DISPONIBLE"),
                fila("id", 6L, "versionNumero", 3, "estado", "FALLIDO")));
        assertThat(new String((byte[]) paquete.archivos().get("procesos/3-despacho/manuales/5-v2.html"),
                StandardCharsets.UTF_8)).isEqualTo("<html/>");
        assertThat(new String((byte[]) paquete.archivos().get("procesos/3-despacho/manuales/5-v2.pdf"),
                StandardCharsets.UTF_8)).isEqualTo("%PDF");
        assertThat(paquete.archivos()).doesNotContainKeys("procesos/3-despacho/manuales/6-v3.html",
                "procesos/3-despacho/manuales/6-v3.pdf", "procesos/9-zeta/manuales/manuales.json");
    }

    @Test
    @DisplayName("los documentos salen con sus versiones sin claves, y solo el archivo de las disponibles, con su "
            + "nombre saneado")
    void documentos() {
        lector.con(ExportacionDelModelado.DOCUMENTOS, List.of(fila("id", 12L, "nombre", "Guía")), EMPRESA, 3L)
                .con(ExportacionDelModelado.VERSIONES_DE_DOCUMENTO, List.of(
                        fila("numero", 1, "nombre", "../Guía de empaque.pdf", "estado", "DISPONIBLE", "clave", "d/1"),
                        fila("numero", 2, "nombre", "virus.exe", "estado", "BLOQUEADO", "clave", "d/2"),
                        fila("numero", 3, "nombre", "nueva.pdf", "estado", "PENDIENTE", "clave", "d/3")),
                        EMPRESA, 12L);
        when(almacen.abrir("d/1")).thenReturn(bytes("%PDF-1"));

        parte.escribir(EMPRESA, paquete);

        String archivo = "procesos/3-despacho/documentos/12/v1-Guia-de-empaque.pdf";
        assertThat(paquete.archivos()).containsEntry("procesos/3-despacho/documentos/documentos.json", List.of(
                fila("id", 12L, "nombre", "Guía", "versiones", List.of(
                        fila("numero", 1, "nombre", "../Guía de empaque.pdf", "estado", "DISPONIBLE", "archivo",
                                archivo),
                        fila("numero", 2, "nombre", "virus.exe", "estado", "BLOQUEADO"),
                        fila("numero", 3, "nombre", "nueva.pdf", "estado", "PENDIENTE")))));
        assertThat(new String((byte[]) paquete.archivos().get(archivo), StandardCharsets.UTF_8)).isEqualTo("%PDF-1");
        verify(almacen, never()).abrir("d/2");
        verify(almacen, never()).abrir("d/3");
        assertThat(paquete.archivos().keySet()).filteredOn(ruta -> ruta.contains("/documentos/12/")).hasSize(1);
    }

    @Test
    @DisplayName("un archivo disponible que el almacen ya no tiene no frena la exportacion: no sale, y su lista lo "
            + "marca como faltante")
    void archivosFaltantes() {
        lector.con(ExportacionDelModelado.MANUALES, List.of(
                fila("id", 5L, "versionNumero", 2, "estado", "DISPONIBLE", "claveHtml", "m/5/html", "clavePdf",
                        "m/5/pdf"),
                fila("id", 7L, "versionNumero", 4, "estado", "DISPONIBLE", "claveHtml", "m/7/html", "clavePdf",
                        "m/7/pdf")),
                EMPRESA, 3L)
                .con(ExportacionDelModelado.DOCUMENTOS, List.of(fila("id", 12L)), EMPRESA, 3L)
                .con(ExportacionDelModelado.VERSIONES_DE_DOCUMENTO, List.of(
                        fila("numero", 1, "nombre", "perdido.pdf", "estado", "DISPONIBLE", "clave", "d/1"),
                        fila("numero", 2, "nombre", "nuevo.pdf", "estado", "DISPONIBLE", "clave", "d/2")),
                        EMPRESA, 12L);
        when(almacen.abrir("m/5/html")).thenReturn(bytes("<html/>"));
        when(almacen.abrir("m/5/pdf")).thenThrow(new ArchivoNoEncontradoException("m/5/pdf"));
        when(almacen.abrir("m/7/html")).thenThrow(new ArchivoNoEncontradoException("m/7/html"));
        when(almacen.abrir("m/7/pdf")).thenThrow(new ArchivoNoEncontradoException("m/7/pdf"));
        when(almacen.abrir("d/1")).thenThrow(new ArchivoNoEncontradoException("d/1"));
        when(almacen.abrir("d/2")).thenReturn(bytes("%PDF-2"));

        parte.escribir(EMPRESA, paquete);

        assertThat(paquete.archivos()).containsEntry("procesos/3-despacho/manuales/manuales.json", List.of(
                fila("id", 5L, "versionNumero", 2, "estado", "DISPONIBLE", "faltantes", List.of("pdf")),
                fila("id", 7L, "versionNumero", 4, "estado", "DISPONIBLE", "faltantes", List.of("html", "pdf"))));
        String nuevo = "procesos/3-despacho/documentos/12/v2-nuevo.pdf";
        assertThat(paquete.archivos()).containsEntry("procesos/3-despacho/documentos/documentos.json", List.of(
                fila("id", 12L, "versiones", List.of(
                        fila("numero", 1, "nombre", "perdido.pdf", "estado", "DISPONIBLE", "faltante", true),
                        fila("numero", 2, "nombre", "nuevo.pdf", "estado", "DISPONIBLE", "archivo", nuevo)))));
        assertThat(paquete.archivos()).containsKeys("procesos/3-despacho/manuales/5-v2.html", nuevo)
                .doesNotContainKeys("procesos/3-despacho/manuales/5-v2.pdf", "procesos/3-despacho/manuales/7-v4.html",
                        "procesos/3-despacho/manuales/7-v4.pdf", "procesos/3-despacho/documentos/12/v1-perdido.pdf")
                .containsKey("comentarios.json");
    }

    @Test
    @DisplayName("un archivo que el almacen no deja leer hace fallar la exportacion, con su ruta")
    void almacenRoto() {
        lector.con(ExportacionDelModelado.MANUALES, List.of(fila("id", 5L, "versionNumero", 2, "estado",
                "DISPONIBLE", "claveHtml", "m/5/html", "clavePdf", "m/5/pdf")), EMPRESA, 3L);
        when(almacen.abrir("m/5/html")).thenReturn(new InputStream() {
            @Override
            public int read() {
                return -1;
            }

            @Override
            public void close() throws IOException {
                throw new IOException("roto");
            }
        });

        assertThatThrownBy(() -> parte.escribir(EMPRESA, paquete)).isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("manuales/5-v2.html");
        verify(diagramaService, never()).obtener(EMPRESA, 9L);
    }
}
