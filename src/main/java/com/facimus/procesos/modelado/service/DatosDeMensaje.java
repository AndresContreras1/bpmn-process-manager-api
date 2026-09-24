package com.facimus.procesos.modelado.service;

import java.util.List;

import com.facimus.procesos.modelado.model.AccionSiFalla;
import com.facimus.procesos.modelado.model.CampoDeMensaje;
import com.facimus.procesos.modelado.model.TipoDestino;

/**
 * Todo lo que define un mensaje entre participantes. Va junto porque las reglas se miran entre si: el nodo anclado
 * depende del pool de su lado, y la actividad que maneja el error depende de que el envio pueda fallar.
 *
 * @param nombre           nombre del mensaje, unico dentro del proceso a ojos de quien lo lee
 * @param contenido        que lleva, contado en palabras
 * @param poolOrigenId     participante que lo manda
 * @param poolDestinoId    participante que lo recibe
 * @param nodoOrigenId     nodo desde el que sale, si el pool de origen modela su flujo
 * @param nodoDestinoId    nodo que lo espera, si el pool de destino modela su flujo
 * @param tipoDestino      por donde sale: correo, servicio web o cola
 * @param siFalla          que hace el proceso si el envio no llega
 * @param nodoManejoErrorId actividad que atiende el fallo, obligatoria con MANEJAR_ERROR
 * @param origenExterno    el mensaje llega de fuera del diagrama y no hay throw que lo mande
 * @param campos           los datos que viajan dentro
 * @param usoDeLosDatos    para que se usan esos datos
 * @param variable         nombre con el que el cuerpo entra a las variables del caso
 * @param respuestaEsperadaId mensaje que contesta a este
 */
public record DatosDeMensaje(
        String nombre,
        String contenido,
        Long poolOrigenId,
        Long poolDestinoId,
        Long nodoOrigenId,
        Long nodoDestinoId,
        TipoDestino tipoDestino,
        AccionSiFalla siFalla,
        Long nodoManejoErrorId,
        boolean origenExterno,
        List<CampoDeMensaje> campos,
        String usoDeLosDatos,
        String variable,
        Long respuestaEsperadaId) {

    /** Los datos minimos de un mensaje, como lo creaba el contrato anterior. */
    public static DatosDeMensaje basico(String nombre, String contenido, Long poolOrigenId, Long poolDestinoId) {
        return new DatosDeMensaje(nombre, contenido, poolOrigenId, poolDestinoId, null, null, null, null, null,
                false, List.of(), null, null, null);
    }
}
