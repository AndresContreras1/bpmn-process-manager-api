package com.facimus.procesos.modelado.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.facimus.procesos.common.almacen.Almacen;
import com.facimus.procesos.common.almacen.ArchivoNoEncontradoException;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.LecturaDeFilas;
import com.facimus.procesos.gestion.service.Paquete;
import com.facimus.procesos.gestion.service.Paquete.FilasJson;
import com.facimus.procesos.gestion.service.ParteDeLaExportacion;
import com.facimus.procesos.modelado.model.EstadoDocumento;
import com.facimus.procesos.modelado.model.EstadoManual;
import com.facimus.procesos.modelado.service.DiagramaService;
import com.facimus.procesos.modelado.service.ModeloBpmnService;

/**
 * PR 66: lo que modelado pone en la exportacion. En la carpeta de cada proceso: su borrador en BPMN y en JSON (si sigue
 * activo), su documentacion en Markdown, su ficha de caracterizacion, los manuales que se armaron y los documentos con
 * cada version; y en la raiz, los comentarios de la tienda. De los documentos salen los bytes de las versiones
 * disponibles: una bloqueada por el antivirus o todavia sin revisar figura en la lista, pero su archivo no sale. Los
 * documentos eliminados no salen. Las claves del almacen se usan para leer y no se exportan. Un archivo disponible
 * que el almacen ya no tiene no frena la exportacion: su lista lo marca como faltante, y la tienda se puede llevar lo
 * demas.
 */
@Component
@Order(2)
public class ExportacionDelModelado implements ParteDeLaExportacion {

    static final String DOCUMENTACION = """
            select bpmn_id, markdown, fecha_creacion, fecha_modificacion
            from documentacion where empresa_id = ? and proceso_id = ? order by id
            """;

    static final String CARACTERIZACION = """
            select objetivo, alcance, entradas, salidas, proveedores, clientes, indicadores, riesgos, normativa,
                   fecha_creacion, fecha_modificacion
            from caracterizaciones where empresa_id = ? and proceso_id = ?
            """;

    static final String MANUALES = """
            select id, version_numero, idioma, estado, motivo, pedido_por, pedido_en, listo_en, clave_html, clave_pdf
            from manuales where empresa_id = ? and proceso_id = ? order by id
            """;

    static final String DOCUMENTOS = """
            select id, bpmn_id, nombre, version_actual, creado_por, fecha_creacion, modificado_por, fecha_modificacion
            from documentos where empresa_id = ? and proceso_id = ? and activo order by id
            """;

    static final String VERSIONES_DE_DOCUMENTO = """
            select numero, nombre, tipo, tamano, sha256, estado, motivo, revisado, subido_por, fecha, clave
            from versiones_documento where empresa_id = ? and documento_id = ? order by numero
            """;

    static final String COMENTARIOS = """
            select id, proceso_id, padre_id, bpmn_id, version_numero, autor_id, markdown, menciones, resuelto_por,
                   resuelto_en, editado_en, eliminado_por, eliminado_en, fecha_creacion
            from comentarios where empresa_id = ? and id > ? order by id limit ?
            """;

    private static final String CLAVE = "clave";
    private static final String CLAVE_HTML = "claveHtml";
    private static final String CLAVE_PDF = "clavePdf";
    private static final String ESTADO = "estado";
    private static final String NUMERO = "numero";

    private static final Logger log = LoggerFactory.getLogger(ExportacionDelModelado.class);

    private final ProcesoRepository procesoRepository;
    private final ModeloBpmnService modeloBpmnService;
    private final DiagramaService diagramaService;
    private final LecturaDeFilas lector;
    private final Almacen almacen;

    public ExportacionDelModelado(ProcesoRepository procesoRepository, ModeloBpmnService modeloBpmnService,
            DiagramaService diagramaService, LecturaDeFilas lector, Almacen almacen) {
        this.procesoRepository = procesoRepository;
        this.modeloBpmnService = modeloBpmnService;
        this.diagramaService = diagramaService;
        this.lector = lector;
        this.almacen = almacen;
    }

    @Override
    public void escribir(Long empresaId, Paquete paquete) {
        List<Proceso> procesos = new ArrayList<>(procesoRepository.findAllByEmpresaId(empresaId));
        procesos.sort(Comparator.comparing(Proceso::getId));
        for (Proceso proceso : procesos) {
            String carpeta = Paquete.carpetaDeProceso(proceso.getId(), proceso.getNombre()) + "/";
            if (proceso.isActivo()) {
                paquete.texto(carpeta + "borrador.bpmn", modeloBpmnService.exportar(empresaId, proceso.getId()).xml());
                paquete.json(carpeta + "borrador.json", diagramaService.obtener(empresaId, proceso.getId()));
            }
            escribirDocumentacion(empresaId, proceso.getId(), carpeta, paquete);
            escribirManuales(empresaId, proceso.getId(), carpeta + "manuales/", paquete);
            escribirDocumentos(empresaId, proceso.getId(), carpeta + "documentos/", paquete);
        }
        try (FilasJson comentarios = paquete.filasJson("comentarios.json")) {
            lector.recorrer(COMENTARIOS, empresaId, comentarios::agregar);
        }
    }

