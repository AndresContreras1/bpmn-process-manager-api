package com.facimus.procesos.modelado.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;

import lombok.RequiredArgsConstructor;

/** HU-08 a HU-10: actividades (tareas del proceso). */
@Service
@RequiredArgsConstructor
public class ActividadService {

    private final NodoFlujoRepository nodoFlujoRepository;
    private final LaneRepository laneRepository;
    private final ArcoRepository arcoRepository;

    @Transactional
    public Actividad crear(Long empresaId, Long laneId, String nombre, String descripcion, int posX, int posY) {
        Lane lane = laneRepository.findByIdAndEmpresaId(laneId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Lane no encontrada."));
        Long procesoId = lane.getPool().getProceso().getId();
        if (nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaId(nombre, procesoId, empresaId)) {
            throw new ReglaNegocioException("Ya existe un nodo con el nombre \"" + nombre + "\" en este proceso.");
        }

        Actividad actividad = new Actividad();
        actividad.setEmpresa(lane.getEmpresa());
        actividad.setLane(lane);
        actividad.setNombre(nombre);
        actividad.setDescripcion(descripcion);
        actividad.setPosicionX(posX);
        actividad.setPosicionY(posY);
        return (Actividad) nodoFlujoRepository.save(actividad);
    }

    @Transactional
    public Actividad editar(Long empresaId, Long actividadId, String nombre, String descripcion, int posX, int posY) {
        Actividad actividad = obtener(empresaId, actividadId);
        actividad.setNombre(nombre);
        actividad.setDescripcion(descripcion);
        actividad.setPosicionX(posX);
        actividad.setPosicionY(posY);
        return (Actividad) nodoFlujoRepository.save(actividad);
    }

    @Transactional
    public void eliminar(Long empresaId, Long actividadId) {
        Actividad actividad = obtener(empresaId, actividadId);
        arcoRepository.deleteAll(arcoRepository.findAllByOrigenIdAndEmpresaId(actividadId, empresaId));
        arcoRepository.deleteAll(arcoRepository.findAllByDestinoIdAndEmpresaId(actividadId, empresaId));
        nodoFlujoRepository.delete(actividad);
    }

    public Actividad obtener(Long empresaId, Long actividadId) {
        return nodoFlujoRepository.findByIdAndEmpresaId(actividadId, empresaId)
                .filter(Actividad.class::isInstance)
                .map(Actividad.class::cast)
                .orElseThrow(() -> new RecursoNoEncontradoException("Actividad no encontrada."));
    }

    public List<Actividad> listarPorLane(Long empresaId, Long laneId) {
        if (!laneRepository.existsByIdAndEmpresaId(laneId, empresaId)) {
            throw new RecursoNoEncontradoException("Lane no encontrada.");
        }
        return nodoFlujoRepository.findAllByLaneIdAndEmpresaId(laneId, empresaId).stream()
                .filter(Actividad.class::isInstance)
                .map(Actividad.class::cast)
                .toList();
    }
}
