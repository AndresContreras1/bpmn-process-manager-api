package com.facimus.procesos.gestion.service;

import com.facimus.procesos.gestion.dto.response.ConfiguracionTiendaResponse;
import com.facimus.procesos.gestion.model.ModoSimulacion;
import com.facimus.procesos.gestion.model.PoliticaEstructura;

/** D16: lo que cada tienda decide sobre si misma, y la regla que de ahi sale (R-46). */
public interface ConfiguracionTiendaService {

    /** Crea la configuracion de una tienda recien registrada, con lo que trae de fabrica. */
    void crearPara(Long empresaId);

    ConfiguracionTiendaResponse obtener(Long empresaId);

    /** Un modo nulo deja el que tenia: el cuerpo de la edicion crecio y los clientes de antes no lo mandan. */
    ConfiguracionTiendaResponse editar(Long empresaId, Long usuarioId, PoliticaEstructura politica,
            ModoSimulacion modo, Long version);

    /** D8: en que tick va la simulacion de esta tienda. */
    int reloj(Long empresaId);

    /** Mueve el reloj de la tienda y devuelve el tick al que llego. */
    int avanzarReloj(Long empresaId, int ticks);

    /**
     * R-46: comprueba que ese usuario puede crear o editar pools y lanes en su tienda. Lo llama el modelado, que no
     * conoce ni la configuracion ni los roles de acceso.
     */
    void exigirPuedeEditarEstructura(Long empresaId, Long usuarioId);
}
