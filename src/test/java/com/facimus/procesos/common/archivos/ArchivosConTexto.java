package com.facimus.procesos.common.archivos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * PR 65: documentos de verdad, con texto adentro, para leerlo: lo justo de cada formato para que su parser lo abra. El
 * PDF lo escribe PDFBox; quien lo pide ya tiene que tener puestas las fuentes fijas del manual (un contexto de Spring
 * las pone al arrancar), o PDFBox recorre las del equipo.
 */
public final class ArchivosConTexto {

    private static final String TIPOS_OOXML = "http://schemas.openxmlformats.org/package/2006/content-types";
    private static final String RELACIONES = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String DOCUMENTO_PRINCIPAL = "http://schemas.openxmlformats.org/officeDocument/2006/"
            + "relationships/officeDocument";
    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";

    private ArchivosConTexto() {
    }

    /** Un PDF de una pagina con cada linea escrita en Helvetica, que no va incrustada. */
    public static byte[] pdf(String... lineas) {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage();
            documento.addPage(pagina);
            try (PDPageContentStream contenido = new PDPageContentStream(documento, pagina)) {
                contenido.beginText();
                contenido.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contenido.newLineAtOffset(72, 700);
                for (String linea : lineas) {
                    contenido.showText(linea);
                    contenido.newLineAtOffset(0, -16);
                }
                contenido.endText();
            }
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Un Word con un parrafo por cada texto. */
    public static byte[] docx(String... parrafos) {
        StringBuilder cuerpo = new StringBuilder();
        for (String parrafo : parrafos) {
            cuerpo.append("<w:p><w:r><w:t>").append(escapar(parrafo)).append("</w:t></w:r></w:p>");
        }
        Map<String, String> partes = new LinkedHashMap<>();
        partes.put("[Content_Types].xml", XML + "<Types xmlns=\"" + TIPOS_OOXML + "\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-"
                + "officedocument.wordprocessingml.document.main+xml\"/></Types>");
        partes.put("_rels/.rels", relacion("word/document.xml"));
        partes.put("word/document.xml", XML + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/"
                + "wordprocessingml/2006/main\"><w:body>" + cuerpo + "</w:body></w:document>");
        return zip(partes);
    }

    /** Un Excel de una hoja con una celda por cada texto, en la primera fila. */
    public static byte[] xlsx(String... celdas) {
        StringBuilder fila = new StringBuilder();
        char columna = 'A';
        for (String celda : celdas) {
            fila.append("<c r=\"").append(columna++).append("1\" t=\"inlineStr\"><is><t>").append(escapar(celda))
                    .append("</t></is></c>");
        }
        String hoja = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
        Map<String, String> partes = new LinkedHashMap<>();
        partes.put("[Content_Types].xml", XML + "<Types xmlns=\"" + TIPOS_OOXML + "\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-"
                + "officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-"
                + "officedocument.spreadsheetml.worksheet+xml\"/></Types>");
        partes.put("_rels/.rels", relacion("xl/workbook.xml"));
        partes.put("xl/workbook.xml", XML + "<workbook xmlns=\"" + hoja + "\" xmlns:r=\"http://schemas.openxmlformats."
                + "org/officeDocument/2006/relationships\"><sheets><sheet name=\"Hoja1\" sheetId=\"1\" r:id=\"rId1\"/>"
                + "</sheets></workbook>");
        partes.put("xl/_rels/workbook.xml.rels", XML + "<Relationships xmlns=\"" + RELACIONES + "\"><Relationship "
                + "Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" "
                + "Target=\"worksheets/sheet1.xml\"/></Relationships>");
        partes.put("xl/worksheets/sheet1.xml", XML + "<worksheet xmlns=\"" + hoja + "\"><sheetData><row r=\"1\">" + fila
                + "</row></sheetData></worksheet>");
        return zip(partes);
    }

    /** Un texto de OpenOffice con un parrafo por cada texto. */
    public static byte[] odt(String... parrafos) {
        StringBuilder cuerpo = new StringBuilder();
        for (String parrafo : parrafos) {
            cuerpo.append("<text:p>").append(escapar(parrafo)).append("</text:p>");
        }
        return openOffice("application/vnd.oasis.opendocument.text", "<office:text>" + cuerpo + "</office:text>");
    }

    /** Una hoja de calculo de OpenOffice con una celda por cada texto. */
    public static byte[] ods(String... celdas) {
        StringBuilder fila = new StringBuilder();
        for (String celda : celdas) {
            fila.append("<table:table-cell office:value-type=\"string\"><text:p>").append(escapar(celda))
                    .append("</text:p></table:table-cell>");
        }
        return openOffice("application/vnd.oasis.opendocument.spreadsheet", "<office:spreadsheet><table:table "
                + "table:name=\"Hoja1\"><table:table-row>" + fila + "</table:table-row></table:table>"
                + "</office:spreadsheet>");
    }

    private static byte[] openOffice(String tipo, String cuerpo) {
        String contenido = XML + "<office:document-content xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:"
                + "1.0\" xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\" xmlns:table=\"urn:oasis:names:"
                + "tc:opendocument:xmlns:table:1.0\" office:version=\"1.2\"><office:body>" + cuerpo
                + "</office:body></office:document-content>";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            byte[] mimetype = tipo.getBytes(StandardCharsets.US_ASCII);
            ZipEntry entrada = new ZipEntry("mimetype");
            entrada.setMethod(ZipEntry.STORED);
            entrada.setSize(mimetype.length);
            CRC32 crc = new CRC32();
            crc.update(mimetype);
            entrada.setCrc(crc.getValue());
            zip.putNextEntry(entrada);
            zip.write(mimetype);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("content.xml"));
            zip.write(contenido.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static String relacion(String destino) {
        return XML + "<Relationships xmlns=\"" + RELACIONES + "\"><Relationship Id=\"rId1\" Type=\""
                + DOCUMENTO_PRINCIPAL + "\" Target=\"" + destino + "\"/></Relationships>";
    }

    private static byte[] zip(Map<String, String> partes) {
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        partes.forEach((nombre, contenido) -> entradas.put(nombre, contenido.getBytes(StandardCharsets.UTF_8)));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entrada : entradas.entrySet()) {
                zip.putNextEntry(new ZipEntry(entrada.getKey()));
                zip.write(entrada.getValue());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static String escapar(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
