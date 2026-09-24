package com.facimus.procesos.modelado.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.facimus.procesos.common.DemasiadosIntentosException;
import com.facimus.procesos.common.IntegracionNoConfiguradaException;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.ArcoResponse;
import com.facimus.procesos.modelado.dto.response.CorrelacionResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.dto.response.GatewayResponse;
import com.facimus.procesos.modelado.dto.response.LaneResponse;
import com.facimus.procesos.modelado.dto.response.MensajeResponse;
import com.facimus.procesos.modelado.dto.response.PoolResponse;
import com.facimus.procesos.modelado.dto.response.RevisionResponse;
import com.facimus.procesos.modelado.service.DiagramaService;
import com.facimus.procesos.modelado.service.Dictamen;
import com.facimus.procesos.modelado.service.RevisionService;
import com.facimus.procesos.modelado.service.RevisorDeDiagramas;
import com.facimus.procesos.security.AttemptLimiter;

/**
 * El diagrama entra por la puerta de lectura, asi que un proceso ajeno o eliminado responde 404 antes de gastar una
 * llamada al modelo. Lo que se manda es una descripcion en texto, no el JSON del endpoint: dice lo mismo con menos
 * ruido y no expone ids internos.
 */
@Service
@Transactional(readOnly = true)
public class RevisionServiceImpl implements RevisionService {

    /** Cuantos procesos recuerdan su ultima revision. Lo que sobra se suelta empezando por lo menos consultado. */
    private static final int PROCESOS_RECORDADOS = 500;

    private final DiagramaService diagramaService;
    private final RevisorDeDiagramas revisor;
    private final Clock reloj;
    private final AttemptLimiter limite;
    private final UltimasRevisiones ultimas = new UltimasRevisiones(PROCESOS_RECORDADOS);

    public RevisionServiceImpl(DiagramaService diagramaService, RevisorDeDiagramas revisor, Clock reloj,
            @Value("${revision.max-reviews}") int maximo,
            @Value("${revision.window}") Duration ventana) {
        this.diagramaService = diagramaService;
        this.revisor = revisor;
        this.reloj = reloj;
        this.limite = new AttemptLimiter(maximo, ventana, PROCESOS_RECORDADOS, reloj);
    }

    @Override
    public RevisionResponse revisar(Long empresaId, Long procesoId) {
        DiagramaResponse diagrama = diagramaService.obtener(empresaId, procesoId);
        String descripcion = describir(diagrama);
        String clave = empresaId + ":" + procesoId;

        // Pedir dos veces la revision de un diagrama que no cambio no gasta ni una llamada ni parte del limite.
        Optional<RevisionResponse> guardada = ultimas.buscar(clave, descripcion);
        if (guardada.isPresent()) {
            RevisionResponse revision = guardada.get();
            return new RevisionResponse(procesoId, revision.resumen(), revision.hallazgos(), true, revision.fecha());
        }
        if (!revisor.estaConfigurado()) {
            throw new IntegracionNoConfiguradaException(
                    "La revisión con IA no está configurada en esta instalación.");
        }
        // El limite es por tienda, no por usuario: la cuenta la paga la tienda.
        limite.espera(empresaId.toString()).ifPresent(espera -> {
            throw new DemasiadosIntentosException(
                    "Esta tienda ya usó sus revisiones con IA por ahora. Intenta de nuevo en "
                            + minutos(espera) + ".", espera);
        });
        limite.registrar(empresaId.toString());

        Dictamen dictamen = revisor.revisar(descripcion);
        RevisionResponse revision = new RevisionResponse(procesoId, dictamen.resumen(), dictamen.hallazgos(), false,
                LocalDateTime.now(reloj));
        ultimas.guardar(clave, descripcion, revision);
        return revision;
    }

    private static String minutos(Duration espera) {
        long minutos = Math.max(1, (espera.toSeconds() + 59) / 60);
        return minutos == 1 ? "1 minuto" : minutos + " minutos";
    }

