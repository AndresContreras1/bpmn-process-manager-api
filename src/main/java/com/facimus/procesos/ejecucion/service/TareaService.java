package com.facimus.procesos.ejecucion.service;

import java.util.Map;

import org.springframework.data.domain.Pageable;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.ejecucion.dto.response.CargaDeTrabajoResponse;
import com.facimus.procesos.ejecucion.dto.response.FormularioDeTareaResponse;
import com.facimus.procesos.ejecucion.dto.response.TareaResponse;
import com.facimus.procesos.ejecucion.model.Bandeja;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;

/**
 * La bandeja: las actividades de usuario que esperan a que alguien las complete. Una tarea nace en la bandeja del rol
 * de proceso de su lane, o en manos de quien diga su regla de asignacion (PR 70), y la completa solo quien la tiene.
 */
public interface TareaService {

    /**
     * PR 70: las tareas de una bandeja, de la mas vieja a la mas nueva, opcionalmente las de un rol, las de un proceso
     * o las que ya se completaron. MIAS son las que tiene quien pregunta; DE_MIS_ROLES, las de sus roles de proceso
     * (D13), y sin ningun rol, ninguna; SIN_ASIGNAR, las que no tiene nadie.
     */
    @SuppressWarnings("java:S107") // cada filtro de la bandeja, por su nombre
    PageResponse<TareaResponse> bandeja(Long empresaId, Long usuarioId, Bandeja bandeja, Long rolProcesoId,
            Long procesoId, EstadoActividadCaso estado, Pageable pagina);

    /** La de antes del PR 70: con {@code mias}, la de los roles de quien pregunta; sin el, la de la tienda. */
    @SuppressWarnings("java:S107") // cada filtro de la bandeja, por su nombre
    PageResponse<TareaResponse> bandeja(Long empresaId, Long usuarioId, boolean mias, Long rolProcesoId,
            Long procesoId, EstadoActividadCaso estado, Pageable pagina);

    TareaResponse obtener(Long empresaId, Long tareaId);

    /**
     * Completa la tarea y deja que el caso siga. Los datos entran a las variables del caso, asi que un gateway
     * posterior puede preguntar por ellos. Una tarea se completa una sola vez (R-49). PR 69: si la tarea tiene
     * formulario, los datos son sus respuestas y se validan contra el; sin formulario, solo en PRUEBA se aceptan.
     * PR 70: la completa quien la tiene; la que no tiene nadie la toma al completarla quien la podria tomar, y la que
     * tiene otra persona, solo un administrador y solo si la tienda lo deja, con su registro en la bitacora.
     */
    TareaResponse completar(Long empresaId, Long usuarioId, Long tareaId, Map<String, Object> datos);

    /**
     * PR 69: el formulario de la tarea, como lo congelo la version de su caso, con lo que se propone en cada campo y
     * las variables que sus condiciones leen.
     */
    FormularioDeTareaResponse formulario(Long empresaId, Long tareaId);

    /** PR 70: como esta repartido lo que espera, de un proceso o de la tienda: por persona y sin nadie. */
    CargaDeTrabajoResponse carga(Long empresaId, Long procesoId);
}
