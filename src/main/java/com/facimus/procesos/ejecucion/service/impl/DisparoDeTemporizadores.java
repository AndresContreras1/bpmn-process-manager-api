package com.facimus.procesos.ejecucion.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.ClaseTemporizador;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.EstadoTemporizador;
import com.facimus.procesos.ejecucion.model.ModoCaso;
import com.facimus.procesos.ejecucion.model.Temporizador;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.repository.CasoRepository;
import com.facimus.procesos.ejecucion.repository.TemporizadorRepository;
import com.facimus.procesos.gestion.model.VersionProceso;
import com.facimus.procesos.gestion.service.VersionService;

import lombok.RequiredArgsConstructor;

/**
 * PR 68: disparar un temporizador, cada uno en su transaccion. Primero se bloquea su caso, como en cualquier operacion
 * que lo mueve (D3), y despues se toma la fila con {@code FOR UPDATE SKIP LOCKED} pidiendo que siga armada y vencida.
 * Asi salta una sola vez aunque dos instancias lo encuentren a la vez: la segunda espera el caso, y cuando lo tiene el
 * temporizador ya no esta armado; uno de inicio, que no tiene caso, lo salta quien lo toma primero.
 *
 * <p>Si la transaccion se cae a medias, el temporizador sigue armado y vencido: lo toma la pasada siguiente, aunque la
 * API haya estado caida entre una y otra.
 */
@Component
@RequiredArgsConstructor
class DisparoDeTemporizadores {

    /** Al tercer fallo un temporizador deja de intentarlo y queda FALLIDO, con su ultimo error. */
    static final int INTENTOS = 3;
    private static final int LARGO_DEL_ERROR = 500;

    private final TemporizadorRepository temporizadorRepository;
    private final CasoRepository casoRepository;
    private final ActividadCasoRepository actividadCasoRepository;
    private final VersionService versionService;
    private final GrafosDeVersion grafos;
    private final MotorDeProcesos motor;
    private final Temporizadores temporizadores;
    private final RelojDeLaTienda relojDeLaTienda;
    private final Clock reloj;

    /** Dispara uno de produccion si sigue armado y ya vencio con el reloj de pared. Dice si salto. */
    @Transactional
    public boolean enProduccion(Long empresaId, Long temporizadorId) {
        Instant ahora = reloj.instant();
        Optional<Caso> caso = bloquearSuCaso(empresaId, temporizadorId);
        return temporizadorRepository.tomarEnProduccion(temporizadorId, empresaId, Temporizadores.utc(ahora))
                .map(temporizador -> disparar(temporizador, caso, ahora,
                        new Momento(relojDeLaTienda.ahora(empresaId), null)))
                .orElse(false);
    }

    /** Dispara uno de prueba si sigue armado y vence en ese tick o antes. Dice si salto. */
    @Transactional
    public boolean enPrueba(Long empresaId, Long temporizadorId, int tick) {
        Optional<Caso> caso = bloquearSuCaso(empresaId, temporizadorId);
        return temporizadorRepository.tomarEnPrueba(temporizadorId, empresaId, tick)
                .map(temporizador -> disparar(temporizador, caso, reloj.instant(), Momento.en(tick)))
                .orElse(false);
    }

    /**
     * Anota que no pudo saltar y lo aplaza: un minuto por intento en produccion, un tick en prueba. Al tercer fallo
     * queda FALLIDO; el error es lo que se lee para saber por que.
     */
    @Transactional
    public void anotarFallo(Long empresaId, Long temporizadorId, String error) {
        temporizadorRepository.findByIdAndEmpresaId(temporizadorId, empresaId).ifPresent(temporizador -> {
            temporizador.setIntentos(temporizador.getIntentos() + 1);
            temporizador.setUltimoError(error.length() > LARGO_DEL_ERROR ? error.substring(0, LARGO_DEL_ERROR)
                    : error);
            if (temporizador.getIntentos() >= INTENTOS) {
                temporizador.setEstado(EstadoTemporizador.FALLIDO);
            } else if (temporizador.getModo() == ModoCaso.PRUEBA) {
                temporizador.setVenceTick(temporizador.getVenceTick() + 1);
            } else {
                temporizador.setVenceEn(LocalDateTime.ofInstant(reloj.instant(), ZoneOffset.UTC)
                        .plusMinutes(temporizador.getIntentos()).truncatedTo(ChronoUnit.MICROS));
            }
            temporizadorRepository.save(temporizador);
        });
    }

    /** D3: el caso primero. Uno de inicio no tiene caso que bloquear. */
    private Optional<Caso> bloquearSuCaso(Long empresaId, Long temporizadorId) {
        return temporizadorRepository.casoDe(temporizadorId, empresaId)
                .flatMap(casoId -> casoRepository.bloquear(casoId, empresaId));
    }

