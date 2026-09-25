package com.facimus.procesos.ejecucion.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoGateway;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * El motor: coge los pasos PENDIENTE de un caso, los procesa segun lo que sea cada nodo y repite hasta que no queda
 * ninguno. Entonces el caso o termino, o esta esperando a una persona, a un mensaje o a que lleguen los demas
 * tokens de un join.
 *
 * <p>No guarda estado: recibe el caso ya bloqueado (D3) y trabaja dentro de su transaccion. Lo que decide sale del
 * grafo de la version y de las variables del caso, nunca del modelo vivo.
 *
 * <p>En este PR no hay mensajeria ni reloj. Las actividades de envio y de recepcion y los eventos de mensaje se
 * completan de inmediato, con su TODO, y los ticks se anotan como 0.
 */
@Component
@RequiredArgsConstructor
class MotorDeProcesos {

    /**
     * Cuantos pasos puede dar un caso en una sola vuelta. Un diagrama con un ciclo sin nada que espere daria
     * vueltas para siempre; antes que colgar la peticion, el caso queda en ERROR y lo dice.
     */
    private static final int PASOS_MAXIMOS = 1_000;

    private static final List<EstadoActividadCaso> VIVOS =
            List.of(EstadoActividadCaso.PENDIENTE, EstadoActividadCaso.EN_ESPERA);

    private final ActividadCasoRepository actividadCasoRepository;
    private final Bitacora bitacora;
    private final JsonMapper json;

    /** Abre el caso por el nodo que empieza el proceso y lo avanza hasta donde llegue. */
    void arrancar(Caso caso, GrafoDeVersion grafo, NodoDeLaVersion inicio, Long autorId) {
        bitacora.anotar(caso, TipoEventoCaso.CASO_ABIERTO,
                "El caso empieza en " + comillas(inicio.nombre()) + ".", autorId);
        activar(caso, grafo, inicio);
        avanzar(caso, grafo, autorId);
    }

    /**
     * Procesa los pasos pendientes hasta que no queda ninguno. Cada vuelta relee los pendientes porque procesar uno
     * crea los siguientes, y un join puede volver a ponerse pendiente cuando le llega otro token.
     */
    void avanzar(Caso caso, GrafoDeVersion grafo, Long autorId) {
        VariablesDelCaso variables = VariablesDelCaso.de(caso, json);
        int pasos = 0;
        List<ActividadCaso> pendientes = pendientesDe(caso);
        while (!pendientes.isEmpty() && caso.getEstado() == EstadoCaso.ABIERTO) {
            for (ActividadCaso token : pendientes) {
                if (++pasos > PASOS_MAXIMOS || caso.getEstado() != EstadoCaso.ABIERTO) {
                    break;
                }
                procesar(caso, grafo, token, variables, autorId);
            }
            if (pasos > PASOS_MAXIMOS) {
                enError(caso, "El caso dio " + PASOS_MAXIMOS + " pasos sin detenerse: el diagrama tiene un ciclo "
                        + "que nunca espera.");
                return;
            }
            pendientes = pendientesDe(caso);
        }
        terminarSiNoQuedanTokens(caso);
    }

    /**
     * Sigue el caso desde un nodo que se completo fuera del bucle, como una tarea que alguien acaba de completar o
     * un mensaje que llego a quien lo esperaba.
     */
    void seguirDesde(Caso caso, GrafoDeVersion grafo, Long nodoId, Long autorId) {
        seguir(caso, grafo, grafo.salidasDe(nodoId));
        avanzar(caso, grafo, autorId);
    }

