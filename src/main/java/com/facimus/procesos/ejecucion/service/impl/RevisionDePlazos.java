package com.facimus.procesos.ejecucion.service.impl;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.notificaciones.TipoDeNotificacion;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoPlazo;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.model.Vencimiento;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.repository.CasoRepository;

import lombok.RequiredArgsConstructor;

/**
 * PR 71: mover un plazo, cada uno en su transaccion. Se lee como esta, se calcula lo que le toca con el reloj de pared
 * (o con el tick, en prueba) y se escribe solo si sigue como estaba: dos instancias que lo encuentran a la vez lo
 * mueven una sola vez, y solo esa avisa. El aviso y la linea de tiempo van en la misma transaccion que el cambio.
 *
 * <p>Uno que salta de a tiempo a vencida (la API estuvo caida, o el plazo era tan corto que no hubo repaso en medio)
 * solo avisa que vencio: el aviso de que estaba por vencer ya no le sirve a nadie.
 */
@Component
@RequiredArgsConstructor
class RevisionDePlazos {

    private final ActividadCasoRepository actividadCasoRepository;
    private final CasoRepository casoRepository;
    private final Bitacora bitacora;
    private final AvisosDePlazo avisos;
    private final RelojDeLaTienda relojDeLaTienda;
    private final Clock reloj;

    /**
     * Mueve el plazo de una tarea que sigue esperando. Dice si cambio.
     *
     * @param tick el de la tienda, en prueba; nulo en produccion, donde se lee el de la tienda para la linea de tiempo
     */
    @Transactional
    public boolean deLaTarea(Long empresaId, Long tareaId, Integer tick) {
        Optional<ActividadCaso> leida = actividadCasoRepository.findByIdAndEmpresaId(tareaId, empresaId)
                .filter(tarea -> tarea.getEstado() == EstadoActividadCaso.EN_ESPERA)
                .filter(tarea -> tarea.getVencimiento() != null);
        if (leida.isEmpty()) {
            return false;
        }
        ActividadCaso tarea = leida.get();
        Long casoId = tarea.getCaso().getId();
        int elTick = tick != null ? tick : relojDeLaTienda.ahora(empresaId);
        Optional<EstadoPlazo> nuevo = cambio(tarea.getVencimiento(), elTick);
        if (nuevo.isEmpty() || actividadCasoRepository.moverPlazo(tareaId, empresaId,
                tarea.getVencimiento().getEstado().name(), nuevo.get().name()) == 0) {
            return false;
        }
        Caso caso = casoRepository.findByIdAndEmpresaId(casoId, empresaId).orElseThrow();
        boolean vencio = nuevo.get() == EstadoPlazo.VENCIDA;
        bitacora.anotar(caso, elTick, vencio ? TipoEventoCaso.PLAZO_VENCIDO : TipoEventoCaso.PLAZO_POR_VENCER,
                "\"" + tarea.getNodoNombre() + (vencio ? "\" paso su plazo." : "\" esta por vencer: entro en el ultimo "
                        + "cuarto de su plazo."));
        avisos.deLaTarea(caso, tarea, aviso(vencio));
        return true;
    }

    /** Mueve el plazo de un caso que sigue abierto o en error. Dice si cambio. */
    @Transactional
    public boolean delCaso(Long empresaId, Long casoId, Integer tick) {
        Optional<Caso> leido = casoRepository.findByIdAndEmpresaId(casoId, empresaId)
                .filter(caso -> !caso.getEstado().estaCerrado())
                .filter(caso -> caso.getVencimiento() != null);
        if (leido.isEmpty()) {
            return false;
        }
        int elTick = tick != null ? tick : relojDeLaTienda.ahora(empresaId);
        Optional<EstadoPlazo> nuevo = cambio(leido.get().getVencimiento(), elTick);
        if (nuevo.isEmpty() || casoRepository.moverPlazo(casoId, empresaId,
                leido.get().getVencimiento().getEstado().name(), nuevo.get().name()) == 0) {
            return false;
        }
        Caso caso = casoRepository.findByIdAndEmpresaId(casoId, empresaId).orElseThrow();
        boolean vencio = nuevo.get() == EstadoPlazo.VENCIDA;
        bitacora.anotar(caso, elTick, vencio ? TipoEventoCaso.PLAZO_VENCIDO : TipoEventoCaso.PLAZO_POR_VENCER,
                vencio ? "El caso paso su plazo." : "El caso esta por vencer: entro en el ultimo cuarto de su plazo.");
        avisos.delCaso(caso, aviso(vencio));
        return true;
    }

    /** El estado que le toca ahora, si no es el que ya tiene. */
    private Optional<EstadoPlazo> cambio(Vencimiento vencimiento, int tick) {
        EstadoPlazo toca = vencimiento.estadoEn(reloj.instant(), tick);
        return toca == vencimiento.getEstado() ? Optional.empty() : Optional.of(toca);
    }

    private static TipoDeNotificacion aviso(boolean vencio) {
        return vencio ? TipoDeNotificacion.PLAZO_VENCIDO : TipoDeNotificacion.PLAZO_POR_VENCER;
    }
}