    /** El diagrama contado como lo leeria una persona: participantes, quien hace que, el flujo y los mensajes. */
    static String describir(DiagramaResponse diagrama) {
        StringBuilder texto = new StringBuilder();
        texto.append("Proceso: ").append(diagrama.proceso().nombre())
                .append(" (").append(diagrama.proceso().estado()).append(")\n")
                .append("Descripcion: ").append(diagrama.proceso().descripcion()).append('\n');

        Map<Long, List<LaneResponse>> lanesPorPool = agrupar(diagrama.lanes(), LaneResponse::poolId);
        Map<Long, List<ActividadResponse>> actividadesPorLane = agrupar(diagrama.actividades(),
                ActividadResponse::laneId);
        Map<Long, List<GatewayResponse>> gatewaysPorLane = agrupar(diagrama.gateways(), GatewayResponse::laneId);
        Map<Long, List<EventoResponse>> eventosPorLane = agrupar(diagrama.eventos(), EventoResponse::laneId);
        Map<Long, String> nombresDePool = nombres(diagrama.pools(), PoolResponse::id, PoolResponse::nombre);
        Map<Long, String> nombresDeNodo = nombres(diagrama.actividades(), ActividadResponse::id,
                ActividadResponse::nombre);
        nombresDeNodo.putAll(nombres(diagrama.gateways(), GatewayResponse::id, GatewayResponse::nombre));
        nombresDeNodo.putAll(nombres(diagrama.eventos(), EventoResponse::id, EventoResponse::nombre));

        texto.append("\nParticipantes:\n");
        for (PoolResponse pool : diagrama.pools()) {
            texto.append("- ").append(pool.nombre()).append(" (").append(pool.tipoParticipante())
                    .append(pool.cajaNegra() ? ", caja negra" : "").append(")\n");
            for (LaneResponse lane : lanesPorPool.getOrDefault(pool.id(), List.of())) {
                texto.append("  Lane ").append(lane.nombre())
                        .append(" (rol ").append(lane.rolProcesoNombre()).append(")\n");
                for (EventoResponse evento : eventosPorLane.getOrDefault(lane.id(), List.of())) {
                    texto.append("    Evento ").append(evento.tipoEvento())
                            .append(": ").append(evento.nombre()).append('\n');
                }
                for (ActividadResponse actividad : actividadesPorLane.getOrDefault(lane.id(), List.of())) {
                    texto.append("    Actividad ").append(actividad.tipoActividad())
                            .append(": ").append(actividad.nombre());
                    if (StringUtils.hasText(actividad.descripcion())) {
                        texto.append(" - ").append(actividad.descripcion());
                    }
                    texto.append('\n');
                }
                for (GatewayResponse gateway : gatewaysPorLane.getOrDefault(lane.id(), List.of())) {
                    texto.append("    Gateway ").append(gateway.tipoGateway())
                            .append(": ").append(gateway.nombre()).append('\n');
                }
            }
        }

        texto.append("\nFlujo dentro de cada participante:\n");
        for (ArcoResponse arco : diagrama.arcos()) {
            texto.append("- \"").append(nombresDeNodo.getOrDefault(arco.origenId(), "?"))
                    .append("\" -> \"").append(nombresDeNodo.getOrDefault(arco.destinoId(), "?")).append('"');
            if (StringUtils.hasText(arco.etiqueta())) {
                texto.append(" [").append(arco.etiqueta()).append(']');
            }
            if (StringUtils.hasText(arco.condicion())) {
                texto.append(" si ").append(arco.condicion());
            }
            texto.append('\n');
        }

        Map<Long, String> criterios = nombres(diagrama.correlaciones(), CorrelacionResponse::mensajeId,
                CorrelacionResponse::criterio);
        texto.append("\nMensajes entre participantes:\n");
        for (MensajeResponse mensaje : diagrama.mensajes()) {
            texto.append("- \"").append(mensaje.nombre()).append("\": ")
                    .append(nombresDePool.getOrDefault(mensaje.poolOrigenId(), "?")).append(" -> ")
                    .append(nombresDePool.getOrDefault(mensaje.poolDestinoId(), "?"));
            anclaje(texto, " desde", mensaje.nodoOrigenId(), nombresDeNodo);
            anclaje(texto, " hacia", mensaje.nodoDestinoId(), nombresDeNodo);
            if (mensaje.origenExterno()) {
                texto.append(", llega de fuera del diagrama");
            }
            if (mensaje.tipoDestino() != null) {
                texto.append(", por ").append(mensaje.tipoDestino());
            }
            texto.append(", si falla ").append(mensaje.siFalla());
            if (!mensaje.campos().isEmpty()) {
                texto.append(", campos: ").append(mensaje.campos().stream()
                        .map(campo -> campo.nombre() + " (" + campo.tipo() + ")")
                        .collect(Collectors.joining(", ")));
            }
            String criterio = criterios.get(mensaje.id());
            texto.append(criterio == null ? " (sin clave de correlacion)" : " (correlacion por " + criterio + ")");
            texto.append('\n');
        }
        return texto.toString();
    }

    /** El nodo al que se ancla un mensaje, si el pool de ese lado modela su flujo. */
    private static void anclaje(StringBuilder texto, String preposicion, Long nodoId,
            Map<Long, String> nombresDeNodo) {
        if (nodoId != null) {
            texto.append(",").append(preposicion).append(" \"")
                    .append(nombresDeNodo.getOrDefault(nodoId, "?")).append('\"');
        }
    }

    private static <T> Map<Long, List<T>> agrupar(List<T> elementos, Function<T, Long> padre) {
        return elementos.stream().collect(Collectors.groupingBy(padre));
    }

    private static <T> Map<Long, String> nombres(List<T> elementos, Function<T, Long> id, Function<T, String> nombre) {
        return elementos.stream().collect(Collectors.toMap(id, nombre, (a, b) -> a, HashMap::new));
    }
}
