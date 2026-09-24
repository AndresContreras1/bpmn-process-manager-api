package com.facimus.procesos.gestion.service;

import com.facimus.procesos.gestion.dto.response.ConfiguracionTiendaResponse;
import com.facimus.procesos.gestion.model.PoliticaEstructura;

/** D16: lo que cada tienda decide sobre si misma, y la regla que de ahi sale (R-46). */
public interface ConfiguracionTiendaService {

    /** Crea la configuracion de una tienda recien registrada, con lo que trae de fabrica. */
    void crearPara(Long empresaId);

    ConfiguracionTiendaResponse obtener(Long empresaId);

    ConfiguracionTiendaResponse editar(Long empresaId, Long usuarioId, PoliticaEstructura politica, Long version);

    /**
     * R-46: comprueba que ese usuario puede crear o editar pools y lanes en su tienda. Lo llama el modelado, que no
     * conoce ni la configuracion ni los roles de acceso.
     */
    void exigirPuedeEditarEstructura(Long empresaId, Long usuarioId);
}
