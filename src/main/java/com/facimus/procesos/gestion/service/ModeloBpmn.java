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
        String sinTildes = Normalizer.normalize(proceso == null ? "" : proceso, Normalizer.Form.NFD);
        StringBuilder nombre = new StringBuilder();
        for (char letra : sinTildes.toLowerCase(Locale.ROOT).toCharArray()) {
            if (letra >= 'a' && letra <= 'z' || letra >= '0' && letra <= '9') {
                nombre.append(letra);
            } else if (Character.getType(letra) != Character.NON_SPACING_MARK && !nombre.isEmpty()
                    && nombre.charAt(nombre.length() - 1) != '-') {
                // Cualquier otra cosa separa palabras, salvo las tildes que la normalizacion dejo sueltas
                nombre.append('-');
            }
        }
        String limpio = sinGuionAlFinal(nombre.length() > LARGO_MAXIMO ? nombre.substring(0, LARGO_MAXIMO)
                : nombre.toString());
        return limpio.isEmpty() ? "proceso" : limpio;
    }

    private static String sinGuionAlFinal(String nombre) {
        int fin = nombre.length();
        while (fin > 0 && nombre.charAt(fin - 1) == '-') {
            fin--;
        }
        return nombre.substring(0, fin);
    }
}
