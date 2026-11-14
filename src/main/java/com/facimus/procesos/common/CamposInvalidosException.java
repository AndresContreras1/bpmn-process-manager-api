package com.facimus.procesos.common;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * PR 69: lo que llego esta bien escrito como JSON, pero sus campos no cumplen reglas que solo el servicio conoce, como
 * el formulario de una tarea. Responde 400 con {@code errors}, el mismo mapa de campo a mensaje que dan los errores de
 * validacion de cualquier peticion, para que quien la mando lo muestre junto a cada campo.
 */
public class CamposInvalidosException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Map<String, String> errores;

    public CamposInvalidosException(String mensaje, Map<String, String> errores) {
        super(mensaje);
        this.errores = new TreeMap<>(errores);
    }

    /** Los errores, por la ruta del campo en el cuerpo de la peticion: {@code datos.total}. */
    public Map<String, String> errores() {
        return Collections.unmodifiableMap(errores);
    }

    /** Los mismos errores, con la ruta del campo que los contiene delante: {@code total} pasa a {@code datos.total}. */
    public static CamposInvalidosException en(String contenedor, String mensaje, Map<String, String> errores) {
        Map<String, String> conRuta = new TreeMap<>();
        errores.forEach((campo, error) -> conRuta.put(contenedor + "." + campo, error));
        return new CamposInvalidosException(mensaje, conRuta);
    }
}
