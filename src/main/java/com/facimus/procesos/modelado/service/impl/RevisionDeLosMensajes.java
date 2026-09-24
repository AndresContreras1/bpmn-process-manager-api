package com.facimus.procesos.modelado.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

import com.facimus.procesos.modelado.dto.response.CorrelacionResponse;
import com.facimus.procesos.modelado.dto.response.MensajeResponse;
import com.facimus.procesos.modelado.dto.response.PoolResponse;
import com.facimus.procesos.modelado.model.CodigoDeDiagnostico;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoParticipante;

/**
 * La mitad del diagnostico que mira lo que el proceso intercambia con los demas participantes: que nodo manda cada
 * mensaje, que nodo lo espera, con que clave encuentra su caso y que pasa si el envio falla.
 */
final class RevisionDeLosMensajes {

    private RevisionDeLosMensajes() {
    }

    static void revisar(MapaDelDiagrama mapa, Hallazgos hallazgos) {
        nodosSinSuMensaje(mapa, hallazgos);
        mapa.diagrama().mensajes().forEach(mensaje -> revisarMensaje(mapa, mensaje, hallazgos));
        nombresRepetidos(mapa, hallazgos);
        participantes(mapa, hallazgos);
    }

    /** E-10, E-11 y A-12: un nodo que existe para intercambiar un mensaje, sin ningun mensaje anclado. */
    private static void nodosSinSuMensaje(MapaDelDiagrama mapa, Hallazgos hallazgos) {
        for (NodoDelDiagrama nodo : mapa.nodos()) {
            boolean espera = nodo.puedeRecibirMensajes() && mapa.mensajesQueLleganA(nodo.id()).isEmpty();
            boolean manda = nodo.puedeEnviarMensajes() && mapa.mensajesQueSalenDe(nodo.id()).isEmpty();
            if (espera && nodo.esEvento()) {
                hallazgos.anotar(CodigoDeDiagnostico.E10, nodo.etiqueta(), nodo.id(),
                        "Este evento se queda esperando un mensaje y no tiene ninguno anclado.",
                        "Ancla a este evento el mensaje que espera, o cambiale el tipo.");
            } else if (espera || (manda && nodo.tipoActividad() != TipoActividad.SERVICIO)) {
                hallazgos.anotar(CodigoDeDiagnostico.E11, nodo.etiqueta(), nodo.id(),
                        "Este nodo existe para intercambiar un mensaje y no tiene ninguno anclado.",
                        "Ancla su mensaje, o cambia el tipo del nodo por uno que no intercambie mensajes.");
            } else if (manda) {
                hallazgos.anotar(CodigoDeDiagnostico.A12, nodo.etiqueta(), nodo.id(),
                        "Esta actividad de servicio no manda ningun mensaje: se completara sola, sin efecto.",
                        "Ancla el mensaje que manda, o dejala como esta si solo marca un paso del proceso.");
            }
        }
    }

    private static void revisarMensaje(MapaDelDiagrama mapa, MensajeResponse mensaje, Hallazgos hallazgos) {
        anclajeEnSuPool(mapa, mensaje, hallazgos);
        anclajesQueFaltan(mapa, mensaje, hallazgos);
        porDondeViaja(mapa, mensaje, hallazgos);
        clave(mapa, mensaje, hallazgos);
    }

    /** E-12: el nodo anclado tiene que estar en el pool de su lado; si no, el mensaje sale de donde no es. */
    private static void anclajeEnSuPool(MapaDelDiagrama mapa, MensajeResponse mensaje, Hallazgos hallazgos) {
        fueraDeSuPool(mapa, mensaje.nodoOrigenId(), mensaje.poolOrigenId(), "sale", mensaje, hallazgos);
        fueraDeSuPool(mapa, mensaje.nodoDestinoId(), mensaje.poolDestinoId(), "llega", mensaje, hallazgos);
    }

