package com.facimus.procesos.gestion.service;

import com.facimus.procesos.gestion.dto.response.ReservaIdempotencia;

/** Idempotency-Key: un POST reintentado con la misma clave recibe la misma respuesta en vez de repetirse. */
public interface IdempotenciaService {

    /**
     * Reserva la clave del usuario para esta peticion, identificada por su huella (metodo, ruta y cuerpo), o dice que
     * ya se uso: con la misma peticion, que ya respondio o sigue en curso; con otra, que la clave no sirve.
     */
    ReservaIdempotencia reservar(Long empresaId, Long usuarioId, String clave, String huella);

    /** Guarda lo que respondio la peticion de una reserva, para devolverlo en los reintentos. */
    void guardar(Long empresaId, Long reservaId, int estado, String cuerpo, String ubicacion);

    /** Suelta la reserva de una peticion que fallo: el cliente puede reintentarla con la misma clave. */
    void liberar(Long empresaId, Long reservaId);
}
