package com.facimus.procesos.ejecucion.service.impl;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.facimus.procesos.common.tiempo.ExpresionDeTiempo;
import com.facimus.procesos.common.tiempo.ExpresionDeTiempo.Ciclo;
import com.facimus.procesos.common.tiempo.ExpresionMalEscrita;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.ClaseTemporizador;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoTemporizador;
import com.facimus.procesos.ejecucion.model.ModoCaso;
import com.facimus.procesos.ejecucion.model.Temporizador;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.repository.TemporizadorRepository;
import com.facimus.procesos.gestion.model.VersionProceso;
import com.facimus.procesos.gestion.service.CalendarioLaboral;
import com.facimus.procesos.gestion.service.CalendarioService;

import lombok.RequiredArgsConstructor;

/**
 * PR 68: armar y soltar temporizadores. Lo llama el motor dentro de la transaccion del caso, con el caso ya bloqueado
 * (D3): un token que llega a una espera arma la suya, una actividad que se queda esperando arma sus bordes, y al final
 * de cada vuelta se sueltan los que ya no tienen a quien esperar. Los inicios temporizados los arma la publicacion.
 *
 * <p>Un caso de produccion cuenta con el reloj de pared y el calendario de la tienda; uno de prueba, en ticks.
 */
@Component
@RequiredArgsConstructor
class Temporizadores {

    private static final DateTimeFormatter COMO_SE_LEE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** Lo que se guarda en vence_en, y bastante menos que lo que cabe en un timestamp: ningun plazo pasa del 9999. */
    private static final Instant ULTIMO_INSTANTE = Instant.parse("9999-12-31T23:59:59Z");

    private final TemporizadorRepository temporizadorRepository;
    private final ActividadCasoRepository actividadCasoRepository;
    private final CalendarioService calendarioService;
    private final Bitacora bitacora;
    private final Clock reloj;

    /**
     * Arma el temporizador de un evento para ese token: el de una espera, o el de un borde de la actividad donde el
     * token espera. Dice si lo armo: uno cuya expresion no se entiende, o un ciclo sin vueltas, no se arma.
     */
    boolean armar(Caso caso, ActividadCaso token, TemporizadorDeLaVersion evento, Momento momento) {
        Long empresaId = caso.getEmpresa().getId();
        CalendarioLaboral calendario = calendarioService.laboral(empresaId);
        Temporizador temporizador = nuevo(evento, caso.getModo())
                .empresa(caso.getEmpresa())
                .procesoId(caso.getProceso().getId())
                .versionProcesoId(caso.getVersionProceso().getId())
                .casoId(caso.getId())
                .actividadCasoId(token.getId())
                .build();
        try {
            if (!programar(temporizador, momento.tick(), calendario)) {
                bitacora.anotar(caso, momento.tick(), TipoEventoCaso.TEMPORIZADOR_PROGRAMADO, comillas(evento.nombre())
                        + " no tiene ninguna vuelta por delante: no se arma.");
                return false;
            }
        } catch (ExpresionMalEscrita mal) {
            bitacora.anotar(caso, momento.tick(), TipoEventoCaso.TEMPORIZADOR_PROGRAMADO, comillas(evento.nombre())
                    + " no se arma: " + mal.getMessage());
            return false;
        }
        temporizadorRepository.save(temporizador);
        bitacora.anotar(caso, momento.tick(), TipoEventoCaso.TEMPORIZADOR_PROGRAMADO,
                comillas(evento.nombre()) + " vence " + cuando(temporizador, calendario.zona()) + ".");
        return true;
    }

    /** Arma los bordes que cuelgan de la actividad donde ese token se quedo esperando. */
    void armarBordes(Caso caso, GrafoDeVersion grafo, ActividadCaso token, Momento momento) {
        for (TemporizadorDeLaVersion borde : grafo.bordesDe(token.getNodoId())) {
            armar(caso, token, borde, momento);
        }
    }

    /**
     * Arma un inicio temporizado de la version vigente, siempre en produccion: es el reloj de pared el que abre casos
     * solos. Una version de prueba no arma el suyo; su caso empieza cuando se pide la prueba.
     */
    boolean armarInicio(VersionProceso version, TemporizadorDeLaVersion inicio) {
        Long empresaId = version.getEmpresa().getId();
        Temporizador temporizador = nuevo(inicio, ModoCaso.PRODUCCION)
                .empresa(version.getEmpresa())
                .procesoId(version.getProceso().getId())
                .versionProcesoId(version.getId())
                .build();
        try {
            if (!programar(temporizador, 0, calendarioService.laboral(empresaId))) {
                return false;
            }
        } catch (ExpresionMalEscrita _) {
            return false;
        }
        temporizadorRepository.save(temporizador);
        return true;
    }

    /**
     * Cancela los temporizadores del caso que ya no tienen a quien esperar: los de un paso que dejo de esperar (la
     * tarea se completo, el mensaje llego, el borde que interrumpe saco a la actividad) y, si el caso se cerro, todos.
     * Un caso en error conserva los suyos: sus tokens siguen ahi, y al rescatarlo vuelven a contar.
     */
    void soltarLosQueSobran(Caso caso) {
        Long empresaId = caso.getEmpresa().getId();
        List<Temporizador> armados = temporizadorRepository.findAllByCasoIdAndEmpresaIdAndEstadoOrderByIdAsc(
                caso.getId(), empresaId, EstadoTemporizador.PROGRAMADO);
        if (armados.isEmpty()) {
            return;
        }
        Set<Long> esperando = caso.getEstado().estaCerrado() ? Set.of()
                : actividadCasoRepository.findAllByCasoIdAndEmpresaIdAndEstadoOrderByIdAsc(caso.getId(), empresaId,
                        EstadoActividadCaso.EN_ESPERA).stream().map(ActividadCaso::getId).collect(Collectors.toSet());
        for (Temporizador temporizador : armados) {
            if (!esperando.contains(temporizador.getActividadCasoId())) {
                temporizador.setEstado(EstadoTemporizador.CANCELADO);
                temporizadorRepository.save(temporizador);
            }
        }
    }

