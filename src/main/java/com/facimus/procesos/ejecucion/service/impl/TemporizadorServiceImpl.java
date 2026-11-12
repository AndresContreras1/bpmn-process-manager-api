package com.facimus.procesos.ejecucion.service.impl;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.security.ContextoDeTienda.Alcance;
import com.facimus.procesos.ejecucion.dto.response.TemporizadorResponse;
import com.facimus.procesos.ejecucion.mapper.CasoMapper;
import com.facimus.procesos.ejecucion.model.ClaseTemporizador;
import com.facimus.procesos.ejecucion.model.EstadoTemporizador;
import com.facimus.procesos.ejecucion.model.Temporizador;
import com.facimus.procesos.ejecucion.repository.CasoRepository;
import com.facimus.procesos.ejecucion.repository.TemporizadorRepository;
import com.facimus.procesos.ejecucion.repository.TemporizadorVencido;
import com.facimus.procesos.ejecucion.service.TemporizadorService;
import com.facimus.procesos.gestion.model.VersionProceso;
import com.facimus.procesos.gestion.service.VersionService;

import lombok.RequiredArgsConstructor;

/**
 * PR 68: los temporizadores, de cara al planificador, al tick y a la API. Disparar no abre transaccion: cada
 * temporizador salta en la suya (D3), por lo mismo que el tick entrega cada mensaje en la suya, y si uno se tuerce
 * queda anotado y los demas siguen.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TemporizadorServiceImpl implements TemporizadorService {

    /** Cuantos vencidos toma una pasada del planificador; lo que quede lo toma la siguiente. */
    static final int LOTE = 100;

    private static final Logger log = LoggerFactory.getLogger(TemporizadorServiceImpl.class);

    private final TemporizadorRepository temporizadorRepository;
    private final CasoRepository casoRepository;
    private final VersionService versionService;
    private final GrafosDeVersion grafos;
    private final Temporizadores temporizadores;
    private final DisparoDeTemporizadores disparo;
    private final CasoMapper casoMapper;
    private final Clock reloj;

    /**
     * Los vencidos se buscan entre todas las tiendas, y cada uno se dispara con el alcance de la suya (PR 51): lo que
     * el motor escriba al moverlo solo puede ser de esa tienda.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int dispararVencidos() {
        List<TemporizadorVencido> vencidos = temporizadorRepository.vencidosEnProduccion(
                Temporizadores.utc(reloj.instant()), PageRequest.of(0, LOTE));
        int disparados = 0;
        for (TemporizadorVencido vencido : vencidos) {
            Alcance anterior = ContextoDeTienda.fijar(Alcance.deLaTienda(vencido.empresaId()));
            try {
                if (intentar(vencido, () -> disparo.enProduccion(vencido.empresaId(), vencido.id()))) {
                    disparados++;
                }
            } finally {
                ContextoDeTienda.restaurar(anterior);
            }
        }
        return disparados;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int dispararEnTick(Long empresaId, int tick) {
        int disparados = 0;
        for (TemporizadorVencido vencido : temporizadorRepository.vencidosEnPrueba(empresaId, tick)) {
            if (intentar(vencido, () -> disparo.enPrueba(empresaId, vencido.id(), tick))) {
                disparados++;
            }
        }
        return disparados;
    }

    @Override
    @Transactional
    public void sincronizarInicios(Long empresaId, Long procesoId) {
        Optional<VersionProceso> vigente = versionService.vigente(empresaId, procesoId);
        Long vigenteId = vigente.map(VersionProceso::getId).orElse(null);
        Set<Long> yaArmados = new HashSet<>();
        for (Temporizador armado : iniciosArmados(empresaId, procesoId)) {
            if (armado.getVersionProcesoId().equals(vigenteId)) {
                yaArmados.add(armado.getNodoId());
            } else {
                cancelar(armado);
            }
        }
        if (vigente.isEmpty()) {
            return;
        }
        GrafoDeVersion grafo = grafos.del(empresaId, vigente.orElseThrow());
        if (!grafo.ejecutable()) {
            return;
        }
        for (TemporizadorDeLaVersion inicio : grafo.iniciosTemporizados()) {
            if (!yaArmados.contains(inicio.nodoId())) {
                temporizadores.armarInicio(vigente.orElseThrow(), inicio);
            }
        }
    }

    @Override
    @Transactional
    public void cancelarInicios(Long empresaId, Long procesoId) {
        iniciosArmados(empresaId, procesoId).forEach(this::cancelar);
    }

    @Override
    public List<TemporizadorResponse> delCaso(Long empresaId, Long casoId) {
        casoRepository.findByIdAndEmpresaId(casoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Caso no encontrado."));
        return casoMapper.toTemporizadores(
                temporizadorRepository.findAllByCasoIdAndEmpresaIdOrderByIdAsc(casoId, empresaId));
    }

    /** Pasa por la version vigente a proposito: un proceso de otra tienda, o eliminado, responde 404. */
    @Override
    public List<TemporizadorResponse> delProceso(Long empresaId, Long procesoId) {
        versionService.vigente(empresaId, procesoId);
        return casoMapper.toTemporizadores(temporizadorRepository
                .findAllByProcesoIdAndEmpresaIdAndClaseOrderByIdDesc(procesoId, empresaId, ClaseTemporizador.INICIO));
    }

    private List<Temporizador> iniciosArmados(Long empresaId, Long procesoId) {
        return temporizadorRepository.findAllByProcesoIdAndEmpresaIdAndClaseAndEstadoOrderByIdAsc(procesoId,
                empresaId, ClaseTemporizador.INICIO, EstadoTemporizador.PROGRAMADO);
    }

    private void cancelar(Temporizador temporizador) {
        temporizador.setEstado(EstadoTemporizador.CANCELADO);
        temporizadorRepository.save(temporizador);
    }

    /** Un salto que falla no frena a los demas: queda anotado en el temporizador, que lo vuelve a intentar despues. */
    private boolean intentar(TemporizadorVencido vencido, BooleanSupplier disparar) {
        try {
            return disparar.getAsBoolean();
        } catch (RuntimeException fallo) {
            log.warn("El temporizador {} no pudo saltar", vencido.id(), fallo);
            disparo.anotarFallo(vencido.empresaId(), vencido.id(),
                    fallo.getClass().getSimpleName() + ": " + fallo.getMessage());
            return false;
        }
    }
}
