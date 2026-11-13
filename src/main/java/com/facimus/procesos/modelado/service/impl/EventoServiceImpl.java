package com.facimus.procesos.modelado.service.impl;

import java.util.List;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.mapper.EventoMapper;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Evento;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.NodoFlujo;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.EventoRepository;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;
import com.facimus.procesos.modelado.service.DatosDeTemporizador;
import com.facimus.procesos.modelado.service.EventoService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventoServiceImpl implements EventoService {

    private final ElementosPegados pegados;
    private final HistorialCambioService historialCambioService;
    private final EventoRepository eventoRepository;
    private final NodoFlujoRepository nodoFlujoRepository;
    private final LaneRepository laneRepository;
    private final ArcoRepository arcoRepository;
    private final MensajeRepository mensajeRepository;
    private final EventoMapper eventoMapper;

    @Override
    @Transactional
    public EventoResponse crear(Long empresaId, Long usuarioId, Long laneId, String nombre, TipoEvento tipoEvento,
            int posX, int posY) {
        return crear(empresaId, usuarioId, laneId, nombre, tipoEvento, posX, posY, DatosDeTemporizador.ninguno());
    }

    @Override
    @Transactional
    public EventoResponse crear(Long empresaId, Long usuarioId, Long laneId, String nombre, TipoEvento tipoEvento,
            int posX, int posY, DatosDeTemporizador temporizador) {
        Lane lane = laneRepository.findByIdAndEmpresaId(laneId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Lane no encontrada."));
        NodoFlujo adjuntoA = adjuntoA(empresaId, tipoEvento, temporizador, lane);
        Long procesoId = lane.getPool().getProceso().getId();
        if (nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaId(nombre, procesoId,
                empresaId)) {
            throw new ReglaNegocioException("Ya existe un nodo con el nombre \"" + nombre + "\" en este proceso.");
        }

        Evento evento = Evento.builder()
                .empresa(lane.getEmpresa())
                .lane(adjuntoA != null ? adjuntoA.getLane() : lane)
                .nombre(nombre)
                .tipoEvento(tipoEvento)
                .posicionX(posX)
                .posicionY(posY)
                .build();
        temporizar(evento, tipoEvento, temporizador, adjuntoA);
        evento = eventoRepository.save(evento);
        historialCambioService.registrar(empresaId, usuarioId, lane.getPool().getProceso(),
                "Evento \"" + nombre + "\" agregado.");
        return eventoMapper.toResponse(evento);
    }

    @Override
    @Transactional
    public EventoResponse editar(Long empresaId, Long usuarioId, Long eventoId, String nombre, TipoEvento tipoEvento,
            Long laneId, int posX, int posY, Long version) {
        return editar(empresaId, usuarioId, eventoId, nombre, tipoEvento, laneId, posX, posY, version,
                DatosDeTemporizador.ninguno());
    }

    @Override
    @Transactional
    public EventoResponse editar(Long empresaId, Long usuarioId, Long eventoId, String nombre, TipoEvento tipoEvento,
            Long laneId, int posX, int posY, Long version, DatosDeTemporizador temporizador) {
        Evento evento = buscar(empresaId, eventoId);
        evento.verificarVersion(version);
        if (evento.getFormulario() != null && tipoEvento != TipoEvento.INICIO) {
            // PR 69: el formulario es de quien abre el caso a mano; un inicio de mensaje o de temporizador se abre solo
            throw new ReglaNegocioException("El evento tiene formulario, y solo un inicio simple lo lleva: quítalo "
                    + "antes de cambiarle el tipo.");
        }
        if (evento.getPlazoUnidad() != null && !tipoEvento.empiezaElProceso()) {
            // PR 71: el plazo del caso cuenta desde que el caso empieza, y solo un inicio lo empieza
            throw new ReglaNegocioException("El evento tiene el plazo del caso, y solo un inicio lo lleva: quítalo "
                    + "antes de cambiarle el tipo.");
        }
        if (evento.getEscalamiento() != null && (!tipoEvento.esDeBorde() || interrumpe(temporizador))) {
            // PR 71: solo escala un borde que deja seguir a su tarea
            throw new ReglaNegocioException("El evento escala su tarea, y solo lo hace un temporizador de borde que "
                    + "no interrumpe: quita el escalamiento antes de cambiarlo.");
        }
        Long procesoId = evento.getLane().getPool().getProceso().getId();
        if (nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaIdAndIdNot(nombre, procesoId,
                empresaId, eventoId)) {
            throw new ReglaNegocioException("Ya existe un nodo con el nombre \"" + nombre + "\" en este proceso.");
        }
        // El tipo nuevo tiene que caber en los arcos que el evento ya tiene: no se cambia primero y se rompe despues.
        if ((tipoEvento.empiezaElProceso() || tipoEvento.esDeBorde())
                && !arcoRepository.findAllByDestinoIdAndEmpresaId(eventoId, empresaId).isEmpty()) {
            throw new ReglaNegocioException(ReglasDeEventos.SIN_ENTRANTES);
        }
        if (tipoEvento.terminaElProceso() && !arcoRepository.findAllByOrigenIdAndEmpresaId(eventoId, empresaId)
                .isEmpty()) {
            throw new ReglaNegocioException(ReglasDeEventos.SIN_SALIENTES);
        }
        // Se consulta antes de tocar la entidad: una consulta despues la escribiria y gastaria una version de mas
        Lane destino = ReglasDeNodos.mudanza(empresaId, evento, laneId, laneRepository, arcoRepository,
                mensajeRepository);
        NodoFlujo adjuntoA = adjuntoA(empresaId, tipoEvento, temporizador, destino);
        evento.setLane(adjuntoA != null ? adjuntoA.getLane() : destino);
        evento.setNombre(nombre);
        evento.setTipoEvento(tipoEvento);
        evento.setPosicionX(posX);
        evento.setPosicionY(posY);
        temporizar(evento, tipoEvento, temporizador, adjuntoA);
        historialCambioService.registrar(empresaId, usuarioId, evento.getLane().getPool().getProceso(),
                "Evento \"" + nombre + "\" editado.");
        return eventoMapper.toResponse(eventoRepository.saveAndFlush(evento));
    }

    /**
     * PR 55: un evento de borde va pegado a una actividad de su mismo pool, y queda en la lane de ella; cualquier otro
     * no va pegado a nada.
     */
    private NodoFlujo adjuntoA(Long empresaId, TipoEvento tipoEvento, DatosDeTemporizador temporizador, Lane lane) {
        Long adjuntoAId = temporizador == null ? null : temporizador.adjuntoAId();
        if (!tipoEvento.esDeBorde()) {
            if (adjuntoAId != null) {
                throw new ReglaNegocioException("Solo un evento de borde va pegado a una actividad.");
            }
            return null;
        }
        if (adjuntoAId == null) {
            throw new ReglaNegocioException("Un evento de borde va pegado a una actividad: indique cual.");
        }
        // Si el evento ya estaba pegado, en la sesion hay un proxy de NodoFlujo, que no es una Actividad
        NodoFlujo actividad = nodoFlujoRepository.findByIdAndEmpresaId(adjuntoAId, empresaId)
                .map(Hibernate::unproxy)
                .filter(Actividad.class::isInstance)
                .map(NodoFlujo.class::cast)
                .orElseThrow(() -> new ReglaNegocioException("Un evento de borde va pegado a una actividad."));
        if (!actividad.getLane().getPool().getId().equals(lane.getPool().getId())) {
            throw new ReglaNegocioException("La actividad de un evento de borde esta en su mismo pool.");
        }
        return actividad;
    }

    /** Lo del temporizador solo lo guarda un evento de temporizador; el de borde, ademas, si interrumpe. */
    private static void temporizar(Evento evento, TipoEvento tipoEvento, DatosDeTemporizador temporizador,
            NodoFlujo adjuntoA) {
        boolean esTemporizador = tipoEvento.esTemporizador() && temporizador != null;
        evento.setTipoTemporizador(esTemporizador ? temporizador.tipo() : null);
        evento.setTemporizador(esTemporizador && temporizador.expresion() != null
                ? temporizador.expresion().strip() : null);
        evento.setAdjuntoA(adjuntoA);
        evento.setInterrumpe(tipoEvento.esDeBorde() ? Boolean.valueOf(interrumpe(temporizador)) : null);
    }

    /** Un borde interrumpe salvo que se diga lo contrario. */
    private static boolean interrumpe(DatosDeTemporizador temporizador) {
        return temporizador == null || !Boolean.FALSE.equals(temporizador.interrumpe());
    }

    @Override
    @Transactional
    public void eliminar(Long empresaId, Long usuarioId, Long eventoId) {
        Evento evento = buscar(empresaId, eventoId);
        pegados.soltarAsociaciones(empresaId, evento.getLane().getPool().getProceso().getId(), evento.getBpmnId());
        arcoRepository.deleteAll(arcoRepository.findAllByOrigenIdAndEmpresaId(eventoId, empresaId));
        arcoRepository.deleteAll(arcoRepository.findAllByDestinoIdAndEmpresaId(eventoId, empresaId));
        eventoRepository.delete(evento);
        historialCambioService.registrar(empresaId, usuarioId, evento.getLane().getPool().getProceso(),
                "Evento \"" + evento.getNombre() + "\" eliminado.");
    }

    @Override
    public EventoResponse obtener(Long empresaId, Long eventoId) {
        return eventoMapper.toResponse(buscar(empresaId, eventoId));
    }

    @Override
    public List<EventoResponse> listarPorLane(Long empresaId, Long laneId) {
        if (!laneRepository.existsByIdAndEmpresaId(laneId, empresaId)) {
            throw new RecursoNoEncontradoException("Lane no encontrada.");
        }
        return eventoMapper.toResponses(eventoRepository.findAllByLaneIdAndEmpresaId(laneId, empresaId));
    }

    private Evento buscar(Long empresaId, Long eventoId) {
        return eventoRepository.findByIdAndEmpresaId(eventoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Evento no encontrado."));
    }
}
