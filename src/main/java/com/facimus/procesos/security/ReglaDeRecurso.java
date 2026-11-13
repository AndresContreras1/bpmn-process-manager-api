package com.facimus.procesos.security;

import java.util.Optional;
import java.util.Set;

import com.facimus.procesos.common.model.Accion;

/**
 * PR 60: que accion pide una ruta y sobre que. Es una funcion pura de metodo y patron, para que la tabla se pueda probar
 * entera. Las rutas sin recurso, como crear un proceso o un arco, no estan aqui: las comprueba su servicio.
 */
record ReglaDeRecurso(Accion accion, Clase clase, String tipo) {

    enum Clase {
        PROCESO,
        CARPETA,
        ELEMENTO
    }

    private static final String RAIZ = "/api/v1/";
    /** PR 63: escribir en los comentarios no es modelar: lo puede hacer quien solo lee. */
    private static final String COMENTARIOS = "comentarios";
    private static final Set<String> ELEMENTOS = Set.of("pools", "lanes", "actividades", "gateways", "eventos", "nodos",
            "arcos", "mensajes", "artefactos", "asociaciones", "documentos", "manuales", COMENTARIOS);
    /** Operar un proceso no es modelarlo: abrir un caso o recibir un mensaje solo necesita verlo. */
    private static final Set<String> DE_OPERACION = Set.of("casos", "mensajes-entrantes", "comparar");
    private static final String DIBUJAR = "dibujar";

    /** El archivo de un proceso se descarga; {@code para=dibujar} es la web, que lo necesita para mostrarlo. */
    static Optional<ReglaDeRecurso> de(String metodo, String patron, String para) {
        if (patron == null || !patron.startsWith(RAIZ)) {
            return Optional.empty();
        }
        String[] tramos = patron.substring(RAIZ.length()).split("/");
        boolean lee = "GET".equals(metodo);
        if ("procesos".equals(tramos[0]) && tramos.length > 1 && tramos[1].startsWith("{")) {
            return Optional.of(new ReglaDeRecurso(deProceso(metodo, tramos, para), Clase.PROCESO, null));
        }
        if ("carpetas".equals(tramos[0]) && tramos.length > 1) {
            boolean concesiones = tramos.length > 2 && "concesiones".equals(tramos[2]);
            return Optional.of(new ReglaDeRecurso(concesiones ? Accion.ADMINISTRAR : Accion.MODELAR, Clase.CARPETA,
                    null));
        }
        if (ELEMENTOS.contains(tramos[0]) && tramos.length > 1 && tramos[1].startsWith("{")) {
            Accion escribir = COMENTARIOS.equals(tramos[0]) ? Accion.COMENTAR : Accion.MODELAR;
            return Optional.of(new ReglaDeRecurso(lee ? Accion.VER : escribir, Clase.ELEMENTO, tramos[0]));
        }
        return Optional.empty();
    }

    private static Accion deProceso(String metodo, String[] tramos, String para) {
        boolean lee = "GET".equals(metodo);
        if (tramos.length == 2) {
            return switch (metodo) {
                case "GET" -> Accion.VER;
                case "PATCH" -> Accion.PUBLICAR;
                case "DELETE" -> Accion.ADMINISTRAR;
                default -> Accion.MODELAR;
            };
        }
        String tramo = tramos[2];
        String ultimo = tramos[tramos.length - 1];
        // Con quien se comparte se lee como el resto; compartir y conceder es administrarlo
        if ("concesiones".equals(tramo) || "compartidos".equals(tramo) && !lee) {
            return Accion.ADMINISTRAR;
        }
        if (lee && "modelo".equals(ultimo)) {
            return DIBUJAR.equals(para) ? Accion.VER : Accion.EXPORTAR;
        }
        if (lee || DE_OPERACION.contains(tramo)) {
            return Accion.VER;
        }
        if (COMENTARIOS.equals(tramo)) {
            return Accion.COMENTAR;
        }
        if ("versiones".equals(tramo)) {
            return Accion.PUBLICAR;
        }
        // PR 62: armar un manual es exportar el proceso
        if ("manuales".equals(tramo)) {
            return Accion.EXPORTAR;
        }
        return "revision".equals(tramo) ? Accion.REVISAR : Accion.MODELAR;
    }
}
