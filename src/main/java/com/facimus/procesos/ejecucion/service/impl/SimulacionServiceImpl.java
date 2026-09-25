package com.facimus.procesos.ejecucion.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.ejecucion.dto.response.PanelDeSimulacionResponse;
import com.facimus.procesos.ejecucion.model.EstadoMensajeSaliente;
import com.facimus.procesos.ejecucion.model.ResultadoCorrelacion;
import com.facimus.procesos.ejecucion.repository.MensajeEntranteRepository;
import com.facimus.procesos.ejecucion.repository.MensajeSalienteRepository;
import com.facimus.procesos.ejecucion.service.SimulacionService;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;

import lombok.RequiredArgsConstructor;

/**
 * D8: mover el reloj. Un tick no es tiempo real, es un paso: la tienda avanza tantos pasos y lo que con ese paso ya
 * tendria que haber llegado, llega. Asi una prueba, o una demo, dice cuando pasan las cosas.
 *
 * <p>El tick no abre transaccion. Cada mensaje se entrega en la suya, con su caso bloqueado (D3): veinte pedidos
 * se mueven uno detras de otro, y si uno se tuerce los demas no se quedan a medias.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SimulacionServiceImpl implements SimulacionService {

    /** Cuantos ticks se pueden mover de una vez. Mas que esto no es una demo, es una carga, y esa tiene su k6. */
    private static final int TICKS_MAXIMOS = 100;

    private final MensajeSalienteRepository mensajeSalienteRepository;
    private final MensajeEntranteRepository mensajeEntranteRepository;
    private final ConfiguracionTiendaService configuracionTiendaService;
    private final EntregaDeMensajes entrega;
    private final RelojDeLaTienda reloj;

    @Override
    public PanelDeSimulacionResponse panel(Long empresaId) {
        var configuracion = configuracionTiendaService.obtener(empresaId);
        return new PanelDeSimulacionResponse(configuracion.reloj(), configuracion.modoSimulacion(),
                mensajeSalienteRepository.countByEmpresaIdAndEstado(empresaId, EstadoMensajeSaliente.PENDIENTE),
                mensajeSalienteRepository.pendientesPorSocio(empresaId),
                mensajeEntranteRepository.countByEmpresaIdAndResultado(empresaId, ResultadoCorrelacion.EN_ESPERA));
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PanelDeSimulacionResponse tick(Long empresaId, int ticks) {
        if (ticks < 1 || ticks > TICKS_MAXIMOS) {
            throw new ReglaNegocioException("El reloj se mueve entre 1 y " + TICKS_MAXIMOS + " ticks por vez.");
        }
        int ahora = reloj.avanzar(empresaId, ticks);
        // Primero lo que ya habia llegado y nadie esperaba, porque llego antes: si ahora si lo esperan, le toca a
        // ese y no a la respuesta que venga en este mismo tick.
        for (Long entrante : entrega.enEspera(empresaId)) {
            entrega.reintentar(empresaId, entrante);
        }
        for (Long saliente : entrega.vencidos(empresaId, ahora)) {
            entrega.entregar(empresaId, saliente, ahora);
        }
        return panel(empresaId);
    }
}
