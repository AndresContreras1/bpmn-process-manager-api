package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.apache.fontbox.FontBoxFont;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.font.CIDFontMapping;
import org.apache.pdfbox.pdmodel.font.FontMapper;
import org.apache.pdfbox.pdmodel.font.FontMappers;
import org.apache.pdfbox.pdmodel.font.FontMapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PR 62: PDFBox mide y dibuja con la fuente que trae, sin recorrer las fuentes del equipo. */
class FuentesFijasTest {

    @Test
    @DisplayName("al crear la redaccion PDFBox queda con las fuentes fijas, y se ponen una sola vez")
    void instalar() {
        new RedaccionDelManual();
        FontMapper puestas = FontMappers.instance();

        FuentesFijas.instalar();

        assertThat(puestas).isInstanceOf(FuentesFijas.class);
        assertThat(FontMappers.instance()).isSameAs(puestas);
    }

    @Test
    @DisplayName("cualquier fuente que se pida es la Liberation Sans de PDFBox, y no como reemplazo")
    void cualquieraEsLaMisma() throws IOException {
        FuentesFijas fuentes = new FuentesFijas();

        FontMapping<TrueTypeFont> trueType = fuentes.getTrueTypeFont("Arial", null);
        FontMapping<FontBoxFont> estandar = fuentes.getFontBoxFont("Helvetica", null);
        CIDFontMapping cid = fuentes.getCIDFont("MS-Mincho", null, null);

        assertThat(trueType.getFont().getName()).isEqualTo("LiberationSans");
        assertThat(estandar.getFont()).isSameAs(trueType.getFont());
        assertThat(cid.getTrueTypeFont()).isSameAs(trueType.getFont());
        assertThat(cid.getFont()).isNull();
        assertThat(cid.isCIDFont()).isFalse();
        assertThat(trueType.isFallback()).isFalse();
        assertThat(estandar.isFallback()).isFalse();
        assertThat(cid.isFallback()).isFalse();
    }
}
