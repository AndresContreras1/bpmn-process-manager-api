package com.facimus.procesos.common.archivos;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.ParseContext;

/**
 * PR 61: que es un archivo por lo que tiene adentro, con el detector de Apache Tika (firmas de bytes, sin parsers). El
 * nombre no cuenta: un ejecutable que se llama {@code informe.pdf} es un ejecutable. Tika ve un documento de Office o
 * de OpenOffice como un ZIP; aqui se mira su indice para saber cual es, y un ZIP que no es ninguno de ellos no se
 * acepta. El texto plano se distingue por la extension solo entre texto, CSV y Markdown, que son lo mismo por dentro.
 */
public class DetectorDeTipo {

    /** Las firmas de bytes de Tika: el detector de tika-core, sin los de los parsers. */
    private static final MimeTypes TIKA = MimeTypes.getDefaultMimeTypes();

    /** El tipo, si esta en la lista blanca. */
    public Optional<TipoDeArchivo> detectar(Path archivo, String nombre) {
        MediaType tipo = porContenido(archivo);
        String base = tipo.getBaseType().toString();
        boolean contenedor = TipoDeArchivo.deMime(base).filter(TipoDeArchivo::esContenedor).isPresent();
        if (contenedor || "application/zip".equals(base) || base.startsWith("application/x-tika-")) {
            return deZip(archivo);
        }
        if ("text/plain".equals(base)) {
            return Optional.of(deTexto(nombre));
        }
        if ("application/xml".equals(base) || "text/xml".equals(base)) {
            return esBpmn(archivo) ? Optional.of(TipoDeArchivo.BPMN) : Optional.empty();
        }
        return TipoDeArchivo.deMime(base).filter(encontrado -> !encontrado.esContenedor());
    }

    private static MediaType porContenido(Path archivo) {
        try (TikaInputStream entrada = TikaInputStream.get(new BufferedInputStream(Files.newInputStream(archivo)))) {
            return TIKA.detect(entrada, new Metadata(), new ParseContext());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo subido", e);
        }
    }

    private static TipoDeArchivo deTexto(String nombre) {
        String minusculas = nombre == null ? "" : nombre.toLowerCase(Locale.ROOT);
        if (minusculas.endsWith(".csv")) {
            return TipoDeArchivo.CSV;
        }
        if (minusculas.endsWith(".md") || minusculas.endsWith(".markdown")) {
            return TipoDeArchivo.MARKDOWN;
        }
        return TipoDeArchivo.TEXTO;
    }

    /** Un documento de Office por las partes que tiene; uno de OpenOffice por su entrada {@code mimetype}. */
    private static Optional<TipoDeArchivo> deZip(Path archivo) {
        try (ZipFile zip = new ZipFile(archivo.toFile())) {
            ZipEntry mimetype = zip.getEntry("mimetype");
            if (mimetype != null && mimetype.getSize() < 200) {
                try (InputStream entrada = zip.getInputStream(mimetype)) {
                    String declarado = new String(entrada.readNBytes(200), StandardCharsets.US_ASCII).trim();
                    return TipoDeArchivo.deMime(declarado).filter(TipoDeArchivo::esContenedor);
                }
            }
            if (zip.getEntry("[Content_Types].xml") == null) {
                return Optional.empty();
            }
            if (zip.getEntry("word/document.xml") != null) {
                return Optional.of(TipoDeArchivo.DOCX);
            }
            if (zip.getEntry("xl/workbook.xml") != null) {
                return Optional.of(TipoDeArchivo.XLSX);
            }
            if (zip.getEntry("ppt/presentation.xml") != null) {
                return Optional.of(TipoDeArchivo.PPTX);
            }
            return Optional.empty();
        } catch (IOException _) {
            return Optional.empty();
        }
    }

    /** Un XML que es un BPMN: su elemento raiz es {@code definitions} del espacio de nombres del modelo. */
    private static boolean esBpmn(Path archivo) {
        try (InputStream entrada = Files.newInputStream(archivo)) {
            String inicio = new String(entrada.readNBytes(4096), StandardCharsets.UTF_8);
            return inicio.contains("http://www.omg.org/spec/BPMN/20100524/MODEL") && inicio.contains("definitions");
        } catch (IOException _) {
            return false;
        }
    }
}
