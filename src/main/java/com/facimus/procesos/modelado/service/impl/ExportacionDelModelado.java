package com.facimus.procesos.modelado.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.facimus.procesos.common.almacen.Almacen;
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
 * documentos eliminados no salen. Las claves del almacen se usan para leer y no se exportan.
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

    /** La lista de manuales, sin las claves del almacen, y el HTML y el PDF de cada uno que quedo disponible. */
    private void escribirManuales(Long empresaId, Long procesoId, String carpeta, Paquete paquete) {
        List<Map<String, Object>> manuales = filas(MANUALES, empresaId, procesoId);
        if (manuales.isEmpty()) {
            return;
        }
        List<Map<String, Object>> claves = new ArrayList<>();
        for (Map<String, Object> manual : manuales) {
            Map<String, Object> clave = new LinkedHashMap<>();
            clave.put(CLAVE_HTML, manual.remove(CLAVE_HTML));
            clave.put(CLAVE_PDF, manual.remove(CLAVE_PDF));
            claves.add(clave);
        }
        paquete.json(carpeta + "manuales.json", manuales);
        for (int i = 0; i < manuales.size(); i++) {
            Map<String, Object> manual = manuales.get(i);
            if (EstadoManual.DISPONIBLE.name().equals(manual.get(ESTADO))) {
                String nombre = carpeta + manual.get("id") + "-v" + manual.get("versionNumero");
                copiar(paquete, nombre + ".html", (String) claves.get(i).get(CLAVE_HTML));
                copiar(paquete, nombre + ".pdf", (String) claves.get(i).get(CLAVE_PDF));
            }
        }
    }

    /**
     * La lista de documentos con sus versiones, sin las claves del almacen, y el archivo de cada version disponible:
     * {@code documentos/12/v2-Packing-instructions.pdf}. El nombre que eligio quien lo subio pasa por
     * {@link Paquete#tramo} antes de entrar en la ruta.
     */
    private void escribirDocumentos(Long empresaId, Long procesoId, String carpeta, Paquete paquete) {
        List<Map<String, Object>> documentos = filas(DOCUMENTOS, empresaId, procesoId);
        if (documentos.isEmpty()) {
            return;
        }
        Map<String, String> archivos = new LinkedHashMap<>();
        for (Map<String, Object> documento : documentos) {
            Object documentoId = documento.get("id");
            List<Map<String, Object>> versiones = filas(VERSIONES_DE_DOCUMENTO, empresaId, documentoId);
            for (Map<String, Object> version : versiones) {
                String clave = (String) version.remove(CLAVE);
                if (EstadoDocumento.DISPONIBLE.name().equals(version.get(ESTADO))) {
                    String ruta = carpeta + documentoId + "/v" + version.get(NUMERO) + "-"
                            + Paquete.tramo((String) version.get("nombre"));
                    version.put("archivo", ruta);
                    archivos.put(ruta, clave);
                }
            }
            documento.put("versiones", versiones);
        }
        paquete.json(carpeta + "documentos.json", documentos);
        archivos.forEach((ruta, clave) -> copiar(paquete, ruta, clave));
    }

    private void copiar(Paquete paquete, String ruta, String clave) {
        try (InputStream contenido = almacen.abrir(clave)) {
            paquete.copiar(ruta, contenido);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + ruta + " del almacén", e);
        }
    }

    private List<Map<String, Object>> filas(String consulta, Long empresaId, Object id) {
        return lector.filas(consulta, empresaId, id);
    }
}
