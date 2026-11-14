package com.facimus.procesos.common.archivos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** PR 61: archivos de prueba armados byte a byte: lo justo para que cada tipo se reconozca por su contenido. */
public final class Archivos {

    public static final byte[] PDF = ascii("""
            %PDF-1.4
            1 0 obj << /Type /Catalog >> endobj
            trailer << /Root 1 0 R >>
            %%EOF
            """);
    public static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13, 'I', 'H', 'D',
        'R', 0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0, (byte) 0x90, 'w', 'S', (byte) 0xde};
    public static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0, 16, 'J', 'F', 'I', 'F',
        0, 1, 1, 0, 0, 1, 0, 1, 0, 0};
    public static final byte[] GIF = ascii("GIF89a\u0001\u0000\u0001\u0000\u0000\u0000\u0000;");
    public static final byte[] SVG_CON_SCRIPT = utf8("""
            <?xml version="1.0" encoding="UTF-8"?>
            <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10">
              <script>alert(document.cookie)</script>
              <rect width="10" height="10"/>
            </svg>
            """);
    public static final byte[] TEXTO = utf8("Envolver con papel de regalo y cinta verde.\n");
    public static final byte[] HTML = utf8("""
            <!DOCTYPE html>
            <html><head><title>x</title></head><body><script>alert(1)</script></body></html>
            """);
    /** El comienzo de un ejecutable de Windows: MZ, y en 0x3c donde empieza su cabecera PE. */
    public static final byte[] EXE = ejecutable();
    public static final byte[] BPMN = utf8("""
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" id="Definitions_1">
              <bpmn:process id="Process_1"/>
            </bpmn:definitions>
            """);

    private Archivos() {
    }

    /** Un documento de Word minimo: el indice de tipos y su documento. */
    public static byte[] docx() {
        return zip(Map.of("[Content_Types].xml", utf8("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/"
                + "content-types\"/>"), "word/document.xml", utf8("<w:document/>")));
    }

    /** Una hoja de calculo de Excel minima. */
    public static byte[] xlsx() {
        return zip(Map.of("[Content_Types].xml", utf8("<Types/>"), "xl/workbook.xml", utf8("<workbook/>")));
    }

    /** Un texto de OpenOffice: su primera entrada es {@code mimetype}, sin comprimir. */
    public static byte[] odt() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            byte[] tipo = ascii("application/vnd.oasis.opendocument.text");
            ZipEntry mimetype = new ZipEntry("mimetype");
            mimetype.setMethod(ZipEntry.STORED);
            mimetype.setSize(tipo.length);
            CRC32 crc = new CRC32();
            crc.update(tipo);
            mimetype.setCrc(crc.getValue());
            zip.putNextEntry(mimetype);
            zip.write(tipo);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("content.xml"));
            zip.write(utf8("<office:document-content/>"));
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /** Un ZIP cualquiera, que no es ningun documento. */
    public static byte[] zipSuelto() {
        return zip(Map.of("leeme.txt", utf8("hola")));
    }

    /** Un documento de Word que al abrirse son {@code megas} megas de ceros: pesa unos pocos KB. */
    public static byte[] bomba(int megas) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write(utf8("<Types/>"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            byte[] ceros = new byte[1024 * 1024];
            for (int i = 0; i < megas; i++) {
                zip.write(ceros);
            }
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /** Un ZIP con una entrada que, al extraerse, saldria de su carpeta. */
    public static byte[] zipQueSale() {
        return zip(Map.of("[Content_Types].xml", utf8("<Types/>"), "word/document.xml", utf8("<w:document/>"),
                "../../etc/cron.d/x", utf8("* * * * * root sh")));
    }

    public static byte[] zip(Map<String, byte[]> entradas) {
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

    private static byte[] ejecutable() {
        byte[] exe = new byte[256];
        exe[0] = 'M';
        exe[1] = 'Z';
        exe[0x3c] = (byte) 0x80;
        exe[0x80] = 'P';
        exe[0x81] = 'E';
        return exe;
    }

    private static byte[] ascii(String texto) {
        return texto.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] utf8(String texto) {
        return texto.getBytes(StandardCharsets.UTF_8);
    }
}
