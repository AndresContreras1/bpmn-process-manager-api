package com.facimus.procesos.ejecucion.service;

import java.util.Map;

import org.springframework.data.domain.Pageable;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.ejecucion.dto.response.TareaResponse;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;

/**
 * La bandeja: las actividades de usuario que esperan a que alguien las complete. Una tarea nace con el rol de
 * proceso de su lane y la completa cualquier administrador o editor de la tienda (D13).
 */
public interface TareaService {

    /**
     * Las tareas de la tienda, opcionalmente las de un rol, las de un proceso o las que ya se completaron. Con
     * {@code mias}, solo las de los roles de proceso de quien pregunta (D13); sin ningun rol, ninguna.
     */
    PageResponse<TareaResponse> bandeja(Long empresaId, Long usuarioId, boolean mias, Long rolProcesoId,
            Long procesoId, EstadoActividadCaso estado, Pageable pagina);

    TareaResponse obtener(Long empresaId, Long tareaId);

    /**
     * Completa la tarea y deja que el caso siga. Los datos entran a las variables del caso, asi que un gateway
     * posterior puede preguntar por ellos. Una tarea se completa una sola vez (R-49).
     */
    TareaResponse completar(Long empresaId, Long usuarioId, Long tareaId, Map<String, Object> datos);

    /** Reserva la tarea para alguien de la tienda, o la libera si no llega ningun usuario. */
    TareaResponse asignar(Long empresaId, Long tareaId, Long usuarioId);
}
