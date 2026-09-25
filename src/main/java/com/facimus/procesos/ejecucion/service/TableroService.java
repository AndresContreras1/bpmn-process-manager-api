package com.facimus.procesos.ejecucion.service;

import com.facimus.procesos.ejecucion.dto.response.TableroResponse;

/** Como va la operacion de una tienda: de un proceso o de todos. */
public interface TableroService {

    /**
     * El tablero de un proceso, o de la tienda entera si no se dice cual. Sale de la base en un numero fijo de
     * consultas agrupadas, crezcan los pedidos lo que crezcan.
     */
    TableroResponse de(Long empresaId, Long procesoId);
}
