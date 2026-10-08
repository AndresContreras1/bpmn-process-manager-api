package com.facimus.procesos.common.correo;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.messageresolver.AbstractMessageResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Escribe los correos con las plantillas de {@code correos/}: cada una en HTML y en texto, y sus palabras en
 * {@code correos/mensajes}, en espanol, ingles y frances. Thymeleaf es aqui solo el motor de las plantillas: la web es
 * una SPA y no hay vistas del servidor (D18). En el HTML, lo que viene de afuera, como el nombre de una tienda, sale
 * escapado.
 */
@Component
public class Redactor {

    /** Los idiomas que tienen sus palabras; cualquier otro recibe el espanol. */
    public static final Set<String> IDIOMAS = Set.of("es", "en", "fr");

    private static final Locale POR_DEFECTO = Locale.of("es");

    private final MessageSource mensajes;
    private final TemplateEngine html;
    private final TemplateEngine texto;

    public Redactor() {
        ResourceBundleMessageSource fuente = new ResourceBundleMessageSource();
        fuente.setBasename("correos/mensajes");
        fuente.setDefaultEncoding(StandardCharsets.UTF_8.name());
        fuente.setFallbackToSystemLocale(false);
        this.mensajes = fuente;
        this.html = motor(TemplateMode.HTML, ".html");
        this.texto = motor(TemplateMode.TEXT, ".txt");
    }

    /** El idioma en que se escribe: el pedido si tiene sus palabras, el espanol si no. */
    public static Locale idioma(Locale pedido) {
        return pedido != null && IDIOMAS.contains(pedido.getLanguage()) ? Locale.of(pedido.getLanguage())
                : POR_DEFECTO;
    }

    public CorreoSaliente redactar(String plantilla, Locale pedido, String para, Map<String, Object> datos) {
        Locale idioma = idioma(pedido);
        Context contexto = new Context(idioma, datos);
        String asunto = mensajes.getMessage(plantilla + ".asunto", null, idioma);
        return new CorreoSaliente(para, asunto, texto.process(plantilla, contexto), html.process(plantilla, contexto));
    }

    private TemplateEngine motor(TemplateMode modo, String sufijo) {
        ClassLoaderTemplateResolver plantillas = new ClassLoaderTemplateResolver();
        plantillas.setPrefix("correos/");
        plantillas.setSuffix(sufijo);
        plantillas.setTemplateMode(modo);
        plantillas.setCharacterEncoding(StandardCharsets.UTF_8.name());
        plantillas.setCacheable(true);
        TemplateEngine motor = new TemplateEngine();
        motor.setTemplateResolver(plantillas);
        motor.setMessageResolver(new AbstractMessageResolver() {
            @Override
            public String resolveMessage(ITemplateContext contexto, Class<?> origen, String clave,
                    Object[] parametros) {
                return mensajes.getMessage(clave, parametros, contexto.getLocale());
            }

            @Override
            public String createAbsentMessageRepresentation(ITemplateContext contexto, Class<?> origen, String clave,
                    Object[] parametros) {
                throw new IllegalStateException("Falta el mensaje " + clave + " de los correos.");
            }
        });
        return motor;
    }
}
