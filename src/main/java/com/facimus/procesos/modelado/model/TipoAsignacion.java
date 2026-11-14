package com.facimus.procesos.modelado.model;

/**
 * PR 70: a quien le llega una tarea de usuario cuando un caso pasa por ella. Lo de siempre es la bandeja del rol de la
 * lane; los otros tres la dan a una persona al crearse, y si esa persona no se puede saber o ya no puede completar
 * tareas, la tarea se queda en la bandeja del rol.
 */
public enum TipoAsignacion {
    /** La bandeja del rol de la lane: la toma quien pertenezca a el. */
    ROL,
    /** Una persona fija de la organizacion. */
    PERSONA,
    /** Quien abrio el caso. */
    INICIADOR,
    /** La persona que dice una variable del caso: la que escribio un campo PERSONA de un formulario anterior. */
    CAMPO;

    /** Lo largo que puede ser la ruta de la variable de una asignacion CAMPO; es lo que cabe en su columna. */
    public static final int LARGO_DEL_CAMPO = 200;

    /**
     * Si una ruta se puede leer como una variable del caso, igual que en una condicion: tramos separados por puntos,
     * cada uno una letra o un guion bajo seguidos de letras, digitos o guiones bajos. Se revisa a mano y no con una
     * expresion regular, que con un tramo repetido se podria quedar dando vueltas.
     */
    public static boolean esRutaDeVariable(String ruta) {
        if (ruta == null || ruta.isEmpty() || ruta.length() > LARGO_DEL_CAMPO) {
            return false;
        }
        for (String tramo : ruta.split("\\.", -1)) {
            if (!esNombre(tramo)) {
                return false;
            }
        }
        return true;
    }

    private static boolean esNombre(String tramo) {
        if (tramo.isEmpty() || !esLetra(tramo.charAt(0))) {
            return false;
        }
        for (int i = 1; i < tramo.length(); i++) {
            char letra = tramo.charAt(i);
            if (!esLetra(letra) && (letra < '0' || letra > '9')) {
                return false;
            }
        }
        return true;
    }

    private static boolean esLetra(char letra) {
        return letra >= 'a' && letra <= 'z' || letra >= 'A' && letra <= 'Z' || letra == '_';
    }
}
