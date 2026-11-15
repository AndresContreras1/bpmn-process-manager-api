package com.facimus.procesos.common.markdown;

import java.util.List;
import java.util.regex.Pattern;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

/**
 * PR 62: la documentacion se escribe en Markdown y se guarda tambien como HTML, convertido en el servidor. Dos
 * barreras: el HTML que alguien escriba dentro del Markdown sale escapado, como texto, y lo que produce la conversion
 * pasa por una lista blanca (OWASP Java HTML Sanitizer) de lo que un documento necesita: parrafos, titulos, listas,
 * enfasis, codigo, citas, tablas y enlaces http, https o mailto que no se llevan la pagina. Ni imagenes (un pixel
 * espia es una imagen), ni estilos, ni nada que corra.
 */
public class Markdown {

    private static final List<Extension> EXTENSIONES = List.of(TablesExtension.create());

    /** Un enlace va a otro sitio por http o https, o a un correo; uno vacio o relativo no es un enlace. */
    private static final Pattern ENLACE = Pattern.compile("(?i)(https?://|mailto:)\\S+");

    private static final PolicyFactory LISTA_BLANCA = new HtmlPolicyBuilder()
            .allowElements("p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "strong", "em", "code", "pre",
                    "blockquote", "hr", "br", "table", "thead", "tbody", "tr", "th", "td", "a")
            .allowStandardUrlProtocols()
            .allowAttributes("href").matching(ENLACE).onElements("a")
            .requireRelsOnLinks("nofollow", "noopener", "noreferrer")
            .allowAttributes("align").matching(Pattern.compile("left|center|right")).onElements("th", "td")
            .allowAttributes("start").matching(Pattern.compile("\\d{1,6}")).onElements("ol")
            .toFactory();

    private final Parser parser = Parser.builder().extensions(EXTENSIONES).build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().extensions(EXTENSIONES).escapeHtml(true)
            .sanitizeUrls(true).build();

    /** El HTML seguro de un texto en Markdown; vacio si el texto es vacio. */
    public String aHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        return LISTA_BLANCA.sanitize(renderer.render(parser.parse(markdown))).strip();
    }
}
