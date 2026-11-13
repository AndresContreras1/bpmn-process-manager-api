package com.facimus.procesos.gestion.service.impl;

import java.util.List;

/**
 * PR 63: lo que la cola guarda del correo de unas notificaciones de una persona: sus ids, no sus textos. El correo se
 * escribe al mandarlo, con lo que haya entonces.
 *
 * @param resumen si es el resumen del dia; si no, el correo de una sola, en cuanto paso
 */
record PedidoDeAviso(Long usuarioId, List<Long> notificaciones, boolean resumen) {
}
