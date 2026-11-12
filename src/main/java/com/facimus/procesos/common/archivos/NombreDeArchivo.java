package com.facimus.procesos.common.archivos;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * PR 61: el nombre con que se guarda y se descarga un archivo. Se queda solo con la ultima parte de una ruta, sin
 * caracteres de control ni los que un sistema de archivos no acepta, sin puntos al principio, con la extension del
 * tipo que se detecto y con un largo razonable. Un nombre que queda vacio, o que Windows reserva, pasa a ser
 * "documento". El nombre nunca entra en una clave del almacen: solo se muestra y se manda al descargar.
 */
public final class NombreDeArchivo {

    private static final int LARGO_MAXIMO = 150;
    private static final String POR_DEFECTO = "documento";
    private static final Set<String> RESERVADOS_EN_WINDOWS = Set.of("con", "prn", "aux", "nul", "com1", "com2", "com3",
            "com4", "com5", "com6", "com7", "com8", "com9", "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7",
            "lpt8", "lpt9");

    private NombreDeArchivo() {
    }

    /** El nombre saneado, terminado en la extension del tipo real. */
    public static String sanear(String nombre, TipoDeArchivo tipo) {
        String base = nombre == null ? "" : Normalizer.normalize(nombre, Normalizer.Form.NFC);
        int barra = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        base = base.substring(barra + 1);
        StringBuilder limpio = new StringBuilder();
        base.codePoints().filter(NombreDeArchivo::seQueda).forEach(limpio::appendCodePoint);
        String sinPuntos = limpio.toString().strip().replaceAll("^[.\\s]+", "").replaceAll("[.\\s]+$", "");
        String sinExtension = quitarExtension(sinPuntos);
        if (sinExtension.isBlank() || RESERVADOS_EN_WINDOWS.contains(sinExtension.toLowerCase(Locale.ROOT))) {
            sinExtension = POR_DEFECTO;
        }
        String extension = "." + tipo.extension();
        int espacio = LARGO_MAXIMO - extension.length();
        if (sinExtension.codePointCount(0, sinExtension.length()) > espacio) {
            sinExtension = sinExtension.substring(0, sinExtension.offsetByCodePoints(0, espacio)).strip();
        }
        return sinExtension + extension;
    }

    private static boolean seQueda(int caracter) {
        return !Character.isISOControl(caracter) && "<>:\"/\\|?*".indexOf(caracter) < 0
                && Character.getType(caracter) != Character.FORMAT;
    }

    private static String quitarExtension(String nombre) {
        int punto = nombre.lastIndexOf('.');
        return punto > 0 ? nombre.substring(0, punto) : nombre;
    }
}
