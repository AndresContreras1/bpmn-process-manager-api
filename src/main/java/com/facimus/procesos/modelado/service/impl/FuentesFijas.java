package com.facimus.procesos.modelado.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.apache.fontbox.FontBoxFont;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.font.CIDFontMapping;
import org.apache.pdfbox.pdmodel.font.FontMapper;
import org.apache.pdfbox.pdmodel.font.FontMappers;
import org.apache.pdfbox.pdmodel.font.FontMapping;
import org.apache.pdfbox.pdmodel.font.PDCIDSystemInfo;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;

/**
 * PR 62: las fuentes de PDFBox, sin mirar las del equipo. Sin esto, la primera vez que PDFBox necesita una fuente que
 * el PDF no trae recorre cada fuente instalada y deja una cache en la carpeta personal: en un equipo con muchas tarda
 * minutos, y lo que sale depende de la maquina. El manual solo usa las 14 fuentes estandar, que pone el lector del
 * PDF; para medir y dibujar sus letras basta la Liberation Sans que viene con PDFBox. Asi el manual sale igual en
 * cualquier maquina y nada se escribe fuera del almacen.
 */
final class FuentesFijas implements FontMapper {

    private static final String LIBERATION_SANS = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";

    private static boolean instaladas;

    private final TrueTypeFont fuente;

    FuentesFijas() {
        try (InputStream archivo = FontMapper.class.getResourceAsStream(LIBERATION_SANS)) {
            if (archivo == null) {
                throw new IllegalStateException("PDFBox no trae " + LIBERATION_SANS);
            }
            fuente = new TTFParser().parse(new RandomAccessReadBuffer(archivo));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + LIBERATION_SANS, e);
        }
    }

    /** Las pone para PDFBox entero, una sola vez: PDFBox tiene una sola tabla de fuentes por JVM. */
    static synchronized void instalar() {
        if (!instaladas) {
            FontMappers.set(new FuentesFijas());
            instaladas = true;
        }
    }

    // Ninguna es un reemplazo de ultima hora: es la fuente elegida, asi que PDFBox no avisa nada

    @Override
    public FontMapping<TrueTypeFont> getTrueTypeFont(String nombre, PDFontDescriptor descriptor) {
        return new FontMapping<>(fuente, false);
    }

    @Override
    public FontMapping<FontBoxFont> getFontBoxFont(String nombre, PDFontDescriptor descriptor) {
        return new FontMapping<>(fuente, false);
    }

    @Override
    public CIDFontMapping getCIDFont(String nombre, PDFontDescriptor descriptor, PDCIDSystemInfo sistema) {
        return new CIDFontMapping(null, fuente, false);
    }
}
