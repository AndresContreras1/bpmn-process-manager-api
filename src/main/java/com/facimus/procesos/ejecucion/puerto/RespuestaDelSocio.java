package com.facimus.procesos.ejecucion.puerto;

import java.util.List;

/**
 * Lo que un socio simulado hace con un mensaje: darlo por entregado o no, y lo que contesta. Un socio puede
 * contestar nada (una notificacion no se responde), una cosa o varias.
 *
 * @param entregado  si el mensaje llego a su destino
 * @param error      por que no llego, cuando no llego
 * @param respuestas lo que el socio manda de vuelta
 */
public record RespuestaDelSocio(boolean entregado, String error, List<RespuestaEntrante> respuestas) {

    /** Llego y no contesta nada, como una notificacion por correo. */
    public static RespuestaDelSocio llego() {
        return new RespuestaDelSocio(true, null, List.of());
    }

    /** Llego y contesta. */
    public static RespuestaDelSocio llegoYContesta(List<RespuestaEntrante> respuestas) {
        return new RespuestaDelSocio(true, null, respuestas);
    }

    /** No llego, y a partir de aqui manda el siFalla del mensaje. */
    public static RespuestaDelSocio noLlego(String error) {
        return new RespuestaDelSocio(false, error, List.of());
    }
}
