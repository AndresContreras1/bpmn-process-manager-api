package com.facimus.procesos.gestion.service;

import java.io.InputStream;
import java.text.Normalizer;
import java.util.List;

/**
 * PR 66: el ZIP de una exportacion, visto por quien escribe una parte de el. Cada archivo se escribe entero antes de
 * empezar el siguiente, y el paquete anota su tamano y su SHA-256 para el manifiesto. Las rutas son relativas, de
 * tramos que empiezan por una letra o un numero ASCII y siguen con letras, numeros, puntos, guiones y guiones bajos:
 * una ruta con cualquier otra cosa, con {@code ..} o repetida se rechaza. Lo que viene de las personas, como el nombre
 * de un documento, entra en una ruta solo despues de pasar por {@link #tramo}.
 */
public interface Paquete {

    /** El nombre del manifiesto, en la raiz: ninguna parte puede usarlo. */
    String MANIFIESTO = "manifest.json";

    /** Un archivo JSON con ese valor. */
    void json(String ruta, Object valor);

    /** Un archivo de texto en UTF-8, tal cual: un BPMN, un SVG o un JSON que ya viene escrito. */
    void texto(String ruta, String contenido);

    /** Un archivo copiado de un flujo, sin cargarlo entero en memoria. El flujo lo cierra quien lo abrio. */
    void copiar(String ruta, InputStream contenido);

    /** Un arreglo JSON que se escribe de a un elemento. Hasta cerrarlo no se puede empezar otro archivo. */
    FilasJson filasJson(String ruta);

    /** Un CSV (RFC 4180) con esa cabecera, que se escribe de a una fila. Hasta cerrarlo no se empieza otro archivo. */
    FilasCsv filasCsv(String ruta, List<String> cabecera);

    /** Los elementos de un arreglo JSON abierto. */
    interface FilasJson extends AutoCloseable {

        void agregar(Object fila);

        @Override
        void close();
    }

    /** Las filas de un CSV abierto: una celda por columna de la cabecera, en su orden. */
    interface FilasCsv extends AutoCloseable {

        void agregar(List<?> celdas);

        @Override
        void close();
    }

    /** La carpeta de un proceso: su id, que no se repite, y su nombre legible: {@code procesos/7-order-fulfillment}. */
    static String carpetaDeProceso(long procesoId, String nombre) {
        return "procesos/" + procesoId + "-" + ModeloBpmn.nombre(nombre);
    }

    /**
     * Un texto libre como tramo de una ruta: sin tildes, con letras y numeros ASCII, puntos, guiones y guiones bajos, y
     * cualquier otra cosa como un guion. Empieza por una letra o un numero, no termina en punto ni en guion y no pasa
     * de 80 caracteres; si no queda nada, es {@code sin-nombre}.
     */
    static String tramo(String texto) {
        String sinTildes = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD);
        StringBuilder tramo = new StringBuilder();
        for (int i = 0; i < sinTildes.length() && tramo.length() < Tramos.LARGO_MAXIMO; i++) {
            char letra = sinTildes.charAt(i);
            if (Tramos.sePermite(letra)) {
                tramo.append(letra);
            } else if (Character.getType(letra) != Character.NON_SPACING_MARK && !tramo.isEmpty()
                    && tramo.charAt(tramo.length() - 1) != '-') {
                tramo.append('-');
            }
        }
        int inicio = 0;
        while (inicio < tramo.length() && !Tramos.esLetraONumero(tramo.charAt(inicio))) {
            inicio++;
        }
        int fin = tramo.length();
        while (fin > inicio && (tramo.charAt(fin - 1) == '-' || tramo.charAt(fin - 1) == '.')) {
            fin--;
        }
        return fin > inicio ? tramo.substring(inicio, fin) : "sin-nombre";
    }

    /** Lo que puede llevar un tramo de una ruta del paquete. */
    final class Tramos {

        static final int LARGO_MAXIMO = 80;

        private Tramos() {
        }

        /** Empieza por una letra o un numero y solo lleva lo que {@link #sePermite}. */
        public static boolean esValido(String tramo) {
            if (tramo.isEmpty() || !esLetraONumero(tramo.charAt(0))) {
                return false;
            }
            for (int i = 1; i < tramo.length(); i++) {
                if (!sePermite(tramo.charAt(i))) {
                    return false;
                }
            }
            return true;
        }

        static boolean esLetraONumero(char letra) {
            return letra >= 'a' && letra <= 'z' || letra >= 'A' && letra <= 'Z' || letra >= '0' && letra <= '9';
        }

        /** Letras y numeros ASCII, punto, guion y guion bajo. */
        static boolean sePermite(char letra) {
            return esLetraONumero(letra) || letra == '.' || letra == '-' || letra == '_';
        }
    }
}
