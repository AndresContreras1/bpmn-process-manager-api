package com.facimus.procesos.gestion.service;

import java.util.List;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.DominioResponse;

/**
 * PR 50: los dominios de correo de la tienda. Se prueban con un registro TXT en su DNS, y uno verificado deja unirse
 * con el rol por defecto a quien entra con Google o Microsoft desde el.
 */
public interface DominioService {

    List<DominioResponse> listar(Long empresaId);

    /** Lo deja sin verificar, con el registro TXT que tiene que publicar. Uno que la tienda ya tiene da 409. */
    DominioResponse agregar(Long empresaId, Long autorId, String dominio, RolAcceso rolPorDefecto);

    /**
     * Busca el registro TXT. Si no aparece lanza SolicitudInvalidaException; si otra tienda ya verifico el dominio,
     * ReglaNegocioException.
     */
    DominioResponse verificar(Long empresaId, Long autorId, Long dominioId);

    void quitar(Long empresaId, Long autorId, Long dominioId);
}