    private static void fueraDeSuPool(MapaDelDiagrama mapa, Long nodoId, Long poolId, String lado,
            MensajeResponse mensaje, Hallazgos hallazgos) {
        if (nodoId == null || poolId.equals(mapa.poolDelNodo(nodoId))) {
            return;
        }
        hallazgos.anotar(CodigoDeDiagnostico.E12, etiqueta(mensaje), mensaje.id(),
                "El nodo por el que " + lado + " el mensaje no esta en el pool de ese lado.",
                "Anclalo a un nodo del pool que " + lado + ", o corrige el participante del mensaje.");
    }

    /** E-13, A-01 y A-02: un mensaje entre dos pools modelados dice por donde sale y por donde entra. */
    private static void anclajesQueFaltan(MapaDelDiagrama mapa, MensajeResponse mensaje, Hallazgos hallazgos) {
        boolean origenModelado = mapa.seModelaPorDentro(mensaje.poolOrigenId());
        boolean destinoModelado = mapa.seModelaPorDentro(mensaje.poolDestinoId());
        if (origenModelado && destinoModelado
                && (mensaje.nodoOrigenId() == null || mensaje.nodoDestinoId() == null)) {
            hallazgos.anotar(CodigoDeDiagnostico.E13, etiqueta(mensaje), mensaje.id(),
                    "Los dos participantes de este mensaje se modelan por dentro, asi que el mensaje tiene que "
                            + "decir desde que nodo sale y en cual se espera.",
                    "Ancla los dos lados del mensaje.");
            return;
        }
        if (destinoModelado && mensaje.nodoDestinoId() == null) {
            sinReceptor(mapa, mensaje, hallazgos);
        }
        if (destinoModelado && mensaje.nodoDestinoId() != null) {
            sinQuienLoMande(mapa, mensaje, hallazgos);
        }
    }

    /** A-01: sin anclaje, lo unico que une el mensaje con quien lo espera es el nombre. */
    private static void sinReceptor(MapaDelDiagrama mapa, MensajeResponse mensaje, Hallazgos hallazgos) {
        boolean hayQuienLoEspere = mapa.nodosDe(mensaje.poolDestinoId()).stream()
                .anyMatch(nodo -> nodo.puedeRecibirMensajes() && nodo.nombre().equals(mensaje.nombre()));
        if (!hayQuienLoEspere) {
            hallazgos.anotar(CodigoDeDiagnostico.A01, etiqueta(mensaje), mensaje.id(),
                    "El mensaje va a un participante que se modela por dentro y ningun nodo suyo lo espera: queda "
                            + "sin receptor.",
                    "Ancla el mensaje al nodo que lo espera.");
        }
    }

    /**
     * A-02: un mensaje que se espera en mitad del flujo tiene que venir de algun sitio. Puede mandarlo un nodo del
     * diagrama, puede ser la respuesta declarada de otro mensaje, o puede llegar de fuera y decirse asi.
     */
    private static void sinQuienLoMande(MapaDelDiagrama mapa, MensajeResponse mensaje, Hallazgos hallazgos) {
        NodoDelDiagrama espera = mapa.nodo(mensaje.nodoDestinoId());
        boolean abreElCaso = espera != null && espera.empiezaElProceso();
        boolean esRespuesta = mapa.diagrama().mensajes().stream()
                .anyMatch(otro -> mensaje.id().equals(otro.respuestaEsperadaId()));
        if (mensaje.nodoOrigenId() == null && !mensaje.origenExterno() && !abreElCaso && !esRespuesta) {
            hallazgos.anotar(CodigoDeDiagnostico.A02, etiqueta(mensaje), mensaje.id(),
                    "El proceso espera este mensaje y nada en el diagrama dice quien lo manda.",
                    "Ancla el nodo que lo manda, declaralo como la respuesta esperada de otro mensaje, o marcalo "
                            + "como de origen externo.");
        }
    }

    /** A-08: hablar con un sistema externo obliga a decir por donde, porque de eso depende como se simula. */
    private static void porDondeViaja(MapaDelDiagrama mapa, MensajeResponse mensaje, Hallazgos hallazgos) {
        PoolResponse destino = mapa.pool(mensaje.poolDestinoId());
        if (destino != null && destino.tipoParticipante() == TipoParticipante.SISTEMA_EXTERNO
                && mensaje.tipoDestino() == null) {
            hallazgos.anotar(CodigoDeDiagnostico.A08, etiqueta(mensaje), mensaje.id(),
                    "El mensaje va a un sistema externo y no dice por donde viaja.",
                    "Elige si sale por correo, por servicio web o por una cola, y que hace el proceso si falla.");
        }
    }