    /**
     * Despues de un salto: un ciclo con vueltas por delante vuelve a quedar programado para la siguiente; lo demas
     * queda disparado.
     *
     * @param tick el tick en que salto, para uno de prueba
     */
    void despuesDelSalto(Temporizador temporizador, Instant ahora, int tick, boolean seRepite) {
        temporizador.setDisparos(temporizador.getDisparos() + 1);
        temporizador.setDisparadoEn(utc(ahora));
        temporizador.setIntentos(0);
        temporizador.setUltimoError(null);
        Optional<Integer> otroTick = Optional.empty();
        Optional<Instant> otraVez = Optional.empty();
        if (seRepite) {
            Ciclo ciclo = ExpresionDeTiempo.ciclo(temporizador.getExpresion());
            if (temporizador.getModo() == ModoCaso.PRUEBA) {
                otroTick = quedanVueltas(temporizador) ? Optional.of(tick + CuandoVence.periodoEnTicks(ciclo))
                        : Optional.empty();
            } else {
                ZoneId zona = calendarioService.laboral(temporizador.getEmpresa().getId()).zona();
                otraVez = CuandoVence.siguiente(ciclo, temporizador.getCreadoEn().toInstant(ZoneOffset.UTC), ahora,
                        zona);
            }
        }
        if (otroTick.isPresent()) {
            temporizador.setVenceTick(otroTick.get());
        } else if (otraVez.isPresent()) {
            temporizador.setVenceEn(utc(otraVez.get()));
        } else {
            temporizador.setEstado(EstadoTemporizador.DISPARADO);
        }
        temporizadorRepository.save(temporizador);
    }

    /** En prueba no hay fechas que contar: un ciclo de tres salta tres veces. */
    private static boolean quedanVueltas(Temporizador temporizador) {
        return temporizador.getRepeticiones() == null
                || temporizador.getDisparos() < temporizador.getRepeticiones();
    }

    /**
     * Pone cuando vence: una hora en produccion, un tick en prueba. Dice si hay alguna vez por delante.
     *
     * <p>El diagnostico ya pone topes a lo que se escribe, pero una version publicada antes de ellos, o un calendario
     * con una jornada de minutos, todavia puede pedir una cuenta que se sale de las fechas: eso tampoco se arma, en vez
     * de tumbar la apertura del caso o la publicacion.
     */
    private boolean programar(Temporizador temporizador, int tick, CalendarioLaboral calendario) {
        try {
            if (temporizador.getModo() == ModoCaso.PRUEBA) {
                temporizador.setVenceTick(Math.addExact(tick, CuandoVence.ticks(temporizador.getTipoTemporizador(),
                        temporizador.getExpresion(), calendario)));
                return true;
            }
            Optional<Instant> vence = CuandoVence.primero(temporizador.getTipoTemporizador(),
                    temporizador.getExpresion(), temporizador.getCreadoEn().toInstant(ZoneOffset.UTC), calendario);
            if (vence.isEmpty()) {
                return false;
            }
            if (vence.orElseThrow().isAfter(ULTIMO_INSTANTE)) {
                throw new ExpresionMalEscrita(fueraDeLaCuenta(temporizador));
            }
            temporizador.setVenceEn(utc(vence.orElseThrow()));
            return true;
        } catch (DateTimeException | ArithmeticException | IllegalStateException _) {
            throw new ExpresionMalEscrita(fueraDeLaCuenta(temporizador));
        }
    }

    private static String fueraDeLaCuenta(Temporizador temporizador) {
        return "\"" + temporizador.getExpresion() + "\" no vence dentro de lo que se puede contar: fechas hasta el "
                + "ano 9999 y tiempo habil en los proximos diez anos.";
    }

    private Temporizador.TemporizadorBuilder<?, ?> nuevo(TemporizadorDeLaVersion evento, ModoCaso modo) {
        return Temporizador.builder()
                .nodoId(evento.nodoId())
                .nodoNombre(evento.nombre())
                .clase(evento.clase())
                .modo(modo)
                .tipoTemporizador(evento.tipo())
                .expresion(evento.expresion().strip())
                .interrumpe(evento.clase() == ClaseTemporizador.BORDE ? evento.interrumpe() : null)
                .repeticiones(evento.seRepite() ? repeticiones(evento.expresion()) : null)
                .estado(EstadoTemporizador.PROGRAMADO)
                .creadoEn(utc(reloj.instant()));
    }

    private static Integer repeticiones(String expresion) {
        try {
            return ExpresionDeTiempo.ciclo(expresion).repeticiones();
        } catch (ExpresionMalEscrita _) {
            return null;
        }
    }

    private static String cuando(Temporizador temporizador, ZoneId zona) {
        if (temporizador.getModo() == ModoCaso.PRUEBA) {
            return "en el tick " + temporizador.getVenceTick();
        }
        return "el " + COMO_SE_LEE.format(temporizador.getVenceEn().atOffset(ZoneOffset.UTC).atZoneSameInstant(zona))
                + " (" + zona.getId() + ")";
    }

    /** Las horas de la tabla van en UTC y con microsegundos, que es lo que guarda PostgreSQL. */
    static LocalDateTime utc(Instant instante) {
        return LocalDateTime.ofInstant(instante, ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private static String comillas(String texto) {
        return "\"" + texto + "\"";
    }
}