    /** Pone un token en un nodo. Un join no estrena token: suma una llegada al que ya estaba esperando. */
    void activar(Caso caso, GrafoDeVersion grafo, NodoDeLaVersion nodo) {
        if (sincroniza(grafo, nodo)) {
            var esperando = actividadCasoRepository.findFirstByCasoIdAndEmpresaIdAndNodoIdAndEstadoOrderByIdAsc(
                    caso.getId(), caso.getEmpresa().getId(), nodo.id(), EstadoActividadCaso.EN_ESPERA);
            if (esperando.isPresent()) {
                ActividadCaso join = esperando.orElseThrow();
                join.setLlegadas(join.getLlegadas() + 1);
                join.setEstado(EstadoActividadCaso.PENDIENTE);
                actividadCasoRepository.save(join);
                return;
            }
        }
        actividadCasoRepository.save(ActividadCaso.builder()
                .empresa(caso.getEmpresa())
                .caso(caso)
                .nodoId(nodo.id())
                .nodoNombre(nodo.nombre())
                .tipoNodo(nodo.tipo())
                .subtipo(nodo.subtipo())
                .rolProcesoId(nodo.rolProcesoId())
                .estado(EstadoActividadCaso.PENDIENTE)
                .llegadas(1)
                .tickInicio(caso.getTickInicio())
                .build());
    }

    /** Cierra los tokens vivos de un caso que deja de correr, por cancelacion o por un envio que no se pudo salvar. */
    void apagarTokens(Caso caso) {
        for (ActividadCaso token : vivosDe(caso)) {
            token.setEstado(EstadoActividadCaso.OMITIDA);
            token.setTickFin(caso.getTickInicio());
            actividadCasoRepository.save(token);
        }
    }

    private void procesar(Caso caso, GrafoDeVersion grafo, ActividadCaso token, VariablesDelCaso variables,
            Long autorId) {
        var nodo = grafo.nodo(token.getNodoId());
        if (nodo.isEmpty()) {
            // La version manda: si el nodo no esta en ella, este token no puede seguir.
            token.setEstado(EstadoActividadCaso.FALLIDA);
            actividadCasoRepository.save(token);
            enError(caso, "El nodo " + comillas(token.getNodoNombre()) + " no esta en la version del caso.");
            return;
        }
        switch (nodo.orElseThrow().tipo()) {
            case EVENTO -> procesarEvento(caso, grafo, token, nodo.orElseThrow());
            case ACTIVIDAD -> procesarActividad(caso, grafo, token, nodo.orElseThrow(), autorId);
            case GATEWAY -> procesarGateway(caso, grafo, token, nodo.orElseThrow(), variables);
        }
    }

    private void procesarEvento(Caso caso, GrafoDeVersion grafo, ActividadCaso token, NodoDeLaVersion nodo) {
        completar(caso, token);
        if (nodo.terminaElProceso()) {
            // TODO (PR 24): un MENSAJE_FIN manda su mensaje antes de consumir el token.
            bitacora.anotar(caso, TipoEventoCaso.NODO_ACTIVADO,
                    "Un camino del caso termina en " + comillas(nodo.nombre()) + ".");
            return;
        }
        // TODO (PR 24): un MENSAJE_INTERMEDIO se queda esperando su mensaje en vez de pasar de largo.
        bitacora.anotar(caso, TipoEventoCaso.NODO_ACTIVADO, "Paso por " + comillas(nodo.nombre()) + ".");
        seguir(caso, grafo, grafo.salidasDe(nodo.id()));
    }

    private void procesarActividad(Caso caso, GrafoDeVersion grafo, ActividadCaso token, NodoDeLaVersion nodo,
            Long autorId) {
        if (nodo.esTareaDeUsuario()) {
            token.setEstado(EstadoActividadCaso.EN_ESPERA);
            actividadCasoRepository.save(token);
            bitacora.anotar(caso, TipoEventoCaso.TAREA_CREADA,
                    comillas(nodo.nombre()) + " queda en la bandeja a la espera de que alguien la complete.",
                    autorId);
            return;
        }
        // TODO (PR 24): una actividad de envio manda su mensaje y una de recepcion se queda esperandolo.
        completar(caso, token);
        bitacora.anotar(caso, TipoEventoCaso.NODO_ACTIVADO,
                comillas(nodo.nombre()) + " (" + enMinusculas(nodo.subtipo()) + ") se completa sola.");
        seguir(caso, grafo, grafo.salidasDe(nodo.id()));
    }

