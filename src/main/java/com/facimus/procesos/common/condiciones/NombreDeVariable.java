package com.facimus.procesos.common.condiciones;

/**
 * Como se llama en las variables del caso lo que entrego una tarea: {@code tarea.<nombre del nodo en camello>}. Lo usan
 * el motor, que guarda ahi los datos al completarla, y el diagnostico, que con eso sabe que rutas escribe un formulario
 * (PR 70).
 */
public final class NombreDeVariable {

    /** Bajo que nombre quedan los datos de las tareas completadas. */
    public static final String DE_LAS_TAREAS = "tarea";

    private NombreDeVariable() {
    }

    /**
     * "Pick and pack items" se convierte en "pickAndPackItems": el nombre del nodo tal cual tiene espacios y no se
     * podria escribir en una condicion.
     */
    public static String enCamello(String nombre) {
        String[] palabras = nombre.trim().split("[^\\p{Alnum}]+");
        StringBuilder camello = new StringBuilder();
        for (String palabra : palabras) {
            if (palabra.isEmpty()) {
                continue;
            }
            if (camello.isEmpty()) {
                camello.append(palabra.substring(0, 1).toLowerCase()).append(palabra.substring(1));
            } else {
                camello.append(palabra.substring(0, 1).toUpperCase()).append(palabra.substring(1));
            }
        }
        return camello.toString();
    }

    /** La ruta de un campo del formulario de una tarea: {@code tarea.revisarPedido.aprobador}. */
    public static String deLaTarea(String nombreDelNodo, String campo) {
        return DE_LAS_TAREAS + "." + enCamello(nombreDelNodo) + "." + campo;
    }
}
