package com.facimus.procesos.ejecucion.service.impl;

import java.time.Clock;
import java.util.List;
import java.util.function.BooleanSupplier;

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

    /** Cuantas tareas, y cuantos casos, mueve una pasada del planificador; lo que quede lo toma la siguiente. */
    static final int LOTE = 100;

    private static final Logger log = LoggerFactory.getLogger(PlazoServiceImpl.class);

    private final ActividadCasoRepository actividadCasoRepository;
    private final CasoRepository casoRepository;
    private final RevisionDePlazos revision;
    private final Clock reloj;

    /**
     * Se buscan entre todas las tiendas, y cada uno se mueve con el alcance de la suya (PR 51): lo que escriba, el
     * aviso incluido, solo puede ser de esa tienda.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int revisarVencidos() {
        var ahora = Temporizadores.utc(reloj.instant());
        int cambiaron = 0;
        for (PlazoQueCambia tarea : actividadCasoRepository.plazosQueCambianEnProduccion(ahora,
                PageRequest.of(0, LOTE))) {
            cambiaron += enSuTienda(tarea, () -> revision.deLaTarea(tarea.empresaId(), tarea.id(), null));
        }
        for (PlazoQueCambia caso : casoRepository.plazosQueCambianEnProduccion(ahora, PageRequest.of(0, LOTE))) {
            cambiaron += enSuTienda(caso, () -> revision.delCaso(caso.empresaId(), caso.id(), null));
        }
        return cambiaron;
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
