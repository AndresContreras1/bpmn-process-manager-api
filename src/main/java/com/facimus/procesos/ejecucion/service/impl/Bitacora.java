package com.facimus.procesos.ejecucion.service.impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.facimus.procesos.common.metricas.MetricasDeNegocio;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EventoCaso;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.repository.EventoCasoRepository;

import lombok.RequiredArgsConstructor;

/**
 * Lo que le paso a un caso, escrito en el momento en que paso. Solo inserta: la linea de tiempo de un caso es lo
 * que se lee cuando algo no salio como se esperaba, y una bitacora que se corrige no sirve para eso.
 *
 * <p>Cada linea tambien suma en el contador de su tipo: por aqui pasa todo lo que les pasa a los casos, sea cual sea
 * el camino por el que llego.
 */
@Component
@RequiredArgsConstructor
class Bitacora {

    /** Lo que cabe en la columna; un detalle mas largo se recorta antes de que la base lo rechace. */
    private static final int LARGO_DEL_DETALLE = 2000;

    private final EventoCasoRepository eventoCasoRepository;
    private final MetricasDeNegocio metricas;

    /**
     * El tick llega de fuera, del {@link Momento} de la operacion: todo lo que pasa en una misma transaccion pasa
     * en el mismo momento de la simulacion, y leer el reloj por cada linea seria preguntar lo mismo muchas veces.
     */
    void anotar(Caso caso, int tick, TipoEventoCaso tipo, String detalle, Long autorId) {
        eventoCasoRepository.save(EventoCaso.builder()
                .empresa(caso.getEmpresa())
                .caso(caso)
                .tick(tick)
                .fecha(LocalDateTime.now())
                .tipo(tipo)
                .detalle(recortar(detalle))
                .autorId(autorId)
                .build());
        metricas.eventoDeCaso(tipo);
    }

    /** Lo que el motor hizo por su cuenta: pasa en un tick, pero no lo hizo nadie. */
    void anotar(Caso caso, int tick, TipoEventoCaso tipo, String detalle) {
        anotar(caso, tick, tipo, detalle, null);
    }

    private static String recortar(String detalle) {
        return detalle.length() <= LARGO_DEL_DETALLE ? detalle : detalle.substring(0, LARGO_DEL_DETALLE - 1) + "…";
    }
}
