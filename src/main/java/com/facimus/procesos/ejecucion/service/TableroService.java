package com.facimus.procesos.ejecucion.service;

import com.facimus.procesos.ejecucion.dto.response.TableroResponse;
import com.facimus.procesos.gestion.service.Actor;

/** Como va la operacion de una tienda: de un proceso o de todos. */
public interface TableroService {

    /**
     * El tablero de un proceso, o de la tienda entera si no se dice cual. Sale de la base en un numero fijo de
     * consultas agrupadas, crezcan los pedidos lo que crezcan. PR 71: un proceso de otra tienda, o que no existe, es un
     * 404; y el cumplimiento de los plazos solo cuenta los procesos que quien pregunta puede ver.
     */
    TableroResponse de(Actor quien, Long procesoId);
}