    private boolean disparar(Temporizador temporizador, Optional<Caso> caso, Instant ahora, Momento momento) {
        return switch (temporizador.getClase()) {
            case INICIO -> abrirUnCaso(temporizador, ahora, momento);
            case INTERMEDIO, BORDE -> caso.map(suyo -> enSuCaso(temporizador, suyo, ahora, momento))
                    .orElseGet(() -> cancelar(temporizador));
        };
    }

    /**
     * Un temporizador de un caso salta solo si su caso sigue abierto y su paso sigue esperando; si no, sobraba y se
     * cancela. Un caso en error no llega aqui: la busqueda de vencidos no lo trae.
     */
    private boolean enSuCaso(Temporizador temporizador, Caso caso, Instant ahora, Momento momento) {
        if (caso.getEstado() != EstadoCaso.ABIERTO) {
            return caso.getEstado().estaCerrado() && cancelar(temporizador);
        }
        Optional<ActividadCaso> paso = actividadCasoRepository
                .findByIdAndEmpresaId(temporizador.getActividadCasoId(), caso.getEmpresa().getId())
                .filter(esperando -> esperando.getEstado() == EstadoActividadCaso.EN_ESPERA);
        if (paso.isEmpty()) {
            return cancelar(temporizador);
        }
        GrafoDeVersion grafo = grafos.del(caso.getEmpresa().getId(), caso.getVersionProceso());
        Optional<TemporizadorDeLaVersion> evento = grafo.temporizador(temporizador.getNodoId());
        Optional<NodoDeLaVersion> nodo = grafo.nodo(temporizador.getNodoId());
        if (evento.isEmpty() || nodo.isEmpty()) {
            return fallar(temporizador, "El evento \"" + temporizador.getNodoNombre()
                    + "\" no esta en la version del caso.");
        }
        // El salto se anota antes de mover el caso: lo que el motor suelte al final de su vuelta ya no lo incluye
        temporizadores.despuesDelSalto(temporizador, ahora, momento.tick(), evento.orElseThrow().seRepite());
        if (temporizador.getClase() == ClaseTemporizador.INTERMEDIO) {
            motor.esperaCumplida(caso, grafo, paso.orElseThrow(), momento);
        } else {
            motor.saltarBorde(caso, grafo, paso.orElseThrow(), nodo.orElseThrow(),
                    !Boolean.FALSE.equals(temporizador.getInterrumpe()), momento);
        }
        return true;
    }

    /**
     * Un inicio temporizado abre un caso de produccion sobre su version, si sigue siendo la vigente: una que se retiro
     * o que otra reemplazo ya no abre casos, y su temporizador se cancela.
     */
    private boolean abrirUnCaso(Temporizador temporizador, Instant ahora, Momento momento) {
        Long empresaId = temporizador.getEmpresa().getId();
        Optional<VersionProceso> vigente = versionService.vigente(empresaId, temporizador.getProcesoId())
                .filter(version -> version.getId().equals(temporizador.getVersionProcesoId()));
        if (vigente.isEmpty()) {
            return cancelar(temporizador);
        }
        VersionProceso version = vigente.orElseThrow();
        GrafoDeVersion grafo = grafos.del(empresaId, version);
        Optional<TemporizadorDeLaVersion> evento = grafo.temporizador(temporizador.getNodoId());
        Optional<NodoDeLaVersion> inicio = grafo.nodo(temporizador.getNodoId());
        if (!grafo.ejecutable() || evento.isEmpty() || inicio.isEmpty()) {
            return fallar(temporizador, "El inicio \"" + temporizador.getNodoNombre()
                    + "\" ya no abre casos en la version vigente.");
        }
        temporizadores.despuesDelSalto(temporizador, ahora, momento.tick(), evento.orElseThrow().seRepite());
        Caso caso = casoRepository.save(Caso.builder()
                .empresa(version.getEmpresa())
                .proceso(version.getProceso())
                .versionProceso(version)
                .estado(EstadoCaso.ABIERTO)
                .modo(ModoCaso.PRODUCCION)
                .variables("{}")
                .tickInicio(momento.tick())
                .build());
        motor.arrancar(caso, grafo, inicio.orElseThrow(), momento);
        return true;
    }

    private boolean cancelar(Temporizador temporizador) {
        temporizador.setEstado(EstadoTemporizador.CANCELADO);
        temporizadorRepository.save(temporizador);
        return false;
    }

    private boolean fallar(Temporizador temporizador, String error) {
        temporizador.setEstado(EstadoTemporizador.FALLIDO);
        temporizador.setUltimoError(error);
        temporizadorRepository.save(temporizador);
        return false;
    }
}
