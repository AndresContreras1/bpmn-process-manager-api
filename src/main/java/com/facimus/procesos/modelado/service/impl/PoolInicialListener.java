package com.facimus.procesos.modelado.service.impl;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.event.ProcesoCreado;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.repository.PoolRepository;

import lombok.RequiredArgsConstructor;

/**
 * Todo proceso nace con el pool de su empresa, el primer participante. Corre de forma sincrona dentro de la
 * transaccion que crea el proceso: si falla, el proceso tampoco se guarda.
 */
@Component
@RequiredArgsConstructor
public class PoolInicialListener {

    private final ProcesoRepository procesoRepository;
    private final PoolRepository poolRepository;

    @EventListener
    public void crearPoolDeLaEmpresa(ProcesoCreado evento) {
        Proceso proceso = procesoRepository.findByIdAndEmpresaId(evento.procesoId(), evento.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
        poolRepository.save(Pool.builder()
                .empresa(proceso.getEmpresa())
                .proceso(proceso)
                .nombre(proceso.getEmpresa().getNombre())
                .tipoParticipante(TipoParticipante.EMPRESA)
                .orden(0)
                .build());
    }
}
