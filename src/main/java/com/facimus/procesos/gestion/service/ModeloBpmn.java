package com.facimus.procesos.gestion.service;

import java.text.Normalizer;
import java.util.Locale;

/**
 * PR 53: un proceso en BPMN 2.0.2, listo para descargarse: el XML y el nombre del archivo. El nombre sale del proceso,
 * sin tildes ni espacios, para que sirva en cualquier sistema de archivos.
 */
public record ModeloBpmn(String archivo, String xml) {

    private static final int LARGO_MAXIMO = 60;

    /** El borrador: {@code order-fulfillment.bpmn}. */
    public static ModeloBpmn delBorrador(String proceso, String xml) {
        return new ModeloBpmn(nombre(proceso) + ".bpmn", xml);
    }

    /** Una version: {@code order-fulfillment-v3.bpmn}. */
    public static ModeloBpmn deLaVersion(String proceso, int numero, String xml) {
        return new ModeloBpmn(nombre(proceso) + "-v" + numero + ".bpmn", xml);
    }

    static String nombre(String proceso) {
        String sinTildes = Normalizer.normalize(proceso == null ? "" : proceso, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String nombre = sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (nombre.length() > LARGO_MAXIMO) {
            nombre = nombre.substring(0, LARGO_MAXIMO).replaceAll("-+$", "");
        }
        return nombre.isEmpty() ? "proceso" : nombre;
    }
}
