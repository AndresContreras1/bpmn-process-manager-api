package com.facimus.procesos.gestion.event;

/**
 * PR 63: se anonimizo a una persona. Sale en la misma transaccion y a quien la escucha en ese momento, sin pasar por el
 * outbox: lleva el nombre que tenia, para que lo que la nombraba la nombre ahora con su seudonimo, y ese nombre no
 * puede quedar guardado en ninguna parte.
 */
public record UsuarioAnonimizado(Long empresaId, Long usuarioId, String nombre, String seudonimo) {
}
