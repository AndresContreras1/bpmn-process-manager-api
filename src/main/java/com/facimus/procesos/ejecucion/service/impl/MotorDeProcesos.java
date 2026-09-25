package com.facimus.procesos.ejecucion.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
 * <p>El motor no lee el reloj: recibe el {@link Momento} en que trabaja. Todo lo que pasa en una operacion pasa
 * en el mismo tick, y asi una prueba puede decir en que tick ocurre sin montar la tienda entera.
 *
 * <p>Los mensajes entran y salen por las bandejas, no por la red: lo que el motor hace al pasar por un nodo que
 * manda es escribir una fila en la de salida, y lo que hace en uno que espera es quedarse en espera. Quien entrega
 * y quien correlaciona estan fuera, porque eso ya no es la semantica del diagrama sino el tiempo (D9).
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
    private final BandejaDeSalida bandejaDeSalida;
    private final Bitacora bitacora;
    private final JsonMapper json;

    /** Abre el caso por el nodo que empieza el proceso y lo avanza hasta donde llegue. */
    void arrancar(Caso caso, GrafoDeVersion grafo, NodoDeLaVersion inicio, Momento momento) {
        bitacora.anotar(caso, momento.tick(), TipoEventoCaso.CASO_ABIERTO,
                "El caso empieza en " + comillas(inicio.nombre()) + ".", momento.autorId());
        activar(caso, grafo, inicio, momento);
        avanzar(caso, grafo, momento);
    }

    /**
     * Procesa los pasos pendientes hasta que no queda ninguno. Cada vuelta relee los pendientes porque procesar uno
     * crea los siguientes, y un join puede volver a ponerse pendiente cuando le llega otro token.
     */
    void avanzar(Caso caso, GrafoDeVersion grafo, Momento momento) {
        VariablesDelCaso variables = VariablesDelCaso.de(caso, json);
        int pasos = 0;
        List<ActividadCaso> pendientes = pendientesDe(caso);
        while (!pendientes.isEmpty() && caso.getEstado() == EstadoCaso.ABIERTO) {
            for (ActividadCaso token : pendientes) {
                if (++pasos > PASOS_MAXIMOS || caso.getEstado() != EstadoCaso.ABIERTO) {
                    break;
                }
                procesar(caso, grafo, token, variables, momento);
            }
            if (pasos > PASOS_MAXIMOS) {
                enError(caso, momento, "El caso dio " + PASOS_MAXIMOS + " pasos sin detenerse: el diagrama tiene "
                        + "un ciclo que nunca espera.");
                return;
            }
            pendientes = pendientesDe(caso);
        }
        terminarSiNoQuedanTokens(caso, momento);
    }

    /**
     * Sigue el caso desde un nodo que se completo fuera del bucle, como una tarea que alguien acaba de completar o
     * un mensaje que llego a quien lo esperaba.
     */
    void seguirDesde(Caso caso, GrafoDeVersion grafo, Long nodoId, Momento momento) {
        seguir(caso, grafo, grafo.salidasDe(nodoId), momento);
        avanzar(caso, grafo, momento);
    }

    /** Pone un token en un nodo. Un join no estrena token: suma una llegada al que ya estaba esperando. */
    void activar(Caso caso, GrafoDeVersion grafo, NodoDeLaVersion nodo, Momento momento) {
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
                .tickInicio(momento.tick())
                .build());
    }

    /** Cierra los tokens vivos de un caso que deja de correr, por cancelacion o por un envio que no se pudo salvar. */
    void apagarTokens(Caso caso, Momento momento) {
        for (ActividadCaso token : vivosDe(caso)) {
            token.setEstado(EstadoActividadCaso.OMITIDA);
            token.setTickFin(momento.tick());
            actividadCasoRepository.save(token);
        }
    }

    private void procesar(Caso caso, GrafoDeVersion grafo, ActividadCaso token, VariablesDelCaso variables,
            Momento momento) {
        var nodo = grafo.nodo(token.getNodoId());
        if (nodo.isEmpty()) {
            // La version manda: si el nodo no esta en ella, este token no puede seguir.
            token.setEstado(EstadoActividadCaso.FALLIDA);
            actividadCasoRepository.save(token);
            enError(caso, momento,
                    "El nodo " + comillas(token.getNodoNombre()) + " no esta en la version del caso.");
            return;
        }
        switch (nodo.orElseThrow().tipo()) {
            case EVENTO -> procesarEvento(caso, grafo, token, nodo.orElseThrow(), variables, momento);
            case ACTIVIDAD -> procesarActividad(caso, grafo, token, nodo.orElseThrow(), variables, momento);
            case GATEWAY -> procesarGateway(caso, grafo, token, nodo.orElseThrow(), variables, momento);
        }
    }

    private void procesarEvento(Caso caso, GrafoDeVersion grafo, ActividadCaso token, NodoDeLaVersion nodo,
            VariablesDelCaso variables, Momento momento) {
        if (quedarseEsperando(caso, grafo, token, nodo, momento)) {
            return;
        }
        mandarLoQueTenga(caso, grafo, nodo, variables, momento);
        completar(caso, token, momento);
        if (nodo.terminaElProceso()) {
            bitacora.anotar(caso, momento.tick(), TipoEventoCaso.NODO_ACTIVADO,
                    "Un camino del caso termina en " + comillas(nodo.nombre()) + ".");
            return;
        }
        bitacora.anotar(caso, momento.tick(), TipoEventoCaso.NODO_ACTIVADO,
                "Paso por " + comillas(nodo.nombre()) + ".");
        seguir(caso, grafo, grafo.salidasDe(nodo.id()), momento);
    }

    private void procesarActividad(Caso caso, GrafoDeVersion grafo, ActividadCaso token, NodoDeLaVersion nodo,
            VariablesDelCaso variables, Momento momento) {
        if (nodo.esTareaDeUsuario()) {
            token.setEstado(EstadoActividadCaso.EN_ESPERA);
            actividadCasoRepository.save(token);
            bitacora.anotar(caso, momento.tick(), TipoEventoCaso.TAREA_CREADA,
                    comillas(nodo.nombre()) + " queda en la bandeja a la espera de que alguien la complete.",
                    momento.autorId());
            return;
        }
        if (quedarseEsperando(caso, grafo, token, nodo, momento)) {
            return;
        }
        boolean mando = mandarLoQueTenga(caso, grafo, nodo, variables, momento);
        completar(caso, token, momento);
        if (!mando) {
            bitacora.anotar(caso, momento.tick(), TipoEventoCaso.NODO_ACTIVADO,
                    comillas(nodo.nombre()) + " (" + enMinusculas(nodo.subtipo()) + ") se completa sola.");
        }
        seguir(caso, grafo, grafo.salidasDe(nodo.id()), momento);
    }

    /**
     * Un nodo que espera un mensaje se queda en espera hasta que llegue. Si es de los que esperan pero no tiene
     * ninguno anclado, sigue de largo: el diagnostico no deja publicar eso (E-11), y un caso parado para siempre
     * seria peor que uno que sigue.
     */
    private boolean quedarseEsperando(Caso caso, GrafoDeVersion grafo, ActividadCaso token, NodoDeLaVersion nodo,
            Momento momento) {
        if (!nodo.esperaUnMensaje()) {
            return false;
        }
        Optional<MensajeDeLaVersion> esperado = grafo.mensajeQueEspera(nodo.id());
        if (esperado.isEmpty()) {
            return false;
        }
        token.setEstado(EstadoActividadCaso.EN_ESPERA);
        actividadCasoRepository.save(token);
        bitacora.anotar(caso, momento.tick(), TipoEventoCaso.NODO_ACTIVADO, comillas(nodo.nombre())
                + " espera el mensaje " + comillas(esperado.orElseThrow().nombre()) + ".");
        return true;
    }

    /** Manda el mensaje anclado al nodo, si el nodo es de los que mandan y tiene uno. */
    private boolean mandarLoQueTenga(Caso caso, GrafoDeVersion grafo, NodoDeLaVersion nodo,
            VariablesDelCaso variables, Momento momento) {
        if (!nodo.puedeEnviar()) {
            return false;
        }
        Optional<MensajeDeLaVersion> mensaje = grafo.mensajeQueManda(nodo.id());
        mensaje.ifPresent(anclado -> bandejaDeSalida.enviar(caso, anclado, variables, momento));
        return mensaje.isPresent();
    }

    /**
     * Llego el mensaje que un token estaba esperando: el token se completa y el caso sigue por sus salidas. Lo
     * llama la mensajeria con el caso ya bloqueado, igual que completar una tarea.
     */
    void mensajeRecibido(Caso caso, GrafoDeVersion grafo, ActividadCaso token, Momento momento) {
        completar(caso, token, momento);
        seguirDesde(caso, grafo, token.getNodoId(), momento);
    }

    /**
     * Un envio no llego a su destino, y lo que pasa ahora lo dice el propio mensaje: seguir por donde iba, desviar
     * el caso a la actividad que atiende el problema, o darlo por perdido.
     */
    void envioFallido(Caso caso, GrafoDeVersion grafo, MensajeDeLaVersion mensaje, String error, Momento momento) {
        switch (mensaje.siFallaOContinuar()) {
            case CONTINUAR -> bitacora.anotar(caso, momento.tick(), TipoEventoCaso.ENVIO_FALLIDO,
                    comillas(mensaje.nombre()) + " no llego (" + error + "), y el caso sigue por donde iba.");
            case MANEJAR_ERROR -> desviar(caso, grafo, mensaje, error, momento);
            case FINALIZAR -> darPorPerdido(caso, mensaje, error, momento);
        }
    }

    private void desviar(Caso caso, GrafoDeVersion grafo, MensajeDeLaVersion mensaje, String error,
            Momento momento) {
        Optional<NodoDeLaVersion> manejo = mensaje.nodoQueManejaElError().flatMap(grafo::nodo);
        if (manejo.isEmpty()) {
            enError(caso, momento, comillas(mensaje.nombre()) + " no llego (" + error + ") y la actividad que "
                    + "tenia que atenderlo no esta en la version del caso.");
            return;
        }
        bitacora.anotar(caso, momento.tick(), TipoEventoCaso.ENVIO_FALLIDO, comillas(mensaje.nombre())
                + " no llego (" + error + "), asi que el caso pasa por "
                + comillas(manejo.orElseThrow().nombre()) + ".");
        activar(caso, grafo, manejo.orElseThrow(), momento);
        avanzar(caso, grafo, momento);
    }

    private void darPorPerdido(Caso caso, MensajeDeLaVersion mensaje, String error, Momento momento) {
        apagarTokens(caso, momento);
        caso.setEstado(EstadoCaso.FALLIDO);
        caso.setTickFin(momento.tick());
        caso.setFechaFin(LocalDateTime.now());
        bitacora.anotar(caso, momento.tick(), TipoEventoCaso.ENVIO_FALLIDO, comillas(mensaje.nombre())
                + " no llego (" + error + ") y sin el el caso no tiene sentido: queda fallido.");
    }

    private void procesarGateway(Caso caso, GrafoDeVersion grafo, ActividadCaso token, NodoDeLaVersion nodo,
            VariablesDelCaso variables, Momento momento) {
        if (sincroniza(grafo, nodo) && !puedeSeguirElJoin(caso, grafo, token, nodo)) {
            token.setEstado(EstadoActividadCaso.EN_ESPERA);
            actividadCasoRepository.save(token);
            return;
        }
        List<ArcoDeLaVersion> salidas = grafo.salidasDe(nodo.id());
        // Solo elige el que se abre en varios caminos. Uno convergente tiene una sola salida y la sigue: decidir
        // ahi seria pedirle una condicion al flujo que sale del join, que nadie escribe ni el modelo exige.
        if (salidas.size() >= 2 && nodo.eligePorCondicion()) {
            decidir(caso, grafo, token, nodo, variables, momento);
            return;
        }
        completar(caso, token, momento);
        bitacora.anotar(caso, momento.tick(), TipoEventoCaso.NODO_ACTIVADO, salidas.size() >= 2
                ? comillas(nodo.nombre()) + " abre sus " + salidas.size() + " caminos a la vez."
                : "Paso por " + comillas(nodo.nombre()) + ".");
        seguir(caso, grafo, salidas, momento);
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
            VariablesDelCaso variables, Momento momento) {
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
        anotarAusentes(caso, nodo, variables, momento);
        if (elegidos.isEmpty() && porDefecto != null) {
            elegidos.add(porDefecto);
        }
        if (elegidos.isEmpty()) {
            token.setEstado(EstadoActividadCaso.EN_ESPERA);
            actividadCasoRepository.save(token);
            bitacora.anotar(caso, momento.tick(), TipoEventoCaso.SIN_CAMINO, comillas(nodo.nombre())
                    + " no encontro por donde seguir: ninguna condicion se cumplio y no hay salida por defecto.");
            // El token se queda donde esta: corregir las variables y reintentar vuelve a evaluar este gateway.
            caso.setEstado(EstadoCaso.ERROR);
            return;
        }
        completar(caso, token, momento);
        bitacora.anotar(caso, momento.tick(), TipoEventoCaso.GATEWAY_DECIDIO, comillas(nodo.nombre()) + " sigue por "
                + elegidos.stream().map(ArcoDeLaVersion::comoSeLlama).map(MotorDeProcesos::comillas).toList());
        seguir(caso, grafo, elegidos, momento);
    }

    private void anotarAusentes(Caso caso, NodoDeLaVersion nodo, VariablesDelCaso variables, Momento momento) {
        for (String ausente : variables.ausentes()) {
            bitacora.anotar(caso, momento.tick(), TipoEventoCaso.VARIABLE_AUSENTE, comillas(nodo.nombre())
                    + " pregunto por " + comillas(ausente)
                    + " y el caso no la tiene, asi que esa comparacion sale falsa.");
        }
        variables.olvidarAusentes();
    }

    private void seguir(Caso caso, GrafoDeVersion grafo, List<ArcoDeLaVersion> salidas, Momento momento) {
        for (ArcoDeLaVersion salida : salidas) {
            grafo.nodo(salida.destinoId()).ifPresent(destino -> activar(caso, grafo, destino, momento));
        }
    }

    /** Un caso termina cuando muere su ultimo token; mientras quede uno vivo, sigue abierto. */
    private void terminarSiNoQuedanTokens(Caso caso, Momento momento) {
        if (caso.getEstado() != EstadoCaso.ABIERTO || !vivosDe(caso).isEmpty()) {
            return;
        }
        caso.setEstado(EstadoCaso.TERMINADO);
        caso.setTickFin(momento.tick());
        caso.setFechaFin(LocalDateTime.now());
        bitacora.anotar(caso, momento.tick(), TipoEventoCaso.CASO_TERMINADO,
                "El ultimo camino del caso llego a un fin.");
    }

    private void enError(Caso caso, Momento momento, String detalle) {
        caso.setEstado(EstadoCaso.ERROR);
        bitacora.anotar(caso, momento.tick(), TipoEventoCaso.SIN_CAMINO, detalle);
    }

    private void completar(Caso caso, ActividadCaso token, Momento momento) {
        token.setEstado(EstadoActividadCaso.COMPLETADA);
        token.setTickFin(momento.tick());
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
