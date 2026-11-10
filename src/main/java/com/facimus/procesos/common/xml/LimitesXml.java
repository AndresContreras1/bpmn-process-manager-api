package com.facimus.procesos.common.xml;

import java.time.Duration;

/**
 * PR 52: lo mas que se lee de un XML. Las cifras de {@link #BPMN} sobran para el diagrama mas grande que se dibuja a
 * mano (unos cientos de elementos) y cortan antes de que un archivo hostil gaste memoria o tiempo.
 *
 * @param bytes tamano del documento, antes de parsearlo y mientras se lee
 * @param elementos numero de elementos de todo el documento
 * @param profundidad anidamiento maximo de elementos
 * @param atributosPorElemento atributos de un mismo elemento
 * @param texto largo de un mismo texto o atributo, en caracteres
 * @param tiempo lo que puede tardar la lectura entera
 */
public record LimitesXml(long bytes, int elementos, int profundidad, int atributosPorElemento, int texto,
        Duration tiempo) {

    /** Para un modelo BPMN: 5 MB, 100 000 elementos, 64 niveles, 64 atributos, 1 MB de texto y 10 segundos. */
    public static final LimitesXml BPMN = new LimitesXml(5L * 1024 * 1024, 100_000, 64, 64, 1024 * 1024,
            Duration.ofSeconds(10));
}