    private void escribirDocumentacion(Long empresaId, Long procesoId, String carpeta, Paquete paquete) {
        List<Map<String, Object>> textos = filas(DOCUMENTACION, empresaId, procesoId);
        if (!textos.isEmpty()) {
            paquete.json(carpeta + "documentacion.json", textos);
        }
        List<Map<String, Object>> ficha = filas(CARACTERIZACION, empresaId, procesoId);
        if (!ficha.isEmpty()) {
            paquete.json(carpeta + "caracterizacion.json", ficha.getFirst());
        }
    }

    /**
     * La lista de manuales, sin las claves del almacen, y el HTML y el PDF de cada uno que quedo disponible. Los
     * archivos van antes que la lista, para que la lista diga cual falto ({@code "faltantes": ["pdf"]}).
     */
    private void escribirManuales(Long empresaId, Long procesoId, String carpeta, Paquete paquete) {
        List<Map<String, Object>> manuales = filas(MANUALES, empresaId, procesoId);
        if (manuales.isEmpty()) {
            return;
        }
        for (Map<String, Object> manual : manuales) {
            String html = (String) manual.remove(CLAVE_HTML);
            String pdf = (String) manual.remove(CLAVE_PDF);
            if (EstadoManual.DISPONIBLE.name().equals(manual.get(ESTADO))) {
                String nombre = carpeta + manual.get("id") + "-v" + manual.get("versionNumero");
                List<String> faltantes = new ArrayList<>();
                if (!copiar(paquete, nombre + ".html", html)) {
                    faltantes.add("html");
                }
                if (!copiar(paquete, nombre + ".pdf", pdf)) {
                    faltantes.add("pdf");
                }
                if (!faltantes.isEmpty()) {
                    manual.put("faltantes", faltantes);
                }
            }
        }
        paquete.json(carpeta + "manuales.json", manuales);
    }

    /**
     * La lista de documentos con sus versiones, sin las claves del almacen, y el archivo de cada version disponible:
     * {@code documentos/12/v2-Packing-instructions.pdf}. El nombre que eligio quien lo subio pasa por
     * {@link Paquete#tramo} antes de entrar en la ruta. Una version cuyo archivo el almacen ya no tiene sale sin
     * {@code archivo} y con {@code "faltante": true}.
     */
    private void escribirDocumentos(Long empresaId, Long procesoId, String carpeta, Paquete paquete) {
        List<Map<String, Object>> documentos = filas(DOCUMENTOS, empresaId, procesoId);
        if (documentos.isEmpty()) {
            return;
        }
        for (Map<String, Object> documento : documentos) {
            Object documentoId = documento.get("id");
            List<Map<String, Object>> versiones = filas(VERSIONES_DE_DOCUMENTO, empresaId, documentoId);
            for (Map<String, Object> version : versiones) {
                String clave = (String) version.remove(CLAVE);
                if (EstadoDocumento.DISPONIBLE.name().equals(version.get(ESTADO))) {
                    String ruta = carpeta + documentoId + "/v" + version.get(NUMERO) + "-"
                            + Paquete.tramo((String) version.get("nombre"));
                    if (copiar(paquete, ruta, clave)) {
                        version.put("archivo", ruta);
                    } else {
                        version.put("faltante", true);
                    }
                }
            }
            documento.put("versiones", versiones);
        }
        paquete.json(carpeta + "documentos.json", documentos);
    }

    /** Copia el archivo de esa clave a esa ruta; si el almacen ya no lo tiene, no copia nada y devuelve false. */
    private boolean copiar(Paquete paquete, String ruta, String clave) {
        try (InputStream contenido = almacen.abrir(clave)) {
            paquete.copiar(ruta, contenido);
            return true;
        } catch (ArchivoNoEncontradoException _) {
            log.warn("La exportacion sigue sin {}: el almacen ya no tiene su archivo", ruta);
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + ruta + " del almacén", e);
        }
    }

    private List<Map<String, Object>> filas(String consulta, Long empresaId, Object id) {
        return lector.filas(consulta, empresaId, id);
    }
}
