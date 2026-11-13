package com.facimus.procesos.ejecucion.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongFunction;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.security.ContextoDeTienda.Alcance;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.repository.CasoRepository;
import com.facimus.procesos.ejecucion.repository.PlazoQueCambia;
import com.facimus.procesos.ejecucion.service.PlazoService;

import lombok.RequiredArgsConstructor;

/**
 * PR 71: el repaso de los plazos, de cara al planificador y al tick. No abre transaccion: cada plazo se mueve en la
 * suya, como cada temporizador salta en la suya, y si uno se tuerce queda en el registro y los demas siguen. Lo que no
 * se pudo mover sigue pendiente, y lo toma la pasada siguiente, aunque la API haya estado caida entre una y otra.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlazoServiceImpl implements PlazoService {

    /** Cuantas tareas, o cuantos casos, se leen de una vez; una pasada sigue leyendo mientras salgan lotes llenos. */
    static final int LOTE = 100;

    /**
     * Lo que puede durar una pasada antes de dejarle el resto a la siguiente: menos que el candado del planificador
     * (diez minutos, D34), para que otra instancia no empiece mientras esta sigue.
     */
    static final Duration PRESUPUESTO = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(PlazoServiceImpl.class);

    private final ActividadCasoRepository actividadCasoRepository;
    private final CasoRepository casoRepository;
    private final RevisionDePlazos revision;
    private final Clock reloj;

    /**
     * Se buscan entre todas las tiendas, y cada uno se mueve con el alcance de la suya (PR 51): lo que escriba, el
     * aviso incluido, solo puede ser de esa tienda. Una pasada recorre cada lote, por id, hasta uno que no sale lleno o
     * hasta gastar su presupuesto: despues de una caida, o cuando muchos vencen juntos, no se queda en los cien
     * primeros, y uno que falla siempre no tapa a los de detras.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int revisarVencidos() {
        Instant empezo = reloj.instant();
        var ahora = Temporizadores.utc(empezo);
        Instant hasta = empezo.plus(PRESUPUESTO);
        int cambiaron = recorrer(despuesDe -> actividadCasoRepository.plazosQueCambianEnProduccion(ahora, despuesDe,
                PageRequest.of(0, LOTE)), tarea -> revision.deLaTarea(tarea.empresaId(), tarea.id(), null), hasta);
        return cambiaron + recorrer(despuesDe -> casoRepository.plazosQueCambianEnProduccion(ahora, despuesDe,
                PageRequest.of(0, LOTE)), caso -> revision.delCaso(caso.empresaId(), caso.id(), null), hasta);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int revisarEnTick(Long empresaId, int tick) {
        int cambiaron = 0;
        List<PlazoQueCambia> tareas = actividadCasoRepository.plazosQueCambianEnPrueba(empresaId, tick);
        for (PlazoQueCambia tarea : tareas) {
            cambiaron += intentar(tarea, () -> revision.deLaTarea(empresaId, tarea.id(), tick));
        }
        for (PlazoQueCambia caso : casoRepository.plazosQueCambianEnPrueba(empresaId, tick)) {
            cambiaron += intentar(caso, () -> revision.delCaso(empresaId, caso.id(), tick));
        }
        return cambiaron;
    }

    /** Lote tras lote, desde el ultimo id del anterior, hasta uno corto o hasta la hora limite. */
    private int recorrer(LongFunction<List<PlazoQueCambia>> lote, Predicate<PlazoQueCambia> mover, Instant hasta) {
        int cambiaron = 0;
        long despuesDe = 0;
        List<PlazoQueCambia> leidos;
        do {
            leidos = lote.apply(despuesDe);
            for (PlazoQueCambia plazo : leidos) {
                cambiaron += enSuTienda(plazo, () -> mover.test(plazo));
                despuesDe = plazo.id();
            }
        } while (leidos.size() == LOTE && reloj.instant().isBefore(hasta));
        return cambiaron;
    }

    private static int enSuTienda(PlazoQueCambia plazo, BooleanSupplier mover) {
        Alcance anterior = ContextoDeTienda.fijar(Alcance.deLaTienda(plazo.empresaId()));
        try {
            return intentar(plazo, mover);
        } finally {
            ContextoDeTienda.restaurar(anterior);
        }
    }

    /** Uno que falla no frena a los demas; sigue pendiente y lo toma la pasada siguiente. */
    private static int intentar(PlazoQueCambia plazo, BooleanSupplier mover) {
        try {
            return mover.getAsBoolean() ? 1 : 0;
        } catch (RuntimeException fallo) {
            if (log.isWarnEnabled()) {
                log.warn("El plazo de {} no se pudo mover", plazo.id(), fallo);
            }
            return 0;
        }
    }
}