    /** A-03 y A-07: sin clave, un mensaje que llega no sabe a que caso pertenece. */
    private static void clave(MapaDelDiagrama mapa, MensajeResponse mensaje, Hallazgos hallazgos) {
        NodoDelDiagrama espera = mapa.nodo(mensaje.nodoDestinoId());
        CorrelacionResponse correlacion = mapa.correlacionDe(mensaje.id());
        if (correlacion == null) {
            if (espera != null && !espera.empiezaElProceso()) {
                hallazgos.anotar(CodigoDeDiagnostico.A03, etiqueta(mensaje), mensaje.id(),
                        "El proceso espera este mensaje en mitad del flujo y no tiene clave de correlacion.",
                        "Definele una clave, para que el mensaje encuentre el caso al que pertenece.");
            }
            return;
        }
        if (!StringUtils.hasText(correlacion.campo())) {
            hallazgos.anotar(CodigoDeDiagnostico.A07, etiqueta(mensaje), mensaje.id(),
                    "La clave de correlacion no dice en que campo del cuerpo viaja.",
                    "Indica el campo, por ejemplo orderId, para poder correlacionar el mensaje.");
        }
    }

    /** A-04: dos mensajes con el mismo nombre y la misma clave no se distinguen cuando llegan. */
    private static void nombresRepetidos(MapaDelDiagrama mapa, Hallazgos hallazgos) {
        Map<String, List<MensajeResponse>> porNombreYClave = mapa.diagrama().mensajes().stream()
                .collect(Collectors.groupingBy(mensaje -> mensaje.nombre() + "/" + criterio(mapa, mensaje),
                        LinkedHashMap::new, Collectors.toList()));
        porNombreYClave.values().stream()
                .filter(repetidos -> repetidos.size() > 1)
                .forEach(repetidos -> hallazgos.anotar(CodigoDeDiagnostico.A04, etiqueta(repetidos.getFirst()),
                        repetidos.getFirst().id(),
                        "El proceso tiene " + repetidos.size() + " mensajes con este nombre y la misma clave: "
                                + "cuando llegue uno no se sabra cual es.",
                        "Cambiale el nombre a uno de ellos, o dales claves de correlacion distintas."));
    }

    private static String criterio(MapaDelDiagrama mapa, MensajeResponse mensaje) {
        CorrelacionResponse correlacion = mapa.correlacionDe(mensaje.id());
        return correlacion == null ? "" : String.valueOf(correlacion.criterio());
    }

    /** A-09 y A-11: que hace cada participante en el proceso, y si el simulador tendria algo que contestar. */
    private static void participantes(MapaDelDiagrama mapa, Hallazgos hallazgos) {
        Set<Long> quienesResponden = mapa.diagrama().mensajes().stream()
                .map(MensajeResponse::poolOrigenId)
                .collect(Collectors.toSet());
        for (PoolResponse pool : mapa.pools()) {
            if (pool.tipoParticipante() == TipoParticipante.EMPRESA) {
                continue;
            }
            if (mapa.mensajesDe(pool.id()).isEmpty()) {
                hallazgos.anotar(CodigoDeDiagnostico.A11, "Pool \"" + pool.nombre() + "\"", pool.id(),
                        "Este participante no intercambia ningun mensaje con el proceso.",
                        "Dale el mensaje que manda o que recibe, o quitalo del diagrama.");
            } else if (pool.integracion() != Integracion.NINGUNA && !quienesResponden.contains(pool.id())) {
                hallazgos.anotar(CodigoDeDiagnostico.A09, "Pool \"" + pool.nombre() + "\"", pool.id(),
                        "Este participante tiene un socio detras y nunca manda nada de vuelta.",
                        "Declara el mensaje con el que responde, para que el socio simulado tenga que contestar.");
            }
        }
    }

    private static String etiqueta(MensajeResponse mensaje) {
        return "Mensaje \"" + mensaje.nombre() + "\"";
    }
}
