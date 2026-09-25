package com.facimus.procesos.ejecucion.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.EstadoMensajeSaliente;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.repository.CasoRepository;
import com.facimus.procesos.ejecucion.repository.MensajeEntranteRepository;
import com.facimus.procesos.ejecucion.repository.MensajeSalienteRepository;
import com.facimus.procesos.ejecucion.service.MedidoresDeLaOperacion;

import lombok.RequiredArgsConstructor;

/**
 * Cuatro conteos, uno por medidor. Se leen cuando alguien pide las metricas y no en un bucle: un medidor que se
 * calculara solo cada pocos segundos seria trabajo constante para una cifra que casi nadie mira.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedidoresDeLaOperacionImpl implements MedidoresDeLaOperacion {

    private final CasoRepository casoRepository;
    private final ActividadCasoRepository actividadCasoRepository;
    private final MensajeSalienteRepository mensajeSalienteRepository;
    private final MensajeEntranteRepository mensajeEntranteRepository;

    @Override
    public long casosAbiertos() {
        return casoRepository.countByEstado(EstadoCaso.ABIERTO);
    }

    @Override
    public long tareasPendientes() {
        return actividadCasoRepository.contarTareasEnEspera();
    }

    @Override
    public long salientesPendientes() {
        return mensajeSalienteRepository.countByEstado(EstadoMensajeSaliente.PENDIENTE);
    }

    @Override
    public long entrantesPendientes() {
        return mensajeEntranteRepository.contarPendientes();
    }
}