    private void procesarGateway(Caso caso, GrafoDeVersion grafo, ActividadCaso token, NodoDeLaVersion nodo,
            VariablesDelCaso variables) {
        if (sincroniza(grafo, nodo) && !puedeSeguirElJoin(caso, grafo, token, nodo)) {
            token.setEstado(EstadoActividadCaso.EN_ESPERA);
            actividadCasoRepository.save(token);
            return;
        }
        List<ArcoDeLaVersion> salidas = grafo.salidasDe(nodo.id());
        // Solo elige el que se abre en varios caminos. Uno convergente tiene una sola salida y la sigue: decidir
        // ahi seria pedirle una condicion al flujo que sale del join, que nadie escribe ni el modelo exige.
        if (salidas.size() >= 2 && nodo.eligePorCondicion()) {
            decidir(caso, grafo, token, nodo, variables);
            return;
        }
        completar(caso, token);
        bitacora.anotar(caso, TipoEventoCaso.NODO_ACTIVADO, salidas.size() >= 2
                ? comillas(nodo.nombre()) + " abre sus " + salidas.size() + " caminos a la vez."
                : "Paso por " + comillas(nodo.nombre()) + ".");
        seguir(caso, grafo, salidas);
    }

    /**
     * Un join paralelo sigue cuando han llegado tantos tokens como caminos entran. Uno inclusivo sigue cuando
     * ningun otro token vivo del caso tiene camino hasta el, que es lo que la alcanzabilidad del grafo responde.
     */
    private boolean puedeSeguirElJoin(Caso caso, GrafoDeVersion grafo, ActividadCaso token, NodoDeLaVersion nodo) {
        if (nodo.esGatewayDe(TipoGateway.PARALELO)) {
            return token.getLlegadas() >= grafo.entradasDe(nodo.id()).size();
        }
        return vivosDe(caso).stream()
                .filter(otro -> !otro.getId().equals(token.getId()))
                .noneMatch(otro -> grafo.alcanza(otro.getNodoId(), nodo.id()));
    }

    /**
     * Un exclusivo toma la primera salida verdadera; un inclusivo, todas. Si ninguna se cumple queda la salida por
     * defecto, y si tampoco la hay el caso no tiene por donde seguir y pasa a ERROR, que es lo que la advertencia
     * A-05 avisaba al publicar.
     */
    private void decidir(Caso caso, GrafoDeVersion grafo, ActividadCaso token, NodoDeLaVersion nodo,
            VariablesDelCaso variables) {
        variables.olvidarAusentes();
        List<ArcoDeLaVersion> elegidos = new ArrayList<>();
        ArcoDeLaVersion porDefecto = null;
        // Las condiciones se evaluan todas aunque el exclusivo se quede con la primera: asi las variables que le
        // faltan a las demas tambien quedan anotadas, que es lo que se lee cuando el caso no fue por donde se
        // esperaba.
        for (ArcoDeLaVersion salida : grafo.salidasDe(nodo.id())) {
            if (salida.porDefecto()) {
                porDefecto = salida;
            } else if (salida.condicion() != null && salida.condicion().seCumple(variables)
                    && (elegidos.isEmpty() || !nodo.esGatewayDe(TipoGateway.EXCLUSIVO))) {
                elegidos.add(salida);
            }
        }
        anotarAusentes(caso, nodo, variables);
        if (elegidos.isEmpty() && porDefecto != null) {
            elegidos.add(porDefecto);
        }
        if (elegidos.isEmpty()) {
            token.setEstado(EstadoActividadCaso.EN_ESPERA);
            actividadCasoRepository.save(token);
            bitacora.anotar(caso, TipoEventoCaso.SIN_CAMINO, comillas(nodo.nombre())
                    + " no encontro por donde seguir: ninguna condicion se cumplio y no hay salida por defecto.");
            // El token se queda donde esta: corregir las variables y reintentar vuelve a evaluar este gateway.
            caso.setEstado(EstadoCaso.ERROR);
            return;
        }
        completar(caso, token);
        bitacora.anotar(caso, TipoEventoCaso.GATEWAY_DECIDIO, comillas(nodo.nombre()) + " sigue por "
                + elegidos.stream().map(ArcoDeLaVersion::comoSeLlama).map(MotorDeProcesos::comillas).toList());
        seguir(caso, grafo, elegidos);
    }

