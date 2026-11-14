package com.facimus.procesos.ejecucion.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.ModoCaso;
import com.facimus.procesos.ejecucion.model.Vencimiento;
import com.facimus.procesos.gestion.service.CalendarioLaboral;
import com.facimus.procesos.gestion.service.CalendarioService;
import com.facimus.procesos.modelado.dto.response.PlazoResponse;

import lombok.RequiredArgsConstructor;

/**
 * PR 71: armar y cerrar los plazos. Lo llama el motor dentro de la transaccion del caso, con el caso bloqueado (D3):
 * una tarea que se queda esperando arma el suyo, un caso que se abre por un inicio con plazo arma el del caso, y al
 * completar la tarea o terminar el caso el plazo se queda en lo que llego a ser. Lo que cambia mientras espera lo
 * mueve el repaso de los plazos, que es tambien quien avisa.
 *
 * <p>Armar no escribe una linea de tiempo propia: devuelve la frase con la que la linea que crea la tarea, o la que
 * abre el caso, dice cuando vence.
 */
@Component
@RequiredArgsConstructor
class Plazos {

    private static final DateTimeFormatter COMO_SE_LEE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String SIN_TIEMPO_HABIL = " Su plazo no se arma: el calendario de la tienda no tiene tiempo "
            + "habil para contarlo.";

    private final CalendarioService calendarioService;
    private final Clock reloj;

    /**
     * El plazo de la tarea que se queda esperando, si su actividad tiene uno en la version. Se guarda con la tarea.
     *
     * @return lo que se le suma a la linea de la tarea creada: cuando vence, por que no tiene plazo, o nada
     */
    String armarDeLaTarea(Caso caso, GrafoDeVersion grafo, ActividadCaso tarea, Momento momento) {
        return grafo.plazoDe(tarea.getNodoId()).map(plazo -> vencimiento(caso, plazo, momento).map(vence -> {
            tarea.setVencimiento(vence);
            return " Vence " + cuando(caso, vence) + ".";
        }).orElse(SIN_TIEMPO_HABIL)).orElse("");
    }

    /**
     * El plazo del caso entero, si el inicio por el que se abre tiene uno en la version.
     *
     * @return lo que se le suma a la linea del caso abierto, como en una tarea
     */
    String armarDelCaso(Caso caso, GrafoDeVersion grafo, NodoDeLaVersion inicio, Momento momento) {
        return grafo.plazoDe(inicio.id()).map(plazo -> vencimiento(caso, plazo, momento).map(vence -> {
            caso.setVencimiento(vence);
            return " Vence " + cuando(caso, vence) + ".";
        }).orElse(SIN_TIEMPO_HABIL)).orElse("");
    }

    /**
     * La tarea se completo: su plazo se queda como iba en ese momento. Si ya habia vencido se completo tarde, aunque el
     * repaso no hubiera pasado todavia a marcarla; no se avisa, porque ya no hay nada que hacer.
     */
    void alCompletar(ActividadCaso tarea, Momento momento) {
        cerrar(tarea.getVencimiento(), momento);
    }

    /** El caso termino: lo mismo con el plazo del caso. Uno cancelado o fallido no se mide. */
    void alTerminar(Caso caso, Momento momento) {
        cerrar(caso.getVencimiento(), momento);
    }

    private void cerrar(Vencimiento vencimiento, Momento momento) {
        if (vencimiento != null) {
            vencimiento.setEstado(vencimiento.estadoEn(reloj.instant(), momento.tick()));
        }
    }

    /**
     * En produccion, con el reloj de pared y el calendario de la tienda; en prueba, en ticks. Una cuenta que se sale de
     * lo que el calendario puede contar (uno que nunca deja trabajar) no arma plazo, en vez de tumbar el caso.
     */
    private Optional<Vencimiento> vencimiento(Caso caso, PlazoResponse plazo, Momento momento) {
        CalendarioLaboral calendario = calendarioService.laboral(caso.getEmpresa().getId());
        try {
            return Optional.of(caso.getModo() == ModoCaso.PRUEBA
                    ? CuandoVenceElPlazo.enPrueba(plazo, momento.tick(), calendario)
                    : CuandoVenceElPlazo.enProduccion(plazo, reloj.instant(), calendario));
        } catch (IllegalStateException | ArithmeticException _) {
            return Optional.empty();
        }
    }

    private String cuando(Caso caso, Vencimiento vencimiento) {
        if (vencimiento.enTicks()) {
            return "en el tick " + vencimiento.getVenceTick();
        }
        ZoneId zona = calendarioService.laboral(caso.getEmpresa().getId()).zona();
        Instant vence = vencimiento.getVenceEn().toInstant(ZoneOffset.UTC);
        return "el " + COMO_SE_LEE.format(vence.atZone(zona)) + " (" + zona.getId() + ")";
    }
}
