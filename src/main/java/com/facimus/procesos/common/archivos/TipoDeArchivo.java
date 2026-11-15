package com.facimus.procesos.common.archivos;

import java.util.Arrays;
import java.util.Optional;

/**
 * PR 61: los tipos que se aceptan como documento, la lista blanca. Cualquier otro se rechaza, diga lo que diga su
 * nombre. {@link #seMuestra()} es falso para lo que un navegador podria ejecutar: un SVG o un HTML siempre se bajan
 * como archivo, nunca se abren en la pagina.
 */
public enum TipoDeArchivo {

    PDF("application/pdf", "pdf", true),
    PNG("image/png", "png", true),
    JPEG("image/jpeg", "jpg", true),
    GIF("image/gif", "gif", true),
    WEBP("image/webp", "webp", true),
    SVG("image/svg+xml", "svg", false),
    TEXTO("text/plain", "txt", true),
    CSV("text/csv", "csv", true),
    MARKDOWN("text/markdown", "md", true),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx", true),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx", true),
    PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx", true),
    ODT("application/vnd.oasis.opendocument.text", "odt", true),
    ODS("application/vnd.oasis.opendocument.spreadsheet", "ods", true),
    BPMN("application/bpmn+xml", "bpmn", false);

    private final String mime;
    private final String extension;
    private final boolean seMuestra;

    TipoDeArchivo(String mime, String extension, boolean seMuestra) {
        this.mime = mime;
        this.extension = extension;
        this.seMuestra = seMuestra;
    }

    public String mime() {
        return mime;
    }

    public String extension() {
        return extension;
    }

    /** Si el navegador lo puede mostrar sin riesgo: lo que no, se sirve como {@code application/octet-stream}. */
    public boolean seMuestra() {
        return seMuestra;
    }

    /** Si es un ZIP por dentro, que hay que revisar entrada por entrada. */
    public boolean esContenedor() {
        return this == DOCX || this == XLSX || this == PPTX || this == ODT || this == ODS;
    }

    /**
     * PR 65: si tiene texto que buscar: los documentos de PDF, de Office y de OpenOffice, y el texto plano. Una imagen,
     * un SVG o un BPMN se buscan solo por su nombre.
     */
    public boolean tieneTexto() {
        return this == PDF || this == TEXTO || this == CSV || this == MARKDOWN || esContenedor();
    }

    public static Optional<TipoDeArchivo> deMime(String mime) {
        return Arrays.stream(values()).filter(tipo -> tipo.mime.equals(mime)).findFirst();
    }
}
