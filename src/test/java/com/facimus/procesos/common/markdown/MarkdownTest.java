package com.facimus.procesos.common.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PR 62: de Markdown a un HTML que se puede mostrar sin miedo. */
class MarkdownTest {

    private final Markdown markdown = new Markdown();

    @Test
    @DisplayName("lo de un documento se convierte: titulos, enfasis, listas, codigo, citas y tablas")
    void documento() {
        String html = markdown.aHtml("""
                ## Empaque

                Envolver **dos veces** y con *cinta*.

                1. Medir
                2. Cortar

                > Ojo con el vidrio

                `codigo`

                | Paso | Quien |
                |:-----|------:|
                | Uno  | Ana   |
                """);

        assertThat(html).contains("<h2>Empaque</h2>", "<strong>dos veces</strong>", "<em>cinta</em>", "<ol>",
                "<li>Medir</li>", "<blockquote>", "<code>codigo</code>", "<table>", "<th align=\"left\">Paso</th>",
                "<td align=\"right\">Ana</td>");
    }

    @Test
    @DisplayName("el HTML escrito dentro del Markdown sale como texto, nunca como HTML")
    void htmlCrudo() {
        String html = markdown.aHtml("Hola <script>alert(1)</script> <img src=x onerror=alert(2)> <b>negrita</b>");

        assertThat(html).doesNotContain("<script", "<img", "onerror=", "<b>")
                .contains("&lt;script&gt;", "&lt;b&gt;negrita&lt;/b&gt;");
    }

    @Test
    @DisplayName("un enlace va con rel que no se lleva la pagina; javascript: y data: no son enlaces")
    void enlaces() {
        String html = markdown.aHtml("[ver](https://example.com/a) [mal](javascript:alert(1)) [datos](data:text/"
                + "html;base64,PHNjcmlwdD4=) [correo](mailto:ana@example.com)");

        assertThat(html).contains("href=\"https://example.com/a\"", "rel=\"nofollow noopener noreferrer\"",
                "href=\"mailto:ana&#64;example.com\"").doesNotContain("javascript:", "data:text");
    }

    @Test
    @DisplayName("sin imagenes, ni siquiera escritas en Markdown: un pixel espia es una imagen")
    void imagenes() {
        assertThat(markdown.aHtml("![espia](https://example.com/p.gif)")).doesNotContain("<img", "example.com/p.gif");
    }

    @Test
    @DisplayName("vacio o en blanco da vacio")
    void vacio() {
        assertThat(markdown.aHtml(null)).isEmpty();
        assertThat(markdown.aHtml("   \n ")).isEmpty();
    }
}
