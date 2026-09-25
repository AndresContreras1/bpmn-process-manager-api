package com.facimus.procesos.ejecucion.service;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.ejecucion.dto.response.CasoDetalleResponse;
import com.facimus.procesos.ejecucion.dto.response.CasoResponse;
import com.facimus.procesos.ejecucion.dto.response.EventoCasoResponse;
import com.facimus.procesos.ejecucion.model.EstadoCaso;

/**
 * Los casos: una ejecucion de una version publicada. Abrir, mirar y cerrar; quien los hace avanzar es el motor, y
 * toda operacion que los toca empieza bloqueando su fila (D3).
 */
public interface CasoService {

    /**
     * Abre un caso sobre la version vigente del proceso y lo avanza hasta donde llegue: normalmente, hasta la
     * primera tarea de la bandeja.
     */
    CasoResponse abrir(Long empresaId, Long usuarioId, Long procesoId, String referencia,
            Map<String, Object> variables);

    PageResponse<CasoResponse> listar(Long empresaId, Long procesoId, EstadoCaso estado, String referencia,
            Pageable pagina);

    /** El caso con por donde ha pasado y con que variables decide. */
    CasoDetalleResponse obtener(Long empresaId, Long casoId);

    /** La linea de tiempo del caso, en el orden en que pasaron las cosas. */
    List<EventoCasoResponse> eventos(Long empresaId, Long casoId);

    /** Cierra el caso antes de tiempo: sus tokens vivos se apagan y no vuelve a moverse (R-51). */
    CasoResponse cancelar(Long empresaId, Long usuarioId, Long casoId);

    /** Reemplaza las variables de un caso, normalmente para corregir lo que dejo a un gateway sin camino. */
    CasoResponse corregirVariables(Long empresaId, Long casoId, Map<String, Object> variables, Long version);

    /** Vuelve a evaluar lo que dejo el caso en ERROR, con las variables que tenga ahora. */
    CasoResponse reintentar(Long empresaId, Long usuarioId, Long casoId);
}
