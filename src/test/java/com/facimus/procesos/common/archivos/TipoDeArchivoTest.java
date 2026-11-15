package com.facimus.procesos.common.archivos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PR 65: de la lista blanca, que tipos tienen texto que buscar. */
class TipoDeArchivoTest {

    @Test
    @DisplayName("tienen texto el PDF, el texto plano, el CSV, el Markdown y los de Office y OpenOffice; nada mas")
    void tieneTexto() {
        assertThat(Arrays.stream(TipoDeArchivo.values()).filter(TipoDeArchivo::tieneTexto))
                .containsExactlyInAnyOrder(TipoDeArchivo.PDF, TipoDeArchivo.TEXTO, TipoDeArchivo.CSV,
                        TipoDeArchivo.MARKDOWN, TipoDeArchivo.DOCX, TipoDeArchivo.XLSX, TipoDeArchivo.PPTX,
                        TipoDeArchivo.ODT, TipoDeArchivo.ODS);
    }
}
