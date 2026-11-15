package com.facimus.procesos.ejecucion.service;

import com.facimus.procesos.ejecucion.dto.response.TareaResponse;

/**
 * PR 70: lo que se hace con una tarea mientras espera: tomarla, soltarla, pasarsela a otra persona o delegarla un
 * tiempo. Cada accion queda en la linea de tiempo del caso y en la bitacora de la tienda.
 */
public interface AsignacionDeTareaService {

    /**
     * La toma quien pertenece al rol de su lane, o un administrador, si no la tiene nadie. Dos que la reclaman a la
     * vez: la toma uno y el otro recibe un 409.
     */
    TareaResponse reclamar(Long empresaId, Long usuarioId, Long tareaId);

    /**
     * La suelta quien la tiene, o un administrador. Vuelve a la bandeja del rol; si quien la tiene es un delegado,
     * vuelve a quien se la delego.
     */
    TareaResponse soltar(Long empresaId, Long usuarioId, Long tareaId, String motivo);

    /**
     * Quien la tiene, o un administrador, se la pasa a otra persona que la pueda completar, con su motivo. Una
     * delegacion abierta se anula: la tarea ya no vuelve a quien la delego.
     */
    TareaResponse reasignar(Long empresaId, Long usuarioId, Long tareaId, Long aQuien, String motivo);

    /**
     * Quien la tiene se la pasa a otra persona por un tiempo, con su motivo: si el delegado la suelta, le vuelve. Una
     * tarea delegada no se vuelve a delegar.
     */
    TareaResponse delegar(Long empresaId, Long usuarioId, Long tareaId, Long aQuien, String motivo);
}