    private void anotarAusentes(Caso caso, NodoDeLaVersion nodo, VariablesDelCaso variables) {
        for (String ausente : variables.ausentes()) {
            bitacora.anotar(caso, TipoEventoCaso.VARIABLE_AUSENTE, comillas(nodo.nombre()) + " pregunto por "
                    + comillas(ausente) + " y el caso no la tiene, asi que esa comparacion sale falsa.");
        }
        variables.olvidarAusentes();
    }

    private void seguir(Caso caso, GrafoDeVersion grafo, List<ArcoDeLaVersion> salidas) {
        for (ArcoDeLaVersion salida : salidas) {
            grafo.nodo(salida.destinoId()).ifPresent(destino -> activar(caso, grafo, destino));
        }
    }

    /** Un caso termina cuando muere su ultimo token; mientras quede uno vivo, sigue abierto. */
    private void terminarSiNoQuedanTokens(Caso caso) {
        if (caso.getEstado() != EstadoCaso.ABIERTO || !vivosDe(caso).isEmpty()) {
            return;
        }
        caso.setEstado(EstadoCaso.TERMINADO);
        caso.setTickFin(caso.getTickInicio());
        caso.setFechaFin(LocalDateTime.now());
        bitacora.anotar(caso, TipoEventoCaso.CASO_TERMINADO, "El ultimo camino del caso llego a un fin.");
    }

    private void enError(Caso caso, String detalle) {
        caso.setEstado(EstadoCaso.ERROR);
        bitacora.anotar(caso, TipoEventoCaso.SIN_CAMINO, detalle);
    }

    private void completar(Caso caso, ActividadCaso token) {
        token.setEstado(EstadoActividadCaso.COMPLETADA);
        token.setTickFin(caso.getTickInicio());
        actividadCasoRepository.save(token);
    }

    /** Solo el paralelo y el inclusivo sincronizan; el exclusivo convergente deja pasar cada token que llega. */
    private static boolean sincroniza(GrafoDeVersion grafo, NodoDeLaVersion nodo) {
        return nodo.esGateway() && !nodo.esGatewayDe(TipoGateway.EXCLUSIVO)
                && grafo.entradasDe(nodo.id()).size() >= 2;
    }

    private List<ActividadCaso> pendientesDe(Caso caso) {
        return actividadCasoRepository.findAllByCasoIdAndEmpresaIdAndEstadoOrderByIdAsc(caso.getId(),
                caso.getEmpresa().getId(), EstadoActividadCaso.PENDIENTE);
    }

    private List<ActividadCaso> vivosDe(Caso caso) {
        return actividadCasoRepository.findAllByCasoIdAndEmpresaIdAndEstadoInOrderByIdAsc(caso.getId(),
                caso.getEmpresa().getId(), VIVOS);
    }

    private static String comillas(String texto) {
        return "\"" + texto + "\"";
    }

    /** El subtipo se guarda como lo dice el enumerado; en una frase se lee mejor en minusculas. */
    private static String enMinusculas(String subtipo) {
        return switch (TipoActividad.valueOf(subtipo)) {
            case SERVICIO -> "servicio";
            case ENVIO -> "envio";
            case RECEPCION -> "recepcion";
            default -> "usuario";
        };
    }
}
