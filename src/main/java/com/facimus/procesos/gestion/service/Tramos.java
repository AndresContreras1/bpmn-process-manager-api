package com.facimus.procesos.gestion.service;

/** PR 66: lo que puede llevar un tramo de una ruta del {@link Paquete} de una exportacion. */
public final class Tramos {

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
