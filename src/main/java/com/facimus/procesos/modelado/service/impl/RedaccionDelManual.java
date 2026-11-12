package com.facimus.procesos.modelado.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.w3c.dom.Document;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;

/**
 * PR 62: del contenido de un manual a sus dos archivos. El HTML sale de una plantilla Thymeleaf con los textos en el
 * idioma de quien lo pidio; lo que viene de la documentacion y el dibujo ya estan saneados, y la plantilla no lleva
 * nada que se cargue de afuera. El PDF es ese mismo HTML impreso por openhtmltopdf, con el SVG dibujado por Batik, y
 * sin dejarle buscar ningun recurso: lo que no esta en el HTML no existe.
 */
@Component
public class RedaccionDelManual {

    private final TemplateEngine plantillas;

    public RedaccionDelManual() {
        ClassLoaderTemplateResolver resolutor = new ClassLoaderTemplateResolver();
        resolutor.setPrefix("manuales/");
        resolutor.setSuffix(".html");
        resolutor.setTemplateMode(TemplateMode.HTML);
        resolutor.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolutor.setCacheable(true);
        plantillas = new TemplateEngine();
        plantillas.setTemplateResolver(resolutor);
    }

    /** El manual como un HTML que se abre solo, sin nada de afuera. */
    public String html(Contenido contenido, String idioma) {
        Context contexto = new Context(Locale.forLanguageTag(idioma), Map.of("m", contenido));
        return plantillas.process("manual", contexto);
    }

    /** El mismo HTML impreso como PDF. */
    public byte[] pdf(String html) {
        Document documento = new W3CDom().fromJsoup(Jsoup.parse(html));
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PdfRendererBuilder pdf = new PdfRendererBuilder();
            pdf.useFastMode();
            pdf.useSVGDrawer(new BatikSVGDrawer());
            // Ningun recurso de afuera: ni imagenes, ni hojas de estilo, ni fuentes por URL
            pdf.useUriResolver((base, uri) -> null);
            pdf.withW3cDocument(documento, "");
            pdf.toStream(salida);
            pdf.run();
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo imprimir el manual", e);
        }
    }

    /** Lo que dice un manual, ya en el orden en que se lee. */
    public record Contenido(String proceso, String descripcion, String categoria, int version,
            LocalDateTime publicadaEn, List<Campo> ficha, String dibujo, String documentacion, List<Seccion> secciones,
            List<Adjunto> documentos, LocalDateTime generadoEn) {

        public Contenido {
            ficha = List.copyOf(ficha);
            secciones = List.copyOf(secciones);
            documentos = List.copyOf(documentos);
        }
    }

    /** Un campo de la ficha: la clave de su titulo en la plantilla y lo que dice. */
    public record Campo(String clave, String valor) {
    }

    /** Una lane con sus pasos, de izquierda a derecha como en el dibujo. */
    public record Seccion(String participante, String lane, String rol, List<Paso> pasos) {

        public Seccion {
            pasos = List.copyOf(pasos);
        }
    }

    /** Un paso: su nombre, la clave de su tipo, su descripcion y su documentacion en HTML saneado. */
    public record Paso(String nombre, String tipo, String descripcion, String documentacion) {
    }

    /** Un documento adjunto al proceso, con la version que estaba disponible. */
    public record Adjunto(String nombre, int version) {
    }
}
