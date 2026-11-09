package com.facimus.procesos.gestion.service;

import java.time.LocalDateTime;
import java.util.List;

import com.facimus.procesos.common.auditoria.SucesoAuditable;
import com.facimus.procesos.gestion.dto.response.PaginaDeBitacoraResponse;
import com.facimus.procesos.gestion.dto.response.RegistroDeBitacoraResponse;
import com.facimus.procesos.gestion.dto.response.VerificacionDeBitacoraResponse;

/**
 * PR 31: la bitacora encadenada de cada tienda. Solo se le agrega; nada la cambia ni la borra.
 */
public interface BitacoraService {

    /** Lo que pide la busqueda: todo es opcional. */
    record Filtro(String accion, Long actorId, LocalDateTime desde, LocalDateTime hasta) {
    }

    /** Lo agrega al final de la cadena de su tienda. Dos a la vez de la misma tienda quedan uno detras del otro. */
    void anotar(SucesoAuditable suceso);

    /** De lo mas nuevo a lo mas viejo, antes de esa secuencia si se da. */
    PaginaDeBitacoraResponse buscar(Long empresaId, Filtro filtro, Long antes, int tamano);

    /** Todo lo que el filtro encuentra, hasta el tope, de lo mas viejo a lo mas nuevo: para exportarlo. */
    List<RegistroDeBitacoraResponse> exportar(Long empresaId, Filtro filtro, int tope);

    /** Recalcula la cadena entera de la tienda y dice donde se rompe, si se rompe. */
    VerificacionDeBitacoraResponse verificar(Long empresaId);
}
