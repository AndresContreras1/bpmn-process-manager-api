package com.facimus.procesos.modelado.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.mapper.EventoMapper;
import com.facimus.procesos.modelado.model.Evento;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.EventoRepository;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;
import com.facimus.procesos.modelado.service.EventoService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventoServiceImpl implements EventoService {

    private final HistorialCambioService historialCambioService;
    private final EventoRepository eventoRepository;
    private final NodoFlujoRepository nodoFlujoRepository;
    private final LaneRepository laneRepository;
    private final ArcoRepository arcoRepository;
    private final EventoMapper eventoMapper;

    @Override
    @Transactional
    public EventoResponse crear(Long empresaId, Long usuarioId, Long laneId, String nombre, TipoEvento tipoEvento,
            int posX, int posY) {
        Lane lane = laneRepository.findByIdAndEmpresaId(laneId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Lane no encontrada."));
        Long procesoId = lane.getPool().getProceso().getId();
        if (nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaId(nombre, procesoId,
                empresaId)) {
            throw new ReglaNegocioException("Ya existe un nodo con el nombre \"" + nombre + "\" en este proceso.");
        }

        Evento evento = eventoRepository.save(Evento.builder()
                .empresa(lane.getEmpresa())
                .lane(lane)
                .nombre(nombre)
                .tipoEvento(tipoEvento)
                .posicionX(posX)
                .posicionY(posY)
                .build());
        historialCambioService.registrar(empresaId, usuarioId, lane.getPool().getProceso(),
                "Evento \"" + nombre + "\" agregado.");
        return eventoMapper.toResponse(evento);
    }

    @Override
    @Transactional
    public EventoResponse editar(Long empresaId, Long usuarioId, Long eventoId, String nombre, TipoEvento tipoEvento,
            int posX, int posY, Long version) {
        Evento evento = buscar(empresaId, eventoId);
        evento.verificarVersion(version);
        Long procesoId = evento.getLane().getPool().getProceso().getId();
        if (nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaIdAndIdNot(nombre, procesoId,
                empresaId, eventoId)) {
            throw new ReglaNegocioException("Ya existe un nodo con el nombre \"" + nombre + "\" en este proceso.");
        }
        // El tipo nuevo tiene que caber en los arcos que el evento ya tiene: no se cambia primero y se rompe despues.
        if (tipoEvento.empiezaElProceso() && !arcoRepository.findAllByDestinoIdAndEmpresaId(eventoId, empresaId)
                .isEmpty()) {
            throw new ReglaNegocioException(ReglasDeEventos.SIN_ENTRANTES);
        }
        if (tipoEvento.terminaElProceso() && !arcoRepository.findAllByOrigenIdAndEmpresaId(eventoId, empresaId)
                .isEmpty()) {
            throw new ReglaNegocioException(ReglasDeEventos.SIN_SALIENTES);
        }
        evento.setNombre(nombre);
        evento.setTipoEvento(tipoEvento);
        evento.setPosicionX(posX);
        evento.setPosicionY(posY);
        historialCambioService.registrar(empresaId, usuarioId, evento.getLane().getPool().getProceso(),
                "Evento \"" + nombre + "\" editado.");
        return eventoMapper.toResponse(eventoRepository.saveAndFlush(evento));
    }

    @Override
    @Transactional
    public void eliminar(Long empresaId, Long usuarioId, Long eventoId) {
        Evento evento = buscar(empresaId, eventoId);
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
